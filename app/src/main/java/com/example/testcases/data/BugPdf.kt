package com.example.testcases.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PDF-отчёт для разработчика: кейсы с шагами, описанием бага и скриншотами.
 * Текст в PDF настоящий — его можно выделять, копировать и искать.
 * Страница A4 (595 × 842 пт), без сторонних библиотек.
 */
object BugPdf {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val CONTENT_W = PAGE_W - 2 * MARGIN
    private const val MAX_IMAGE_H = 300f
    private const val IMAGE_GAP = 8f

    /** Скриншоты кладём вдвое плотнее точек страницы: чётко и файл не раздувается. */
    private const val IMAGE_DENSITY = 2f

    private const val COLOR_TEXT = 0xFF1C1B1F.toInt()
    private const val COLOR_GRAY = 0xFF6B6F7B.toInt()
    private const val COLOR_ACCENT = 0xFF3648C9.toInt()
    private const val COLOR_BUG = 0xFFC62828.toInt()
    private const val COLOR_LINE = 0xFFDADEE9.toInt()

    private fun paint(size: Float, color: Int, bold: Boolean = false) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

    private val docTitle = paint(18f, COLOR_TEXT, bold = true)
    private val docMeta = paint(9f, COLOR_GRAY)
    private val caseTitle = paint(12.5f, COLOR_ACCENT, bold = true)
    private val meta = paint(8.5f, COLOR_GRAY)
    private val label = paint(9.5f, COLOR_TEXT, bold = true)
    private val bugLabel = paint(9.5f, COLOR_BUG, bold = true)
    private val body = paint(10f, COLOR_TEXT)

    /** Пишет текст и картинки сверху вниз, сама переходит на новую страницу. */
    private class Writer(private val doc: PdfDocument) {
        private var pageNo = 0
        private var page: PdfDocument.Page? = null
        private lateinit var canvas: Canvas
        var y = 0f

        private val footer = paint(8f, COLOR_GRAY)
        private val linePaint = Paint().apply {
            color = COLOR_LINE
            strokeWidth = 0.8f
        }
        private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val framePaint = Paint().apply {
            color = COLOR_LINE
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
        }

        private fun newPage() {
            finish()
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            page = p
            canvas = p.canvas
            y = MARGIN
        }

        /** Закрывает текущую страницу, подписав её номер. */
        fun finish() {
            val p = page ?: return
            val n = pageNo.toString()
            canvas.drawText(n, PAGE_W - MARGIN - footer.measureText(n), PAGE_H - 20f, footer)
            doc.finishPage(p)
            page = null
        }

        /** Если до низа страницы меньше [h], начинает новую. */
        fun ensure(h: Float) {
            if (page == null || y + h > PAGE_H - MARGIN) newPage()
        }

        fun space(h: Float) {
            y += h
        }

        /** Текст с переносом строк; каждая строка рисуется отдельно, поэтому абзац может переходить на другую страницу. */
        fun text(raw: String, p: TextPaint, indent: Float = 0f, after: Float = 4f) {
            val s = raw.replace("\r", "").replace("\t", "    ").trimEnd()
            if (s.isEmpty()) return
            val width = (CONTENT_W - indent).toInt()
            val layout = StaticLayout.Builder.obtain(s, 0, s.length, p, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
            for (i in 0 until layout.lineCount) {
                val top = layout.getLineTop(i)
                val h = (layout.getLineBottom(i) - top).toFloat()
                ensure(h)
                val line = s.substring(layout.getLineStart(i), layout.getLineEnd(i)).trimEnd('\n')
                if (line.isNotBlank()) {
                    canvas.drawText(line, MARGIN + indent, y + (layout.getLineBaseline(i) - top), p)
                }
                y += h
            }
            y += after
        }

        fun divider() {
            ensure(4f)
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, linePaint)
            y += 4f
        }

        /** Ряд картинок одной высоты строки; [items] — картинка и её размер на странице. */
        fun imageRow(items: List<Triple<Bitmap, Float, Float>>) {
            if (items.isEmpty()) return
            val rowH = items.maxOf { it.third }
            ensure(rowH)
            var x = MARGIN
            for ((bmp, w, h) in items) {
                val rect = RectF(x, y, x + w, y + h)
                canvas.drawBitmap(bmp, null, rect, imagePaint)
                canvas.drawRect(rect, framePaint)
                x += w + IMAGE_GAP
            }
            y += rowH + IMAGE_GAP
        }
    }

