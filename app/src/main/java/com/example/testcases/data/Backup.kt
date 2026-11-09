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

/** Понятная пользователю ошибка резервной копии. */
class BackupException(message: String) : Exception(message)

/** Что лежит в копии (скриншоты считаются, но не загружаются в память). */
class BackupContents(
    val projects: List<Project>,
    val sections: List<Section>,
    val cases: List<TestCase>,
    val screenCount: Int
)

/**
 * Полная резервная копия одним файлом: ZIP с данными (data.json) и скриншотами багов.
 * Сохраняет разделы с их порядком, кейсы со всеми полями (включая баги) и сами скриншоты.
 */
object Backup {

    private const val APP_ID = "testcases"
    private const val FORMAT = 2
    private const val DATA = "data.json"
    private const val SCREENS = "screens/"

    // ------------------------------------------------------------------ запись

    suspend fun write(
        context: Context,
        projects: List<Project>,
        sections: List<Section>,
        cases: List<TestCase>
    ): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
            val file = File(dir, "testcases-backup-$stamp.zip")

            val root = JSONObject().apply {
                put("app", APP_ID)
                put("format", FORMAT)
                put("exportedAt", System.currentTimeMillis())
                put("projects", JSONArray().also { arr -> projects.forEach { arr.put(projectToJson(it)) } })
                put("sections", JSONArray().also { arr -> sections.forEach { arr.put(sectionToJson(it)) } })
                put("cases", JSONArray().also { arr -> cases.forEach { arr.put(caseToJson(it)) } })
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

    /** Открывает системное окно «Поделиться»: оттуда копию можно отправить в Telegram, на Диск и т. д. */
    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Сохранить копию"))
    }

    // ------------------------------------------------------------------ чтение

    /** Читает копию и проверяет её. Ничего не меняет в приложении. */
    suspend fun read(context: Context, uri: Uri): BackupContents = withContext(Dispatchers.IO) {
        var json: String? = null
        var screens = 0
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw BackupException("Не удалось открыть файл.")
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    if (entry.name == DATA) {
                        json = zip.readBytes().toString(Charsets.UTF_8)
                    } else if (entry.name.startsWith(SCREENS)) {
                        screens++
                    }
                }
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("Файл не похож на резервную копию.")
        }

        val text = json ?: throw BackupException(
            "В файле нет данных приложения. Выберите файл, сохранённый кнопкой «Сохранить копию»."
        )
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw BackupException("Файл копии повреждён.")
        }
        if (root.optString("app") != APP_ID) {
            throw BackupException("Это не резервная копия приложения «Тест-кейсы».")
        }
        if (root.optInt("format", 0) > FORMAT) {
            throw BackupException("Копия сделана более новой версией приложения. Обновите приложение.")
        }

        try {
            // Копии старого формата без проектов целиком попадают в «Проект 1»
            val projects = objects(root.optJSONArray("projects")).map { projectFromJson(it) }
                .ifEmpty { listOf(Project(id = 1, name = "Проект 1")) }
            val sections = objects(root.optJSONArray("sections")).map { sectionFromJson(it) }
            val cases = objects(root.optJSONArray("cases")).map { caseFromJson(it) }
            BackupContents(projects, sections, cases, screens)
        } catch (e: Exception) {
            throw BackupException("Файл копии повреждён.")
        }
    }

    /** Кладёт скриншоты из копии в папку приложения. Возвращает, сколько файлов восстановлено. */
    suspend fun extractScreens(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        var count = 0
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext 0
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || !entry.name.startsWith(SCREENS)) continue
                // Берём только имя файла: так архив не сможет записать ничего за пределами папки скриншотов
                val name = File(entry.name).name
                if (name.isEmpty() || name.startsWith(".")) continue
                BugFiles.file(context, name).outputStream().use { zip.copyTo(it) }
                count++
            }
        }
        count
    }

    // ------------------------------------------------------------------ JSON

    private fun objects(arr: JSONArray?): List<JSONObject> =
        if (arr == null) emptyList() else (0 until arr.length()).map { arr.getJSONObject(it) }

    private fun strings(arr: JSONArray?): List<String> =
        if (arr == null) emptyList() else (0 until arr.length()).map { arr.getString(it) }

    private fun projectToJson(p: Project) = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("createdAt", p.createdAt)
    }

    private fun projectFromJson(o: JSONObject) = Project(
        id = o.getLong("id"),
        name = o.optString("name", "Проект"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis())
    )

    private fun sectionToJson(s: Section) = JSONObject().apply {
        put("id", s.id)
        put("name", s.name)
        put("sortOrder", s.sortOrder)
        put("projectId", s.projectId)
        put("createdAt", s.createdAt)
    }

    private fun sectionFromJson(o: JSONObject) = Section(
        id = o.getLong("id"),
        name = o.optString("name", ""),
        sortOrder = o.optInt("sortOrder", 0),
        projectId = o.optLong("projectId", 1),
        createdAt = o.optLong("createdAt", System.currentTimeMillis())
    )

    private fun caseToJson(c: TestCase) = JSONObject().apply {
        put("id", c.id)
        put("title", c.title)
        put("description", c.description)
        put("preconditions", c.preconditions)
        put("steps", JSONArray(c.stepList))
        put("expectedResult", c.expectedResult)
        put("priority", c.priority.name)
        put("status", c.status.name)
        put("sectionId", c.sectionId ?: JSONObject.NULL)
        put("hasBug", c.hasBug)
        put("bugDescription", c.bugDescription)
        put("bugScreens", JSONArray(c.bugScreenList))
        put("createdAt", c.createdAt)
        put("updatedAt", c.updatedAt)
        put("number", c.number)
        put("projectId", c.projectId)
    }

    private fun caseFromJson(o: JSONObject): TestCase {
        val now = System.currentTimeMillis()
        return TestCase(
            id = o.getLong("id"),
            title = o.optString("title", ""),
            description = o.optString("description", ""),
            preconditions = o.optString("preconditions", ""),
            steps = strings(o.optJSONArray("steps")).joinToString(TestCase.STEP_SEP),
            expectedResult = o.optString("expectedResult", ""),
            priority = runCatching { Priority.valueOf(o.optString("priority")) }.getOrDefault(Priority.MEDIUM),
            status = runCatching { Status.valueOf(o.optString("status")) }.getOrDefault(Status.NOT_RUN),
            sectionId = if (o.isNull("sectionId")) null else o.getLong("sectionId"),
            hasBug = o.optBoolean("hasBug", false),
            bugDescription = o.optString("bugDescription", ""),
            bugScreens = strings(o.optJSONArray("bugScreens")).joinToString(TestCase.STEP_SEP),
            createdAt = o.optLong("createdAt", now),
            updatedAt = o.optLong("updatedAt", now),
            number = o.optInt("number", 0),
            projectId = o.optLong("projectId", 1)
        )
    }
}
