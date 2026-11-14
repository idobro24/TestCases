package com.example.testcases.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Результат одного кейса из файла результатов. */
class ResultEntry(
    val section: String,
    val title: String,
    val status: Status,
    val hasBug: Boolean,
    val bugDescription: String,
    val bugScreens: List<String>
)

/** Содержимое файла результатов. */
class ResultsFile(
    val project: String,
    val exportedAt: Long,
    val entries: List<ResultEntry>
)

/** Что изменится при приёме результатов. */
class ResultsPlan(
    /** Кейсы в новом виде (только те, что реально меняются). */
    val updated: List<TestCase>,
    val unchanged: Int,
    val notFound: Int,
    /** Скриншоты, которые нужно достать из файла. */
    val screens: Set<String>
)

/**
 * Обмен результатами прогона между устройствами.
 * Файл — ZIP с results.json (статус и баг каждого кейса текущего проекта) и скриншотами багов.
 * При приёме кейсы находятся по разделу и названию, меняются только статус и баг.
 */
object RunResults {

    private const val APP_ID = "testcases-results"
    private const val FORMAT = 1
    private const val DATA = "results.json"
    private const val SCREENS = "screens/"

    // ------------------------------------------------------------------ отправка

    suspend fun write(
        context: Context,
        projectName: String,
        cases: List<TestCase>,
        sectionNames: Map<Long, String>
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
        val safeName = projectName.replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').ifEmpty { "project" }
        val file = File(dir, "results_${safeName}_$stamp.zip")

        val arr = JSONArray()
        for (c in cases) {
            arr.put(JSONObject().apply {
                put("section", c.sectionId?.let { sectionNames[it] }.orEmpty())
                put("title", c.title)
                put("number", c.number)
                put("status", c.status.name)
                put("hasBug", c.hasBug)
                put("bugDescription", c.bugDescription)
                put("bugScreens", JSONArray().also { s -> c.bugScreenList.forEach { s.put(it) } })
            })
        }
        val root = JSONObject().apply {
            put("app", APP_ID)
            put("format", FORMAT)
            put("project", projectName)
            put("exportedAt", System.currentTimeMillis())
            put("cases", arr)
        }

        ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zip ->
            zip.putNextEntry(ZipEntry(DATA))
            zip.write(root.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            val used = cases.flatMap { it.bugScreenList }.toSet()
            for (name in used) {
                val src = BugFiles.file(context, name)
                if (!src.exists()) continue
                zip.putNextEntry(ZipEntry(SCREENS + name))
                src.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        file
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Отправить результаты"))
    }

    // ------------------------------------------------------------------ приём

    /** Читает файл результатов. Ничего не меняет в приложении. */
    suspend fun read(context: Context, uri: Uri): ResultsFile = withContext(Dispatchers.IO) {
        var json: String? = null
        var isBackup = false
        val zipScreens = HashSet<String>()
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw BackupException("Не удалось открыть файл.")
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    when {
                        entry.name == DATA -> json = zip.readBytes().toString(Charsets.UTF_8)
                        entry.name == "data.json" -> isBackup = true
                        entry.name.startsWith(SCREENS) -> zipScreens.add(File(entry.name).name)
                    }
                }
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("Файл не похож на файл результатов.")
        }

        val text = json ?: throw BackupException(
            if (isBackup) {
                "Это резервная копия, а не результаты. Её открывают пунктом «Восстановить из копии»."
            } else {
                "В файле нет результатов. Выберите файл, созданный пунктом «Отправить результаты»."
            }
        )
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw BackupException("Файл результатов повреждён.")
        }
        if (root.optString("app") != APP_ID) {
            throw BackupException("Это не файл результатов приложения «Тест-кейсы».")
        }
        if (root.optInt("format", 0) > FORMAT) {
            throw BackupException("Файл сделан более новой версией приложения. Обновите приложение.")
        }

        try {
            val arr = root.optJSONArray("cases") ?: JSONArray()
            val entries = (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val status = runCatching { Status.valueOf(o.optString("status")) }.getOrNull()
                    ?: return@mapNotNull null
                val screensArr = o.optJSONArray("bugScreens")
                val screens = if (screensArr == null) {
                    emptyList()
                } else {
                    // Только те скриншоты, что реально лежат в файле
                    (0 until screensArr.length()).map { screensArr.getString(it) }.filter { it in zipScreens }
                }
                ResultEntry(
                    section = o.optString("section"),
                    title = o.optString("title"),
                    status = status,
                    hasBug = o.optBoolean("hasBug", false),
                    bugDescription = o.optString("bugDescription"),
                    bugScreens = screens
                )
            }
            ResultsFile(root.optString("project"), root.optLong("exportedAt", 0L), entries)
        } catch (e: Exception) {
            throw BackupException("Файл результатов повреждён.")
        }
    }

    private fun key(section: String, title: String): String =
        section.trim().lowercase() + "\u0000" + title.trim().lowercase()

    /**
     * Сопоставляет результаты с кейсами проекта по разделу и названию.
     * Если одинаковых кейсов несколько, они сопоставляются по порядку номеров.
     */
    fun plan(file: ResultsFile, cases: List<TestCase>, sectionNames: Map<Long, String>): ResultsPlan {
        val byKey = cases
            .sortedWith(compareBy<TestCase>({ it.number }, { it.id }))
            .groupBy { key(it.sectionId?.let { id -> sectionNames[id] }.orEmpty(), it.title) }
        val used = HashMap<String, Int>()
        val now = System.currentTimeMillis()

        val updated = ArrayList<TestCase>()
        val screens = HashSet<String>()
        var unchanged = 0
        var notFound = 0

        for (e in file.entries) {
            val k = key(e.section, e.title)
            val index = used[k] ?: 0
            val target = byKey[k]?.getOrNull(index)
            if (target == null) {
                notFound++
                continue
            }
            used[k] = index + 1

            // Скриншоты не теряем: добавляем присланные к уже имеющимся
            val oldScreens = target.bugScreenList
            val newScreens = e.bugScreens.filter { it.isNotEmpty() && it !in oldScreens }
            val changed = target.status != e.status ||
                target.hasBug != e.hasBug ||
                target.bugDescription != e.bugDescription ||
                newScreens.isNotEmpty()
            if (!changed) {
                unchanged++
                continue
            }
            screens.addAll(newScreens)
            updated.add(
                target.copy(
                    status = e.status,
                    hasBug = e.hasBug,
                    bugDescription = e.bugDescription,
                    bugScreens = (oldScreens + newScreens).joinToString(TestCase.STEP_SEP),
                    updatedAt = now
                )
            )
        }
        return ResultsPlan(updated, unchanged, notFound, screens)
    }

    /** Достаёт из файла нужные скриншоты в папку приложения. */
    suspend fun extractScreens(context: Context, uri: Uri, names: Set<String>) = withContext(Dispatchers.IO) {
        if (names.isEmpty()) return@withContext
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || !entry.name.startsWith(SCREENS)) continue
                // Берём только имя файла: так архив не сможет записать ничего за пределами папки скриншотов
                val name = File(entry.name).name
                if (name.isEmpty() || name.startsWith(".") || name !in names) continue
                BugFiles.file(context, name).outputStream().use { zip.copyTo(it) }
            }
        }
    }
}
