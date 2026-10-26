package com.tulip.app.logic

enum class SortField { NAME, SIZE, MODIFIED, ADDED, TAKEN }

data class SortOrder(val field: SortField, val ascending: Boolean) {
    fun encode(): String = "${field.name}:${if (ascending) "ASC" else "DESC"}"

    companion object {
        val Default = SortOrder(SortField.MODIFIED, ascending = false)

        fun decode(s: String?): SortOrder {
            if (s.isNullOrBlank()) return Default
            val parts = s.split(":")
            if (parts.size != 2) return Default
            val f = SortField.entries.firstOrNull { it.name == parts[0] } ?: return Default
            return SortOrder(f, parts[1] != "DESC")
        }
    }
}

object Sorting {
    fun sort(list: List<MediaEntry>, order: SortOrder): List<MediaEntry> {
        if (order.field == SortField.TAKEN) {
            // Las entradas sin fecha de captura van siempre al final: no se inventa ninguna fecha.
            val (with, without) = list.partition { it.dateTakenMs != null }
            val sorted = with.sortedBy { it.dateTakenMs }
            return (if (order.ascending) sorted else sorted.reversed()) + without
        }
        val cmp: Comparator<MediaEntry> = when (order.field) {
            SortField.NAME -> Comparator { a, b -> a.name.compareTo(b.name, ignoreCase = true) }
            SortField.SIZE -> Comparator { a, b -> a.size.compareTo(b.size) }
            SortField.MODIFIED -> Comparator { a, b -> a.dateModifiedMs.compareTo(b.dateModifiedMs) }
            SortField.ADDED -> Comparator { a, b -> a.dateAddedMs.compareTo(b.dateAddedMs) }
            SortField.TAKEN -> Comparator { _, _ -> 0 }
        }
        return list.sortedWith(if (order.ascending) cmp else cmp.reversed())
    }
}

data class MediaFilter(
    val query: String = "",
    val kinds: Set<MediaKind> = emptySet(),
    val extensions: Set<String> = emptySet(),
    val minSize: Long = 0L,
    val maxSize: Long = Long.MAX_VALUE,
    val folderContains: String = "",
    val modifiedAfterMs: Long = 0L,
) {
    fun matches(e: MediaEntry): Boolean {
        if (kinds.isNotEmpty() && e.kind !in kinds) return false
        if (extensions.isNotEmpty() && e.extension !in extensions) return false
        if (e.size < minSize || e.size > maxSize) return false
        if (modifiedAfterMs > 0 && e.dateModifiedMs < modifiedAfterMs) return false
        if (folderContains.isNotBlank() && !e.relativePath.contains(folderContains.trim(), ignoreCase = true)) return false
        // La búsqueda mira nombre y ruta.
        if (query.isNotBlank() && !e.displayPath.contains(query.trim(), ignoreCase = true)) return false
        return true
    }

    fun apply(list: List<MediaEntry>): List<MediaEntry> = list.filter { matches(it) }
}
