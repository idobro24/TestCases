package com.example.testcases.data

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

data class ParsedCase(
    val sectionName: String?,
    val title: String,
    val description: String,
    val preconditions: String,
    val steps: List<String>,
    val expected: String,
    val priority: Priority,
    val status: Status
)

data class ImportResult(
    val cases: List<ParsedCase>,
    val warnings: List<String>,
    /** Если не null — файл прочитать не удалось, cases пуст. */
    val fatal: String? = null
)

/**
 * Разбор CSV-файла с тест-кейсами.
 * Понимает разделители «;», «,» и табуляцию, кодировки UTF-8 и Windows-1251,
 * многострочные ячейки в кавычках, русские и английские названия столбцов.
 */
object CsvImport {

    private const val MAX_BYTES = 5_000_000

    private enum class Field(val aliases: Set<String>) {
        SECTION(setOf("раздел", "section", "suite", "набор", "группа", "модуль")),
        TITLE(setOf("название", "title", "name", "заголовок", "кейс", "тест-кейс", "наименование", "summary")),
        DESCRIPTION(setOf("описание", "description")),
        PRECONDITIONS(setOf("предусловия", "предусловие", "preconditions", "precondition")),
        STEPS(setOf("шаги", "шаг", "steps", "step", "действия")),
        EXPECTED(setOf("ожидаемый результат", "ожидаемый", "expected", "expected result", "результат")),
        PRIORITY(setOf("приоритет", "priority")),
        STATUS(setOf("статус", "status"))
    }

    fun parse(bytes: ByteArray): ImportResult {
        if (bytes.isEmpty()) return fail("Файл пустой.")
        if (bytes.size > MAX_BYTES) return fail("Файл слишком большой (больше 5 МБ).")
        if (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            return fail(
                "Похоже, это файл Excel или Word, а не CSV. " +
                    "В Excel: Файл → Сохранить как → CSV UTF-8. " +
                    "В Google Таблицах: Файл → Скачать → CSV."
            )
        }

        val text = decode(bytes).replace("\r\n", "\n").replace('\r', '\n')
        val rows = tokenize(text, detectDelimiter(text))
        if (rows.isEmpty()) return fail("В файле нет данных.")

        // --- заголовок
        val columns = HashMap<Field, Int>()
        val unknown = ArrayList<String>()
        rows.first().forEachIndexed { index, raw ->
            val key = normalize(raw)
            if (key.isEmpty()) return@forEachIndexed
            val field = Field.entries.firstOrNull { key in it.aliases }
            if (field == null) {
                unknown.add(raw.trim())
            } else if (field !in columns) {
                columns[field] = index
            }
        }
        if (Field.TITLE !in columns) {
            return fail(
                "Не найден столбец «Название». Первая строка файла должна содержать " +
                    "заголовки столбцов, например: Раздел; Название; Шаги; Ожидаемый результат."
            )
        }

        // --- строки
        val cases = ArrayList<ParsedCase>()
        val warnings = ArrayList<String>()
        if (unknown.isNotEmpty()) {
            warnings.add("Эти столбцы не распознаны и пропущены: ${unknown.joinToString(", ")}.")
        }

        for (i in 1 until rows.size) {
            val row = rows[i]
            if (row.all { it.isBlank() }) continue
            val rowNo = i + 1

            fun cell(field: Field): String =
                columns[field]?.let { row.getOrNull(it) }?.trim().orEmpty()

            val title = cell(Field.TITLE)
            if (title.isEmpty()) {
                warnings.add("Строка $rowNo пропущена: нет названия.")
                continue
            }

            val priorityRaw = cell(Field.PRIORITY)
            val priority = if (priorityRaw.isEmpty()) {
                Priority.MEDIUM
            } else {
                parsePriority(priorityRaw) ?: run {
                    warnings.add("Строка $rowNo: приоритет «$priorityRaw» не распознан, поставлен «Средний».")
                    Priority.MEDIUM
                }
            }

            val statusRaw = cell(Field.STATUS)
            val status = if (statusRaw.isEmpty()) {
                Status.NOT_RUN
            } else {
                parseStatus(statusRaw) ?: run {
                    warnings.add("Строка $rowNo: статус «$statusRaw» не распознан, поставлен «Не запущен».")
                    Status.NOT_RUN
                }
            }

            cases.add(
                ParsedCase(
                    sectionName = cell(Field.SECTION).ifEmpty { null },
                    title = title,
                    description = cell(Field.DESCRIPTION),
                    preconditions = cell(Field.PRECONDITIONS),
                    steps = parseSteps(cell(Field.STEPS)),
                    expected = cell(Field.EXPECTED),
                    priority = priority,
                    status = status
                )
            )
        }

        if (cases.isEmpty()) {
            return ImportResult(emptyList(), warnings, "В файле не нашлось ни одного кейса с названием.")
        }
        return ImportResult(cases, warnings)
    }

