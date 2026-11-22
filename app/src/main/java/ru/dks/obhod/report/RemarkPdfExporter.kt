package ru.dks.obhod.report

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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

/** Замечание и фотографии, подготовленные для отчёта. */
data class PdfRemark(val remark: Remark, val photos: List<String>)

/** ГПА или рабочая позиция с замечаниями. */
data class PdfGpaGroup(val title: String, val remarks: List<PdfRemark>) {
    constructor(gpa: Gpa, remarks: List<PdfRemark>) : this(gpa.fullTitle, remarks)
}

/** Общее название группы для новых экранов; старый тип сохранён для совместимости. */
typealias PdfGroup = PdfGpaGroup

/** Формирует офлайн PDF: четыре замечания на листе A4 в сетке 2 × 2. */
object RemarkPdfExporter {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 36f
    private const val CONTENT_BOTTOM = PAGE_H - 44f
    private const val CONTENT_W = PAGE_W - MARGIN * 2
    private const val GRID_GAP = 12f

    suspend fun export(
        context: Context,
        title: String,
        groups: List<PdfGroup>,
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
                writer.paragraph(title, writer.paint(21f, bold = true, color = Color.rgb(31, 78, 121)), 27f, 5f)
                writer.paragraph(
                    "Сформирован: ${format(System.currentTimeMillis())}",
                    writer.paint(10f, color = Color.DKGRAY), 14f, 2f,
                )
                writer.paragraph(
                    "Замечаний: ${all.size} · открыто: $open · устранено: ${all.size - open}",
                    writer.paint(11f, bold = true), 15f, 12f,
                )
                groups.filter { it.remarks.isNotEmpty() }.forEach { group ->
                    writer.groupTitle(group.title)
                    group.remarks.forEachIndexed { index, item -> writer.remark(item, index + 1) }
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
        private var slot = 0
        private var gridTop = MARGIN
        private var currentGroup = ""

        fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

        private fun startPage() {
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            canvas = page!!.canvas
            y = MARGIN
            slot = 0
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

        /** Заголовки отчёта занимают всю ширину; замечания ниже размещаются по ячейкам. */
        fun paragraph(text: String, p: Paint, lineHeight: Float, gap: Float = 5f, indent: Float = 0f) {
            text.split('\n').forEach { raw ->
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
                    canvas!!.drawText(rest.substring(0, count).trimEnd(), MARGIN + indent, y - p.ascent(), p)
                    y += lineHeight
                    rest = rest.substring(count).trimStart()
                }
            }
            y += gap
        }

        fun groupTitle(text: String) {
            // Каждый новый участок начинается с отдельной сетки, чтобы не смешивать заголовки и ячейки.
            if (slot > 0) {
                finishPage()
                startPage()
            }
            currentGroup = text
            drawGroupTitle(text)
        }

        private fun drawGroupTitle(text: String) {
            ensure(38f)
            canvas!!.drawRoundRect(RectF(MARGIN, y, PAGE_W - MARGIN, y + 32f), 5f, 5f, Paint().apply {
                color = Color.rgb(214, 228, 242)
            })
            y += 6f
            paragraph(text, paint(13f, bold = true, color = Color.rgb(31, 78, 121)), 19f, 10f, 8f)
            gridTop = y
            slot = 0
        }

        fun remark(item: PdfRemark, number: Int) {
            if (slot >= 4) {
                finishPage()
                startPage()
                drawGroupTitle("$currentGroup · продолжение")
            }
            val cellW = (CONTENT_W - GRID_GAP) / 2f
            val cellH = (CONTENT_BOTTOM - gridTop - GRID_GAP) / 2f
            val left = MARGIN + (slot % 2) * (cellW + GRID_GAP)
            val top = gridTop + (slot / 2) * (cellH + GRID_GAP)
            drawRemarkCell(item, number, left, top, cellW, cellH)
            slot++
        }

        private sealed interface CellBlock {
            val height: Float
        }

        private data class TextBlock(
            val lines: List<String>,
            val paint: Paint,
            val lineHeight: Float,
            val gap: Float,
        ) : CellBlock {
            override val height: Float = lines.size * lineHeight + gap
        }

        private data class PhotoBlock(
            val bitmap: Bitmap,
            val width: Float,
            val photoHeight: Float,
            val caption: String,
            val captionPaint: Paint,
        ) : CellBlock {
            override val height: Float = photoHeight + 17f
        }

        private fun textBlock(text: String, p: Paint, lineHeight: Float, gap: Float, width: Float): TextBlock {
            val lines = mutableListOf<String>()
            text.split('\n').forEach { raw ->
                var rest = raw.trim()
                if (rest.isEmpty()) {
                    lines.add("")
                    return@forEach
                }
                while (rest.isNotEmpty()) {
                    var count = p.breakText(rest, true, width, null).coerceAtLeast(1)
                    if (count < rest.length) {
                        val space = rest.lastIndexOf(' ', count)
                        if (space > 0) count = space
                    }
                    lines.add(rest.substring(0, count).trimEnd())
                    rest = rest.substring(count).trimStart()
                }
            }
            return TextBlock(lines, p, lineHeight, gap)
        }

        private fun photoBlock(path: String, number: Int, total: Int, width: Float): CellBlock {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return textBlock(
                    "Фото $number из $total: файл не найден или повреждён.",
                    paint(9f, color = Color.GRAY), 13f, 7f, width,
                )
            }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 1200 || bounds.outHeight / (sample * 2) >= 1200) sample *= 2
            val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return textBlock(
                    "Фото $number из $total: не удалось открыть.",
                    paint(9f, color = Color.GRAY), 13f, 7f, width,
                )
            val scale = min(width / bitmap.width, min(120f / bitmap.height, 1f))
            return PhotoBlock(
                bitmap = bitmap,
                width = bitmap.width * scale,
                photoHeight = bitmap.height * scale,
                caption = "Фото $number из $total",
                captionPaint = paint(8f, color = Color.GRAY),
            )
        }

