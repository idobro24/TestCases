package com.example.testcases.data

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Минимальная запись Excel-файла (.xlsx) без сторонних библиотек.
 * Один лист: первая строка — заголовок (белый жирный текст на синем фоне, закреплена),
 * остальные — данные с переносом строк в ячейках. Весь текст пишется как текст,
 * поэтому значение вроде «=1+1» Excel не превратит в формулу.
 */
object XlsxWriter {

    private val widths = listOf(16, 36, 34, 30, 50, 42, 12, 15)

    private const val NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val NS_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"

    private const val XML_HEAD = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF3648C9"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style="thin"><color rgb="FFDADEE9"/></left><right style="thin"><color rgb="FFDADEE9"/></right><top style="thin"><color rgb="FFDADEE9"/></top><bottom style="thin"><color rgb="FFDADEE9"/></bottom><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf><xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""

    /** [rows] — таблица целиком, первая строка — заголовок. */
    fun build(rows: List<List<String>>, sheetName: String = "Кейсы"): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", CONTENT_TYPES)
            put("_rels/.rels", ROOT_RELS)
            put("xl/workbook.xml", workbook(sheetName))
            put("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            put("xl/styles.xml", STYLES)
            put("xl/worksheets/sheet1.xml", sheet(rows))
        }
        return out.toByteArray()
    }

    private fun workbook(sheetName: String): String =
        XML_HEAD + "\n" +
            "<workbook xmlns=\"$NS_MAIN\" xmlns:r=\"$NS_REL\"><sheets>" +
            "<sheet name=\"${esc(sheetName)}\" sheetId=\"1\" r:id=\"rId1\"/>" +
            "</sheets></workbook>"

    private fun sheet(rows: List<List<String>>): String {
        val colCount = (rows.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)
        val lastRef = columnName(colCount - 1) + rows.size.coerceAtLeast(1)

        val sb = StringBuilder()
        sb.append(XML_HEAD).append('\n')
        sb.append("<worksheet xmlns=\"$NS_MAIN\">")
        sb.append("<dimension ref=\"A1:$lastRef\"/>")
        sb.append(
            "<sheetViews><sheetView workbookViewId=\"0\">" +
                "<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>" +
                "<selection pane=\"bottomLeft\" activeCell=\"A2\" sqref=\"A2\"/>" +
                "</sheetView></sheetViews>"
        )
        sb.append("<sheetFormatPr defaultRowHeight=\"15\"/>")

        sb.append("<cols>")
        for (i in 0 until colCount) {
            val w = widths.getOrElse(i) { 20 }
            sb.append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>")
        }
        sb.append("</cols>")

        sb.append("<sheetData>")
        for ((r, row) in rows.withIndex()) {
            val style = if (r == 0) 1 else 2
            sb.append("<row r=\"${r + 1}\">")
            for ((c, text) in row.withIndex()) {
                if (text.isEmpty()) continue
                sb.append("<c r=\"${columnName(c)}${r + 1}\" s=\"$style\" t=\"inlineStr\">")
                sb.append("<is><t xml:space=\"preserve\">").append(esc(text)).append("</t></is></c>")
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    /** 0 → A, 25 → Z, 26 → AA. */
    private fun columnName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        do {
            sb.insert(0, 'A' + n % 26)
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    /** Экранирует спецсимволы XML и выбрасывает управляющие символы, которых в XML быть не может. */
    private fun esc(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\n' -> sb.append("&#10;")
                ch == '\t' -> sb.append("&#9;")
                ch == '\r' -> {}
                ch < ' ' -> {}
                ch == '\uFFFE' || ch == '\uFFFF' -> {}
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
