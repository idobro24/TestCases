package com.example.testcases.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Экспорт кейсов в CSV или Excel (.xlsx) — в том же формате, который понимает импорт.
 * CSV: разделитель «;», UTF-8 с BOM.
 */
object CsvExport {

    private const val MIME_CSV = "text/csv"
    private const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    private val header = listOf(
        "Раздел", "Название", "Описание", "Предусловия",
        "Шаги", "Ожидаемый результат", "Приоритет", "Статус",
        "Баг", "Описание бага"
    )

    /** Таблица для экспорта: первая строка — заголовок. */
    fun table(cases: List<TestCase>, sectionNames: Map<Long, String>): List<List<String>> {
        val rows = ArrayList<List<String>>(cases.size + 1)
        rows.add(header)
        for (c in cases) {
            val steps = c.stepList
                .mapIndexed { i, s -> "${i + 1}. $s" }
                .joinToString("\n")
            rows.add(
                listOf(
                    c.sectionId?.let { sectionNames[it] }.orEmpty(),
                    c.title,
                    c.description,
                    c.preconditions,
                    steps,
                    c.expectedResult,
                    c.priority.label,
                    c.status.label,
                    if (c.hasBug) "Да" else "",
                    c.bugDescription
                )
            )
        }
        return rows
    }

    fun build(cases: List<TestCase>, sectionNames: Map<Long, String>): String {
        val sb = StringBuilder()
        for (row in table(cases, sectionNames)) {
            sb.append(row.joinToString(";") { escape(it) }).append("\r\n")
        }
        return sb.toString()
    }

    private fun escape(value: String): String {
        val needsQuotes = value.any { it == ';' || it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    /** CSV: сохраняет файл во временную папку и открывает окно «Поделиться». */
    fun share(context: Context, fileName: String, csv: String) {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        shareFile(context, fileName, bom + csv.toByteArray(Charsets.UTF_8), MIME_CSV)
    }

    /** Excel: собирает .xlsx и открывает окно «Поделиться». */
    fun shareXlsx(
        context: Context,
        fileName: String,
        cases: List<TestCase>,
        sectionNames: Map<Long, String>
    ) {
        val bytes = try {
            XlsxWriter.build(table(cases, sectionNames))
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось создать Excel-файл: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        shareFile(context, fileName, bytes, MIME_XLSX)
    }

    private fun shareFile(context: Context, fileName: String, bytes: ByteArray, mime: String) {
        try {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, fileName)
            file.writeBytes(bytes)

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Поделиться кейсами"))
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось поделиться файлом: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