    // ------------------------------------------------------------------ сборка

    suspend fun build(
        context: Context,
        scopeTitle: String,
        cases: List<TestCase>,
        sectionNames: Map<Long, String>
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
        val safe = scopeTitle.replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').ifEmpty { "cases" }
        val file = File(dir, "bugs_${safe}_$stamp.pdf")

        val doc = PdfDocument()
        try {
            val w = Writer(doc)
            val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
            val bugs = cases.count { it.hasBug }

            w.ensure(60f)
            w.text("Отчёт по багам", docTitle, after = 2f)
            w.text("$scopeTitle   ·   $date   ·   кейсов: ${cases.size}, с багом: $bugs", docMeta, after = 8f)
            w.divider()
            w.space(10f)

            for (c in cases) {
                writeCase(context, w, c, c.sectionId?.let { sectionNames[it] })
            }
            w.finish()

            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        file
    }

    private fun section(w: Writer, title: String, value: String, titlePaint: TextPaint = label) {
        if (value.isBlank()) return
        w.ensure(30f)
        w.text(title, titlePaint, after = 1f)
        w.text(value, body, after = 6f)
    }

    private fun writeCase(context: Context, w: Writer, c: TestCase, sectionName: String?) {
        // Заголовок кейса не должен остаться один внизу страницы
        w.ensure(80f)
        w.text("${c.code}   ${c.title}", caseTitle, after = 2f)
        val info = listOfNotNull(
            sectionName?.let { "Раздел: $it" },
            "Приоритет: ${c.priority.label}",
            "Статус: ${c.status.label}"
        ).joinToString("   ·   ")
        w.text(info, meta, after = 8f)

        section(w, "Описание", c.description)
        section(w, "Предусловия", c.preconditions)

        val steps = c.stepList.filter { it.isNotBlank() }
        if (steps.isNotEmpty()) {
            w.ensure(30f)
            w.text("Шаги", label, after = 1f)
            steps.forEachIndexed { i, s -> w.text("${i + 1}. $s", body, indent = 6f, after = 2f) }
            w.space(4f)
        }

        section(w, "Ожидаемый результат", c.expectedResult)

        if (c.hasBug) {
            w.ensure(30f)
            w.text("Фактический результат (баг)", bugLabel, after = 1f)
            w.text(c.bugDescription.ifBlank { "—" }, body, after = 6f)
            writeScreens(context, w, c.bugScreenList)
        }

        w.space(4f)
        w.divider()
        w.space(12f)
    }

    /** Скриншоты рядами: узкие (с телефона) встают по 2–3 в ряд, широкие — по одному. */
    private fun writeScreens(context: Context, w: Writer, names: List<String>) {
        val row = ArrayList<Triple<Bitmap, Float, Float>>()
        var rowW = 0f

        fun flush() {
            w.imageRow(row)
            row.forEach { it.first.recycle() }
            row.clear()
            rowW = 0f
        }

        for (name in names) {
            val src = BugFiles.decode(context, name, 1600) ?: continue
            val ratio = src.width.toFloat() / src.height
            var dispW = minOf(CONTENT_W, MAX_IMAGE_H * ratio)
            var dispH = dispW / ratio
            if (dispH > MAX_IMAGE_H) {
                dispH = MAX_IMAGE_H
                dispW = dispH * ratio
            }

            val tw = (dispW * IMAGE_DENSITY).toInt().coerceAtLeast(1)
            val th = (dispH * IMAGE_DENSITY).toInt().coerceAtLeast(1)
            val bmp = if (tw < src.width && th < src.height) {
                Bitmap.createScaledBitmap(src, tw, th, true).also { if (it != src) src.recycle() }
            } else {
                src
            }

            if (row.isNotEmpty() && rowW + IMAGE_GAP + dispW > CONTENT_W) flush()
            rowW += (if (row.isEmpty()) 0f else IMAGE_GAP) + dispW
            row.add(Triple(bmp, dispW, dispH))
        }
        flush()
    }

    // ------------------------------------------------------------------ отправка

    fun share(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Отправить отчёт"))
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось поделиться файлом: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
