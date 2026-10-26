package com.tulip.app.data

import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.tulip.app.logic.FolderExclusions
import com.tulip.app.logic.MediaEntry
import com.tulip.app.logic.MediaKind
import com.tulip.app.logic.NameRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface OpResult {
    data class Done(val message: String) : OpResult
    data class Failed(val message: String) : OpResult

    /** Android exige una confirmación del sistema (diálogo). `resume` se ejecuta si el usuario acepta. */
    class NeedsConsent(val intentSender: IntentSender, val resume: suspend () -> OpResult) : OpResult
}

enum class ConflictPolicy { KEEP_BOTH, SKIP }

data class MovePlan(val entry: MediaEntry, val destPath: String, val newName: String?)

/**
 * Operaciones sobre archivos reales con MediaStore. Mover = cambiar RELATIVE_PATH (el archivo no se
 * duplica ni cambia de id). Para modificar archivos de otras apps se usa createWriteRequest (diálogo
 * del sistema); para borrar, createDeleteRequest.
 */
class FileOps(private val context: Context, private val repo: MediaRepository) {
    private val resolver get() = context.contentResolver
    private fun uri(e: MediaEntry): Uri = Uri.parse(e.uri)
    private fun norm(p: String) = FolderExclusions.normalize(p)

    fun delete(entries: List<MediaEntry>): OpResult {
        if (entries.isEmpty()) return OpResult.Failed("No hay nada seleccionado")
        val sender = MediaStore.createDeleteRequest(resolver, entries.map(::uri)).intentSender
        return OpResult.NeedsConsent(sender) {
            repo.refreshNow()
            OpResult.Done("${entries.size} archivo(s) eliminado(s)")
        }
    }

    private fun requestWrite(entries: List<MediaEntry>, then: suspend () -> OpResult): OpResult {
        val sender = MediaStore.createWriteRequest(resolver, entries.map(::uri)).intentSender
        return OpResult.NeedsConsent(sender, then)
    }

    fun nameExists(volume: String, relPath: String, name: String): Boolean {
        val c = resolver.query(
            MediaStore.Files.getContentUri(volume),
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(norm(relPath), name), null,
        )
        return c?.use { it.count > 0 } ?: false
    }

    /** Entradas que chocarían por nombre en el destino (incluye choques dentro del propio lote). */
    suspend fun findConflicts(entries: List<MediaEntry>, dest: String): List<MediaEntry> = withContext(Dispatchers.IO) {
        val d = norm(dest)
        val taken = HashSet<String>()
        entries.filter { e ->
            if (norm(e.relativePath) == d) return@filter false
            val clash = nameExists(e.volume, d, e.name) || e.name in taken
            taken.add(e.name)
            clash
        }
    }

    fun rename(e: MediaEntry, input: String): OpResult {
        val newName = NameRules.sanitizeFileName(input, e.name) ?: return OpResult.Failed("Nombre no válido")
        if (newName == e.name) return OpResult.Done("Sin cambios")
        if (nameExists(e.volume, e.relativePath, newName)) return OpResult.Failed("Ya existe un archivo con ese nombre en la carpeta")
        return requestWrite(listOf(e)) { applyUpdates(listOf(MovePlan(e, e.relativePath, newName))) }
    }

    suspend fun move(entries: List<MediaEntry>, dest: String, policy: ConflictPolicy): OpResult = withContext(Dispatchers.IO) {
        val d = norm(dest)
        if (d.isEmpty()) return@withContext OpResult.Failed("Elige una carpeta de destino")
        ensureDirectory(d)
        val taken = HashSet<String>()
        val plans = ArrayList<MovePlan>()
        for (e in entries) {
            if (norm(e.relativePath) == d) continue
            val clash = nameExists(e.volume, d, e.name) || e.name in taken
            if (!clash) {
                taken.add(e.name); plans.add(MovePlan(e, d, null))
            } else if (policy == ConflictPolicy.KEEP_BOTH) {
                val n = NameRules.uniqueName(e.name) { nameExists(e.volume, d, it) || it in taken }
                taken.add(n); plans.add(MovePlan(e, d, n))
            }
        }
        if (plans.isEmpty()) return@withContext OpResult.Done("No había nada que mover")
        requestWrite(plans.map { it.entry }) { applyUpdates(plans) }
    }