    private fun fail(message: String) = ImportResult(emptyList(), emptyList(), message)

    // ------------------------------------------------------------ чтение текста

    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xFE) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            if (b0 == 0xFE && b1 == 0xFF) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        var start = 0
        if (bytes.size >= 3 &&
            (bytes[0].toInt() and 0xFF) == 0xEF &&
            (bytes[1].toInt() and 0xFF) == 0xBB &&
            (bytes[2].toInt() and 0xFF) == 0xBF
        ) {
            start = 3
        }
        val body = bytes.copyOfRange(start, bytes.size)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(body))
                .toString()
        } catch (e: CharacterCodingException) {
            // Excel в русской локали сохраняет обычный CSV в Windows-1251
            String(body, charset("windows-1251"))
        }
    }

    private fun detectDelimiter(text: String): Char {
        val firstLine = text.substringBefore('\n')
        val best = listOf(';', '\t', ',').maxByOrNull { c -> firstLine.count { it == c } }
        return if (best != null && firstLine.contains(best)) best else ','
    }

    private fun tokenize(text: String, delimiter: Char): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    sb.append(c)
                }
            } else {
                when {
                    c == '"' -> inQuotes = true
                    c == delimiter -> {
                        row.add(sb.toString())
                        sb.setLength(0)
                    }
                    c == '\n' -> {
                        row.add(sb.toString())
                        sb.setLength(0)
                        rows.add(row)
                        row = ArrayList()
                    }
                    else -> sb.append(c)
                }
            }
            i++
        }
        if (sb.isNotEmpty() || row.isNotEmpty()) {
            row.add(sb.toString())
            rows.add(row)
        }
        return rows
    }

    // ------------------------------------------------------------ значения

    private fun normalize(s: String): String =
        s.lowercase()
            .replace('ё', 'е')
            .replace(Regex("[\\s_]+"), " ")
            .trim()
            .trimEnd('*', ':')
            .trim()

    private val numbering = Regex("^\\s*(?:\\d+[.)]|[-•*])\\s+")

    private fun parseSteps(raw: String): List<String> =
        raw.split('\n', '|')
            .map { it.replace(numbering, "").trim() }
            .filter { it.isNotEmpty() }

    private fun parsePriority(raw: String): Priority? = when (normalize(raw)) {
        "низкий", "low", "1", "minor", "trivial" -> Priority.LOW
        "средний", "medium", "normal", "2", "major" -> Priority.MEDIUM
        "высокий", "high", "3" -> Priority.HIGH
        "критичный", "критический", "critical", "blocker", "4", "блокирующий" -> Priority.CRITICAL
        else -> null
    }

    private fun parseStatus(raw: String): Status? = when (normalize(raw)) {
        "не запущен", "не запущено", "not run", "notrun", "new", "новый", "pending", "ожидает" -> Status.NOT_RUN
        "пройден", "пройдено", "passed", "pass", "ok", "успешно" -> Status.PASSED
        "провален", "провалено", "failed", "fail", "не пройден" -> Status.FAILED
        "заблокирован", "заблокировано", "blocked" -> Status.BLOCKED
        else -> null
    }
}
