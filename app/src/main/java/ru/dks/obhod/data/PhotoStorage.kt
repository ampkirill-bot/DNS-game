package ru.dks.obhod.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Круг в относительных координатах: радиус задан от меньшей стороны изображения. */
data class PhotoCircle(val x: Float, val y: Float, val radius: Float)

/** Фото хранятся во внутренней памяти приложения: files/photos/. */
class PhotoStorage(private val context: Context) {

    private val dir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun newFile(): File = File(dir, "IMG_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Уменьшает фото с камеры до разумного размера и поворачивает по EXIF. */
    suspend fun normalize(file: File) = withContext(Dispatchers.IO) {
        runCatching { shrink(file) }
    }

    /** Копирует выбранное из галереи изображение во внутреннюю папку. */
    suspend fun importFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val f = newFile()
            context.contentResolver.openInputStream(uri)!!.use { input ->
                FileOutputStream(f).use { input.copyTo(it) }
            }
            runCatching { shrink(f) }
            f.absolutePath
        }.getOrNull()
    }

    /** Создаёт отдельную JPEG-копию с красными кругами, не изменяя исходный файл. */
    suspend fun annotate(path: String, circles: List<PhotoCircle>): String? = withContext(Dispatchers.IO) {
        val source = BitmapFactory.decodeFile(path) ?: return@withContext null
        val output = newFile()
        runCatching {
            val annotated = source.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(annotated)
            canvas.drawBitmap(source, 0f, 0f, null)
            val minSide = minOf(annotated.width, annotated.height)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.RED
                style = Paint.Style.STROKE
                strokeWidth = (minSide * 0.006f).coerceAtLeast(4f)
            }
            circles.forEach { c ->
                canvas.drawCircle(c.x * annotated.width, c.y * annotated.height,
                    c.radius * minSide, paint)
            }
            FileOutputStream(output).use { annotated.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            annotated.recycle()
            output.absolutePath
        }.getOrElse {
            output.delete()
            null
        }.also { source.recycle() }
    }

    fun delete(path: String) {
        runCatching { File(path).delete() }
    }

    private fun shrink(file: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return
        val maxSide = 2048
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2

        val rotation = when (ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (sample == 1 && rotation == 0f && file.length() < 1_500_000) return

        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return
        val bmp = if (rotation != 0f) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height,
                Matrix().apply { postRotate(rotation) }, true)
        } else decoded
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        if (bmp !== decoded) bmp.recycle()
        decoded.recycle()
    }
}
