package com.tulip.app.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.tulip.app.logic.Classifier
import com.tulip.app.logic.MediaEntry
import com.tulip.app.logic.MediaKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lee los archivos REALES del dispositivo desde MediaStore (no copia nada a una base privada).
 * Sólo se guardan en memoria los metadatos; las miniaturas las carga Coil bajo demanda.
 */
class MediaRepository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val resolver: ContentResolver get() = context.contentResolver
    private val _items = MutableStateFlow<List<MediaEntry>>(emptyList())
    val items: StateFlow<List<MediaEntry>> = _items.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    private var debounce: Job? = null
    private var started = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    /** Escucha cambios hechos desde cualquier app (crear, mover, renombrar, borrar). */
    fun start() {
        if (started) return
        started = true
        resolver.registerContentObserver(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), true, observer)
        refresh()
    }

    fun refresh() {
        debounce?.cancel()
        debounce = scope.launch { delay(400); refreshNow() }
    }

    suspend fun refreshNow() = withContext(Dispatchers.IO) {
        _items.value = query()
        _loaded.value = true
    }

    private fun query(): List<MediaEntry> {
        val out = ArrayList<MediaEntry>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.VOLUME_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
        )
        val sel = "${MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL"
        try {
            resolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                projection, sel, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val iPath = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val iVol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.VOLUME_NAME)
                val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val iMod = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val iDur = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val name = c.getString(iName) ?: continue
                    val mime = c.getString(iMime) ?: ""
                    val vol = c.getString(iVol) ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
                    val kind = Classifier.classify(mime, name)
                    val base = when (kind) {
                        MediaKind.IMAGE -> MediaStore.Images.Media.getContentUri(vol)
                        MediaKind.VIDEO -> MediaStore.Video.Media.getContentUri(vol)
                        MediaKind.AUDIO -> MediaStore.Audio.Media.getContentUri(vol)
                        else -> MediaStore.Files.getContentUri(vol)
                    }
                    val taken = if (c.isNull(iTaken)) null else c.getLong(iTaken).takeIf { it > 0 }
                    out.add(
                        MediaEntry(
                            id = id,
                            uri = ContentUris.withAppendedId(base, id).toString(),
                            name = name,
                            relativePath = c.getString(iPath) ?: "",
                            volume = vol,
                            mime = mime,
                            size = c.getLong(iSize),
                            dateAddedMs = c.getLong(iAdded) * 1000L,
                            dateModifiedMs = c.getLong(iMod) * 1000L,
                            dateTakenMs = taken,
                            durationMs = if (c.isNull(iDur)) 0L else c.getLong(iDur),
                            width = if (c.isNull(iW)) 0 else c.getInt(iW),
                            height = if (c.isNull(iH)) 0 else c.getInt(iH),
                            kind = kind,
                        ),
                    )
                }
            }
        } catch (e: SecurityException) {
            // Sin permiso de lectura: lista vacía; la interfaz muestra la pantalla de permisos.
        }
        return out
    }
}
