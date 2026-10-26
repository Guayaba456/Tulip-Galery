package com.tulip.app.logic

import kotlin.random.Random

data class FolderInfo(val name: String, val path: String, val count: Int)
data class Browse(val folders: List<FolderInfo>, val items: List<MediaEntry>)

object FolderExclusions {
    /** Normaliza a "Dir/Sub/" (sin barra inicial y con barra final). */
    fun normalize(path: String): String {
        val p = path.trim().trimStart('/')
        return if (p.isEmpty() || p.endsWith("/")) p else "$p/"
    }

    /** Clave estable: volumen + ruta relativa completa (no sólo el nombre, que puede repetirse). */
    fun key(volume: String, relativePath: String): String = "$volume:${normalize(relativePath)}"

    /** Una carpeta excluida afecta también a sus subcarpetas. */
    fun isExcluded(volume: String, relativePath: String, excludedKeys: Set<String>): Boolean {
        if (excludedKeys.isEmpty()) return false
        val k = key(volume, relativePath)
        return excludedKeys.any { it.isNotEmpty() && k.startsWith(it) }
    }
}

object FolderTree {
    fun browse(entries: List<MediaEntry>, current: String): Browse {
        val cur = FolderExclusions.normalize(current)
        val counts = LinkedHashMap<String, Int>()
        val direct = ArrayList<MediaEntry>()
        for (e in entries) {
            val p = FolderExclusions.normalize(e.relativePath)
            if (p == cur) {
                direct.add(e)
            } else if (p.startsWith(cur)) {
                val seg = p.removePrefix(cur).substringBefore('/')
                if (seg.isNotEmpty()) counts[seg] = (counts[seg] ?: 0) + 1
            }
        }
        val folders = counts.map { (seg, n) -> FolderInfo(seg, cur + seg + "/", n) }
            .sortedBy { it.name.lowercase() }
        return Browse(folders, direct)
    }

    fun flat(entries: List<MediaEntry>): List<FolderInfo> =
        entries.groupBy { FolderExclusions.normalize(it.relativePath) }
            .filterKeys { it.isNotEmpty() }
            .map { (p, l) -> FolderInfo(p.trimEnd('/').substringAfterLast('/'), p, l.size) }
            .sortedBy { it.path.lowercase() }
}

object Shuffler {
    const val MAX_QUEUE = 500

    fun <T> shuffleAvoidingRepeat(items: List<T>, random: Random, previousLast: T? = null): List<T> {
        val l = items.shuffled(random).toMutableList()
        if (l.size > 1 && previousLast != null && l[0] == previousLast) {
            val j = 1 + random.nextInt(l.size - 1)
            val t = l[0]; l[0] = l[j]; l[j] = t
        }
        return l
    }

    /** Cola aleatoria de vídeos sin repetidos, sin las carpetas excluidas, con tope por tanda. */
    fun buildVideoQueue(
        all: List<MediaEntry>,
        excludedKeys: Set<String>,
        random: Random,
        max: Int = MAX_QUEUE,
    ): List<MediaEntry> {
        val candidates = all.filter {
            it.kind == MediaKind.VIDEO && !FolderExclusions.isExcluded(it.volume, it.relativePath, excludedKeys)
        }
        return shuffleAvoidingRepeat(candidates, random).take(max)
    }
}

data class QueueState(val uris: List<String>, val index: Int, val positionMs: Long) {
    fun encode(): String = "$index|$positionMs\n" + uris.joinToString("\n")

    companion object {
        fun decode(s: String?): QueueState? {
            if (s.isNullOrBlank()) return null
            val lines = s.split("\n")
            val head = lines.first().split("|")
            if (head.size != 2) return null
            val idx = head[0].toIntOrNull() ?: return null
            val pos = head[1].toLongOrNull() ?: return null
            val uris = lines.drop(1).filter { it.isNotBlank() }
            if (uris.isEmpty()) return null
            return QueueState(uris, idx.coerceIn(0, uris.lastIndex), pos.coerceAtLeast(0))
        }
    }
}

object NameRules {
    private val forbidden = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
    val StandardRoots = setOf("DCIM", "Pictures", "Movies", "Music", "Download", "Documents", "Podcasts")

    /** Nombre de archivo válido; conserva la extensión original si el usuario la omite. */
    fun sanitizeFileName(input: String, originalName: String): String? {
        val n = input.trim()
        if (n.isEmpty() || n == "." || n == ".." || n.any { it in forbidden }) return null
        val origExt = originalName.substringAfterLast('.', "")
        val hasExt = n.contains('.') && n.substringAfterLast('.').isNotEmpty()
        return if (!hasExt && origExt.isNotEmpty()) "$n.$origExt" else n
    }

    fun sanitizeFolderName(input: String): String? {
        val n = input.trim()
        if (n.isEmpty() || n == "." || n == ".." || n.startsWith(".") || n.any { it in forbidden }) return null
        return n
    }

    /** "a.mp4" -> "a (1).mp4", "a (2).mp4"... hasta que `exists` devuelva false. */
    fun uniqueName(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (!exists(candidate)) return candidate
            i++
        }
    }
}
