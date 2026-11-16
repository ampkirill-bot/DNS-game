package ru.dks.obhod.report

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.dks.obhod.data.Gpa
import ru.dks.obhod.data.Remark
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

data class PdfRemark(val remark: Remark, val photos: List<String>)
data class PdfGpaGroup(val gpa: Gpa, val remarks: List<PdfRemark>)

/** Формирует офлайн PDF с замечаниями и фотографиями. Размер страницы — A4 при 72 dpi. */
object RemarkPdfExporter {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 42f
    private const val CONTENT_BOTTOM = PAGE_H - 48f
    private const val CONTENT_W = PAGE_W - MARGIN * 2

    suspend fun export(
        context: Context,
        title: String,
        groups: List<PdfGpaGroup>,
        fileName: String,
    ): File = withContext(Dispatchers.IO) {
        require(groups.any { it.remarks.isNotEmpty() }) { "Нет замечаний для отчёта" }
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val out = File(dir, fileName)
        val document = PdfDocument()
        try {
            Writer(document).use { writer ->
                val all = groups.flatMap { it.remarks }
                val open = all.count { !it.remark.resolved }
                writer.paragraph(title, writer.paint(22f, bold = true, color = Color.rgb(31, 78, 121)), 29f, 5f)
                writer.paragraph(
                    "Сформирован: ${format(System.currentTimeMillis())}",
                    writer.paint(10f, color = Color.DKGRAY), 14f, 2f,
                )
                writer.paragraph(
                    "Замечаний: ${all.size} · открыто: $open · устранено: ${all.size - open}",
                    writer.paint(11f, bold = true), 15f, 12f,
                )
                groups.filter { it.remarks.isNotEmpty() }.forEach { group ->
                    writer.groupTitle("${group.gpa.plant.title} · ГПА-${group.gpa.number}")
                    group.remarks.forEachIndexed { index, item ->
                        writer.remark(item, index + 1)
                    }
                }
            }
            FileOutputStream(out).use { document.writeTo(it) }
            out
        } catch (t: Throwable) {
            out.delete()
            throw t
        } finally {
            document.close()
        }
    }

    fun share(context: Context, file: File, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Сохранить или отправить PDF"))
    }

    private fun format(ts: Long): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru")).format(Date(ts))

    private class Writer(private val document: PdfDocument) : AutoCloseable {
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var pageNumber = 0
        private var y = MARGIN

        fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            textColor(color)
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

        private fun Paint.textColor(value: Int) { color = value }

        private fun startPage() {
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            canvas = page!!.canvas
            y = MARGIN
            if (pageNumber > 1) {
                val p = paint(9f, color = Color.GRAY)
                canvas!!.drawText("Отчёт по замечаниям · продолжение", MARGIN, y - p.ascent(), p)
                y += 22f
            }
        }

        private fun finishPage() {
            val c = canvas ?: return
            val p = paint(9f, color = Color.GRAY)
            c.drawText("Обход ДКС · стр. $pageNumber", MARGIN, PAGE_H - 22f, p)
            document.finishPage(page!!)
            page = null
            canvas = null
        }

        private fun ensure(height: Float) {
            if (page == null) startPage()
            if (y + height > CONTENT_BOTTOM && y > MARGIN + 1f) {
                finishPage()
                startPage()
            }
        }

        fun paragraph(text: String, p: Paint, lineHeight: Float, gap: Float = 5f, indent: Float = 0f) {
            val paragraphs = text.split('\n')
            paragraphs.forEach { raw ->
                var rest = raw.trim()
                if (rest.isEmpty()) {
                    ensure(lineHeight)
                    y += lineHeight
                    return@forEach
                }
                while (rest.isNotEmpty()) {
                    ensure(lineHeight)
                    val available = CONTENT_W - indent
                    var count = p.breakText(rest, true, available, null).coerceAtLeast(1)
                    if (count < rest.length) {
                        val space = rest.lastIndexOf(' ', count)
                        if (space > 0) count = space
                    }
                    val line = rest.substring(0, count).trimEnd()
                    canvas!!.drawText(line, MARGIN + indent, y - p.ascent(), p)
                    y += lineHeight
                    rest = rest.substring(count).trimStart()
                }
            }
            y += gap
        }

        fun groupTitle(text: String) {
            // Не оставляем название участка внизу страницы без первого замечания.
            ensure(313f)
            val c = canvas!!
            c.drawRoundRect(RectF(MARGIN, y, PAGE_W - MARGIN, y + 32f), 5f, 5f, Paint().apply {
                color = Color.rgb(214, 228, 242)
            })
            y += 6f
            paragraph(text, paint(14f, bold = true, color = Color.rgb(31, 78, 121)), 20f, 9f, 8f)
        }

        fun remark(item: PdfRemark, number: Int) {
            val rm = item.remark
            // Обычное замечание с фото целиком переносим на следующую страницу.
            ensure(270f)
            paragraph(
                "Замечание №$number · ${format(rm.timestamp)}",
                paint(12f, bold = true), 17f, 1f,
            )
            paragraph(
                if (rm.resolved) "Статус: УСТРАНЕНО" else "Статус: ОТКРЫТО",
                paint(10f, bold = true, color = if (rm.resolved) Color.rgb(46, 125, 50) else Color.rgb(211, 47, 47)),
                14f, 5f,
            )
            paragraph(
                "Пояснение: ${rm.text.ifBlank { "без описания" }}",
                paint(11f), 16f, 8f,
            )
            if (item.photos.isEmpty()) {
                paragraph("Фотографии отсутствуют.", paint(10f, color = Color.GRAY), 14f, 7f)
            } else {
                item.photos.forEachIndexed { index, path -> photo(path, index + 1, item.photos.size) }
            }
            ensure(14f)
            canvas!!.drawLine(MARGIN, y, PAGE_W - MARGIN, y, Paint().apply { color = Color.LTGRAY })
            y += 14f
        }

        private fun photo(path: String, number: Int, total: Int) {
            val file = File(path)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                paragraph("Фото $number из $total: файл не найден или повреждён.", paint(10f, color = Color.GRAY), 14f, 7f)
                return
            }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 1400 || bounds.outHeight / (sample * 2) >= 1400) sample *= 2
            val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            if (bitmap == null) {
                paragraph("Фото $number из $total: не удалось открыть.", paint(10f, color = Color.GRAY), 14f, 7f)
                return
            }
            try {
                // Компактное фото: на первой странице помещаются минимум два замечания с фото.
                val scale = min(CONTENT_W / bitmap.width, min(160f / bitmap.height, 1f))
                val w = bitmap.width * scale
                val h = bitmap.height * scale
                ensure(h + 25f)
                val c = canvas!!
                c.drawBitmap(bitmap, null, RectF(MARGIN, y, MARGIN + w, y + h), Paint(Paint.ANTI_ALIAS_FLAG))
                c.drawRect(MARGIN, y, MARGIN + w, y + h, Paint().apply {
                    color = Color.LTGRAY
                    style = Paint.Style.STROKE
                    strokeWidth = 0.7f
                })
                y += h + 4f
                paragraph("Фото $number из $total", paint(9f, color = Color.GRAY), 12f, 8f)
            } finally {
                bitmap.recycle()
            }
        }

        override fun close() {
            if (page != null) finishPage()
        }
    }
}
