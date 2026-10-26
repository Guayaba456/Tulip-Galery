package com.tulip.app.logic

enum class MediaKind { IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, OTHER }

/** Fila de MediaStore, sin dependencias de Android (probable con pruebas JVM). Fechas en milisegundos. */
data class MediaEntry(
    val id: Long,
    val uri: String,
    val name: String,
    val relativePath: String,
    val volume: String,
    val mime: String,
    val size: Long,
    val dateAddedMs: Long,
    val dateModifiedMs: Long,
    /** Fecha de captura: null si Android no la proporciona (no se inventa). */
    val dateTakenMs: Long?,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val kind: MediaKind,
) {
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
    val folderKey: String get() = FolderExclusions.key(volume, relativePath)
    val displayPath: String get() = relativePath + name
}

object Classifier {
    private val archiveExt = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")
    private val archiveMime = setOf(
        "application/zip", "application/x-rar-compressed", "application/vnd.rar",
        "application/x-7z-compressed", "application/gzip", "application/x-tar",
    )
    private val docExt = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "odt", "ods", "odp",
        "rtf", "csv", "epub", "md",
    )

    fun classify(mime: String?, name: String): MediaKind {
        val m = (mime ?: "").lowercase()
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            m.startsWith("image/") -> MediaKind.IMAGE
            m.startsWith("video/") -> MediaKind.VIDEO
            m.startsWith("audio/") -> MediaKind.AUDIO
            ext in archiveExt || m in archiveMime -> MediaKind.ARCHIVE
            ext in docExt || m.startsWith("text/") || m == "application/pdf" ||
                m.contains("msword") || m.contains("officedocument") || m.contains("opendocument") -> MediaKind.DOCUMENT
            else -> MediaKind.OTHER
        }
    }
}
