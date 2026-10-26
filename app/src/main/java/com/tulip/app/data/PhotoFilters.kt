package com.tulip.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import com.tulip.app.logic.MediaEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PhotoFilters {
    const val DEST = "Pictures/Tulip/"

    /** Decodifica con orientación EXIF aplicada y limitando el tamaño (no carga imágenes enormes enteras). */
    fun decode(ctx: Context, uri: Uri, maxSide: Int): Bitmap? = runCatching {
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val m = maxOf(info.size.width, info.size.height)
            if (m > maxSide) {
                val f = maxSide.toFloat() / m
                decoder.setTargetSize((info.size.width * f).toInt().coerceAtLeast(1), (info.size.height * f).toInt().coerceAtLeast(1))
            }
        }
    }.getOrNull()

    /** Guarda una COPIA nueva en Pictures/Tulip; el original no se toca. */
    suspend fun saveCopy(ctx: Context, entry: MediaEntry, matrix: FloatArray): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val src = decode(ctx, Uri.parse(entry.uri), 4096) ?: error("No se pudo leer la imagen")
            val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix)) }
            Canvas(out).drawBitmap(src, 0f, 0f, paint)
            src.recycle()
            val base = entry.name.substringBeforeLast('.')
            val name = "${base}_tulip_${System.currentTimeMillis() / 1000}.jpg"
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, DEST)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val newUri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v)
                ?: error("No se pudo crear el archivo")
            try {
                resolver.openOutputStream(newUri)!!.use { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                resolver.update(newUri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(newUri, null, null)
                throw e
            } finally {
                out.recycle()
            }
            DEST + name
        }
    }
}
