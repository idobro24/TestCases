package com.example.testcases.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Экспорт кейсов в CSV — в том же формате, который понимает импорт
 * (разделитель «;», UTF-8 с BOM: файл нормально открывается в Excel).
 */
object CsvExport {

    private val header = listOf(
        "Раздел", "Название", "Описание", "Предусловия",
        "Шаги", "Ожидаемый результат", "Приоритет", "Статус"
    )

    fun build(cases: List<TestCase>, sectionNames: Map<Long, String>): String {
        val sb = StringBuilder()
        sb.append(header.joinToString(";") { escape(it) }).append("\r\n")
        for (c in cases) {
            val steps = c.stepList
                .mapIndexed { i, s -> "${i + 1}. $s" }
                .joinToString("\n")
            val row = listOf(
                c.sectionId?.let { sectionNames[it] }.orEmpty(),
                c.title,
                c.description,
                c.preconditions,
                steps,
                c.expectedResult,
                c.priority.label,
                c.status.label
            )
            sb.append(row.joinToString(";") { escape(it) }).append("\r\n")
        }
        return sb.toString()
    }

    private fun escape(value: String): String {
        val needsQuotes = value.any { it == ';' || it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    /** Сохраняет файл во временную папку и открывает системное окно «Поделиться». */
    fun share(context: Context, fileName: String, csv: String) {
        try {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, fileName)
            val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
            file.writeBytes(bom + csv.toByteArray(Charsets.UTF_8))

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
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