    suspend fun copy(entries: List<MediaEntry>, dest: String, policy: ConflictPolicy): OpResult = withContext(Dispatchers.IO) {
        val d = norm(dest)
        if (d.isEmpty()) return@withContext OpResult.Failed("Elige una carpeta de destino")
        ensureDirectory(d)
        var ok = 0
        var failed = 0
        val taken = HashSet<String>()
        for (e in entries) {
            val clash = nameExists(e.volume, d, e.name) || e.name in taken
            val name = when {
                !clash -> e.name
                policy == ConflictPolicy.KEEP_BOTH -> NameRules.uniqueName(e.name) { nameExists(e.volume, d, it) || it in taken }
                else -> continue
            }
            taken.add(name)
            val collection = when (e.kind) {
                MediaKind.IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                MediaKind.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                MediaKind.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, e.mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, d)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            var newUri: Uri? = null
            try {
                newUri = resolver.insert(collection, v) ?: throw IllegalStateException("insert null")
                resolver.openInputStream(uri(e))!!.use { i -> resolver.openOutputStream(newUri)!!.use { o -> i.copyTo(o) } }
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(newUri, done, null, null)
                ok++
            } catch (ex: Exception) {
                failed++
                newUri?.let { runCatching { resolver.delete(it, null, null) } }   // no dejar copias a medias
            }
        }
        repo.refreshNow()
        if (failed == 0) OpResult.Done("$ok archivo(s) copiado(s)") else OpResult.Failed("$ok copiado(s), $failed con error (posible falta de espacio o destino no permitido)")
    }

    private suspend fun applyUpdates(plans: List<MovePlan>): OpResult = withContext(Dispatchers.IO) {
        var ok = 0
        val errors = ArrayList<String>()
        for (p in plans) {
            try {
                val v = ContentValues()
                if (norm(p.entry.relativePath) != norm(p.destPath)) v.put(MediaStore.MediaColumns.RELATIVE_PATH, norm(p.destPath))
                if (p.newName != null) v.put(MediaStore.MediaColumns.DISPLAY_NAME, p.newName)
                if (v.size() == 0) { ok++; continue }
                val n = resolver.update(uri(p.entry), v, null, null)
                if (n > 0) ok++ else errors.add(p.entry.name)
            } catch (e: Exception) {
                errors.add("${p.entry.name} (${e.message?.take(80)})")
            }
        }
        repo.refreshNow()
        if (errors.isEmpty()) OpResult.Done("$ok archivo(s) actualizado(s)")
        else OpResult.Failed("$ok correcto(s); ${errors.size} fallaron. Android no permite ese tipo de archivo en esa carpeta raíz o el volumen es distinto. Primer error: ${errors.first()}")
    }

    @Suppress("DEPRECATION")
    private fun ensureDirectory(rel: String) {
        runCatching { File(Environment.getExternalStorageDirectory(), rel).mkdirs() }
    }

    /** Crea una carpeta REAL en el almacenamiento compartido (visible en el administrador de archivos). */
    @Suppress("DEPRECATION")
    fun createFolder(parent: String, name: String): OpResult {
        val clean = NameRules.sanitizeFolderName(name) ?: return OpResult.Failed("Nombre de carpeta no válido")
        val rel = norm(parent) + clean + "/"
        val top = rel.substringBefore('/')
        if (top !in NameRules.StandardRoots) {
            return OpResult.Failed("Android sólo permite crear carpetas dentro de: ${NameRules.StandardRoots.joinToString(", ")}. Elige una de ellas como ubicación.")
        }
        val dir = File(Environment.getExternalStorageDirectory(), rel)
        if (dir.exists()) return OpResult.Failed("La carpeta ya existe: $rel")
        return if (dir.mkdirs()) OpResult.Done("Carpeta creada: $rel")
        else OpResult.Failed("Android no permitió crearla directamente. Se creará al mover el primer archivo a «$rel».")
    }

    fun renameFolder(oldPath: String, newName: String, all: List<MediaEntry>): OpResult {
        val old = norm(oldPath)
        val clean = NameRules.sanitizeFolderName(newName) ?: return OpResult.Failed("Nombre de carpeta no válido")
        val parent = old.trimEnd('/').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val newPath = parent + clean + "/"
        if (newPath == old) return OpResult.Done("Sin cambios")
        if (all.any { norm(it.relativePath).startsWith(newPath) }) return OpResult.Failed("Ya existe una carpeta con ese nombre")
        val affected = all.filter { norm(it.relativePath).startsWith(old) }
        if (affected.isEmpty()) return OpResult.Failed("La carpeta no contiene archivos de medios; renómbrala desde el administrador de archivos")
        val plans = affected.map { MovePlan(it, newPath + norm(it.relativePath).removePrefix(old), null) }
        return requestWrite(affected) { applyUpdates(plans) }
    }

    @Suppress("DEPRECATION")
    fun deleteFolder(path: String, all: List<MediaEntry>): OpResult {
        val p = norm(path)
        val affected = all.filter { norm(it.relativePath).startsWith(p) }
        if (affected.isEmpty()) return OpResult.Failed("No hay archivos de medios en esa carpeta")
        val sender = MediaStore.createDeleteRequest(resolver, affected.map(::uri)).intentSender
        return OpResult.NeedsConsent(sender) {
            repo.refreshNow()
            runCatching { File(Environment.getExternalStorageDirectory(), p).delete() }   // sólo si queda vacía
            OpResult.Done("${affected.size} archivo(s) eliminado(s) de «$p»")
        }
    }
}
