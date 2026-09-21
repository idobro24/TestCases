package com.example.testcases.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Минимальный читатель Excel-файлов (.xlsx) без сторонних библиотек.
 * .xlsx — это ZIP с XML внутри: берём первый видимый лист и возвращаем его
 * как таблицу строк. Номер строки в списке = номер строки в Excel.
 */
object XlsxReader {

    class NotXlsx(message: String) : Exception(message)

    private const val MAX_UNZIPPED = 60L * 1024 * 1024

    fun read(bytes: ByteArray): List<List<String>> {
        val entries = HashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                val wanted = name == "xl/workbook.xml" ||
                    name == "xl/_rels/workbook.xml.rels" ||
                    name == "xl/sharedStrings.xml" ||
                    (name.startsWith("xl/worksheets/") && name.endsWith(".xml"))
                if (!wanted) continue
                val data = zip.readBytes()
                total += data.size
                if (total > MAX_UNZIPPED) throw NotXlsx("Файл слишком большой после распаковки.")
                entries[name] = data
            }
        }
        if (entries.isEmpty()) {
            throw NotXlsx("Это не файл Excel (.xlsx). Похоже на Word или другой архив.")
        }

        val sheetPath = firstSheetPath(entries["xl/workbook.xml"], entries["xl/_rels/workbook.xml.rels"])
            ?.takeIf { it in entries }
            ?: entries.keys.filter { it.startsWith("xl/worksheets/") }.minOrNull()
            ?: throw NotXlsx("В файле Excel не найден лист с данными.")

        val shared = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        return parseSheet(entries.getValue(sheetPath), shared)
    }

    private fun newParser(data: ByteArray): XmlPullParser {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(data), null)
        return parser
    }

    /** Путь к XML первого видимого листа (по порядку вкладок в книге). */
    private fun firstSheetPath(workbook: ByteArray?, rels: ByteArray?): String? {
        if (workbook == null || rels == null) return null

        var rid: String? = null
        val wp = newParser(workbook)
        var ev = wp.eventType
        while (ev != XmlPullParser.END_DOCUMENT && rid == null) {
            if (ev == XmlPullParser.START_TAG && wp.name == "sheet") {
                var state: String? = null
                var id: String? = null
                for (i in 0 until wp.attributeCount) {
                    val n = wp.getAttributeName(i)
                    if (n == "state") state = wp.getAttributeValue(i)
                    if (n == "r:id" || n.endsWith(":id")) id = wp.getAttributeValue(i)
                }
                if (state != "hidden" && state != "veryHidden") rid = id
            }
            ev = wp.next()
        }
        if (rid == null) return null

        var target: String? = null
        val rp = newParser(rels)
        ev = rp.eventType
        while (ev != XmlPullParser.END_DOCUMENT && target == null) {
            if (ev == XmlPullParser.START_TAG &&
                rp.name == "Relationship" &&
                rp.getAttributeValue(null, "Id") == rid
            ) {
                target = rp.getAttributeValue(null, "Target")
            }
            ev = rp.next()
        }
        val t = target ?: return null
        return if (t.startsWith("/")) t.removePrefix("/") else "xl/$t"
    }

    /** Общая таблица строк: ячейки хранят только номера записей из неё. */
    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val out = ArrayList<String>()
        val p = newParser(xml)
        var inSi = false
        var inT = false
        var inPhonetic = false
        val sb = StringBuilder()
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "si" -> {
                        inSi = true
                        sb.setLength(0)
                    }
                    "rPh" -> inPhonetic = true
                    "t" -> inT = inSi && !inPhonetic
                }
                XmlPullParser.TEXT -> if (inT) sb.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "t" -> inT = false
                    "rPh" -> inPhonetic = false
                    "si" -> {
                        out.add(clean(sb.toString()))
                        inSi = false
                    }
                }
            }
            event = p.next()
        }
        return out
    }

    private fun parseSheet(xml: ByteArray, shared: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val p = newParser(xml)

        var rowCells: ArrayList<String>? = null
        var lastRowNo = 0
        var colIdx = 0
        var cellType = ""
        var inV = false
        var inIs = false
        var inT = false
        val text = StringBuilder()

        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> {
                        val r = p.getAttributeValue(null, "r")?.toIntOrNull() ?: (lastRowNo + 1)
                        while (lastRowNo + 1 < r) {
                            rows.add(emptyList())
                            lastRowNo++
                        }
                        lastRowNo = r
                        rowCells = ArrayList()
                        colIdx = 0
                    }
                    "c" -> {
                        val ref = p.getAttributeValue(null, "r")
                        if (ref != null) colIdx = columnIndex(ref)
                        cellType = p.getAttributeValue(null, "t") ?: ""
                        text.setLength(0)
                    }
                    "v" -> inV = true
                    "is" -> inIs = true
                    "t" -> inT = inIs
                }
                XmlPullParser.TEXT -> if (inV || inT) text.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "is" -> inIs = false
                    "c" -> {
                        val raw = text.toString()
                        val value = when (cellType) {
                            "s" -> raw.trim().toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                            "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
                            "e" -> ""
                            else -> clean(raw)
                        }
                        val cells = rowCells
                        if (cells != null) {
                            while (cells.size < colIdx) cells.add("")
                            if (cells.size == colIdx) cells.add(value) else cells[colIdx] = value
                        }
                        colIdx++
                    }
                    "row" -> {
                        rowCells?.let { rows.add(it) }
                        rowCells = null
                    }
                }
            }
            event = p.next()
        }
        return rows
    }

    /** «B3» → 1, «AA1» → 26. */
    private fun columnIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            n = when (ch) {
                in 'A'..'Z' -> n * 26 + (ch - 'A' + 1)
                in 'a'..'z' -> n * 26 + (ch - 'a' + 1)
                else -> break
            }
        }
        return (n - 1).coerceAtLeast(0)
    }

    private fun clean(s: String): String =
        s.replace("_x000D_", "").replace("\r\n", "\n").replace('\r', '\n')
}