        private fun drawRemarkCell(item: PdfRemark, number: Int, left: Float, top: Float, width: Float, height: Float) {
            val pad = 9f
            val contentW = width - pad * 2
            val contentH = height - pad * 2
            val rm = item.remark
            val blocks = mutableListOf<CellBlock>()
            blocks += textBlock(
                "Замечание №$number · ${format(rm.timestamp)}",
                paint(10.5f, bold = true), 14f, 2f, contentW,
            )
            blocks += textBlock(
                if (rm.resolved) "Статус: УСТРАНЕНО" else "Статус: ОТКРЫТО",
                paint(9.5f, bold = true, color = if (rm.resolved) Color.rgb(46, 125, 50) else Color.rgb(211, 47, 47)),
                13f, 6f, contentW,
            )
            blocks += textBlock(
                "Пояснение: ${rm.text.ifBlank { "без описания" }}",
                paint(10f), 14f, 8f, contentW,
            )
            if (item.photos.isEmpty()) {
                blocks += textBlock("Фотографии отсутствуют.", paint(9f, color = Color.GRAY), 13f, 0f, contentW)
            } else {
                item.photos.forEachIndexed { index, path ->
                    blocks += photoBlock(path, index + 1, item.photos.size, contentW)
                }
            }

            // Масштабируем всю ячейку целиком: длинный текст и все фотографии остаются в отчёте.
            val naturalHeight = blocks.sumOf { it.height.toDouble() }.toFloat().coerceAtLeast(1f)
            val scale = min(1f, contentH / naturalHeight)
            val c = canvas!!
            c.drawRoundRect(RectF(left, top, left + width, top + height), 5f, 5f, Paint().apply {
                color = Color.rgb(238, 242, 246)
            })
            c.save()
            c.clipRect(left + 1f, top + 1f, left + width - 1f, top + height - 1f)
            c.translate(left + pad, top + pad)
            c.scale(scale, scale)
            var blockY = 0f
            try {
                blocks.forEach { block ->
                    when (block) {
                        is TextBlock -> {
                            block.lines.forEach { line ->
                                if (line.isNotEmpty()) c.drawText(line, 0f, blockY - block.paint.ascent(), block.paint)
                                blockY += block.lineHeight
                            }
                            blockY += block.gap
                        }
                        is PhotoBlock -> {
                            c.drawBitmap(
                                block.bitmap, null,
                                RectF(0f, blockY, block.width, blockY + block.photoHeight),
                                Paint(Paint.ANTI_ALIAS_FLAG),
                            )
                            c.drawRect(0f, blockY, block.width, blockY + block.photoHeight, Paint().apply {
                                color = Color.LTGRAY
                                style = Paint.Style.STROKE
                                strokeWidth = 0.7f
                            })
                            blockY += block.photoHeight + 11f - block.captionPaint.ascent()
                            c.drawText(block.caption, 0f, blockY, block.captionPaint)
                            blockY += 6f + block.captionPaint.ascent()
                        }
                    }
                }
            } finally {
                c.restore()
                blocks.filterIsInstance<PhotoBlock>().forEach { it.bitmap.recycle() }
            }
        }

        override fun close() {
            if (page != null) finishPage()
        }
    }
}
