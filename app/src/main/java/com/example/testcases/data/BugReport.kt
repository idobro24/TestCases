package com.example.testcases.data

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Скриншоты багов хранятся в папке приложения: так они не пропадут, даже если исходник удалят из галереи. */
object BugFiles {

    private const val DIR = "bug_screens"
    private const val ORPHAN_AGE_MS = 10 * 60 * 1000L

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { mkdirs() }

    fun file(context: Context, name: String): File = File(dir(context), name)

    /** Копирует выбранную картинку в папку приложения. Возвращает имя файла или null, если не вышло. */
    fun import(context: Context, uri: Uri): String? {
        return try {
            val sub = context.contentResolver.getType(uri)
                ?.substringAfter('/', "")
                ?.lowercase()
                ?.filter { it.isLetterOrDigit() }
                .orEmpty()
            val ext = when {
                sub.isEmpty() || sub == "jpeg" -> "jpg"
                sub.length > 5 -> "jpg"
                else -> sub
            }
            val name = "bug_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$ext"
            val target = file(context, name)

            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { src -> target.outputStream().use { dst -> src.copyTo(dst) } }

            if (target.length() == 0L) {
                target.delete()
                null
            } else {
                name
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Уменьшенная копия для показа на экране: целиком большую картинку в память не грузим. */
    fun decode(context: Context, name: String, maxSidePx: Int): Bitmap? {
        return try {
            val f = file(context, name)
            if (!f.exists()) return null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxSidePx && bounds.outHeight / (sample * 2) >= maxSidePx) {
                sample *= 2
            }
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            null
        }
    }

    /** Копии для отправки лежат во временной папке, которую уже умеет отдавать FileProvider. */
    fun shareUris(context: Context, names: List<String>): ArrayList<Uri> {
        val out = ArrayList<Uri>()
        val shareDir = File(context.cacheDir, "exports").apply { mkdirs() }
        for (name in names) {
            val src = file(context, name)
            if (!src.exists()) continue
            val copy = File(shareDir, name)
            src.copyTo(copy, overwrite = true)
            out.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", copy))
        }
        return out
    }

    /**
     * Удаляет скриншоты, на которые не ссылается ни один кейс (остались от отменённых правок
     * или удалённых кейсов). Совсем свежие файлы не трогаем — вдруг их только что добавили.
     */
    fun cleanup(context: Context, used: Set<String>) {
        val limit = System.currentTimeMillis() - ORPHAN_AGE_MS
        dir(context).listFiles()?.forEach { f ->
            if (f.name !in used && f.lastModified() < limit) f.delete()
        }
    }
}

/** Отчёт о баге: текст для сообщения и отправка в Telegram вместе со скриншотами. */
object BugReport {

    private val telegramPackages = listOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web"
    )

    private const val MAX_TEXT = 4000

    fun build(tc: TestCase, sectionName: String?): String {
        val sb = StringBuilder()
        val head = if (tc.id > 0) "${tc.code} — ${tc.title}" else tc.title
        sb.append("🐞 Баг: ").append(head).append('\n')

        val meta = listOfNotNull(
            sectionName?.takeIf { it.isNotBlank() }?.let { "Раздел: $it" },
            "Приоритет: ${tc.priority.label}"
        )
        sb.append(meta.joinToString(" · ")).append('\n')

        fun block(title: String, body: String) {
            if (body.isBlank()) return
            sb.append('\n').append(title).append(":\n").append(body.trim()).append('\n')
        }

        block("Что не так", tc.bugDescription)
        block("Предусловия", tc.preconditions)
        block(
            "Шаги воспроизведения",
            tc.stepList.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
        )
        block("Ожидаемый результат", tc.expectedResult)

        val text = sb.toString().trimEnd()
        return if (text.length > MAX_TEXT) text.take(MAX_TEXT - 10).trimEnd() + "…" else text
    }

    /** Открывает Telegram с готовым сообщением и скриншотами; остаётся выбрать чат. */
    fun sendToTelegram(context: Context, tc: TestCase, sectionName: String?) {
        try {
            val text = build(tc, sectionName)
            val uris = BugFiles.shareUris(context, tc.bugScreenList)

            val intent = when {
                uris.isEmpty() -> Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                }
                uris.size == 1 -> Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                }
                else -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
            }
            intent.putExtra(Intent.EXTRA_TEXT, text)

            if (uris.isNotEmpty()) {
                val clip = ClipData.newRawUri("", uris[0])
                for (i in 1 until uris.size) clip.addItem(ClipData.Item(uris[i]))
                intent.clipData = clip
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            for (pkg in telegramPackages) {
                try {
                    intent.setPackage(pkg)
                    context.startActivity(intent)
                    return
                } catch (e: ActivityNotFoundException) {
                    // пробуем следующую версию Telegram
                }
            }

            // Telegram не найден — отдаём выбор приложения системе
            intent.setPackage(null)
            Toast.makeText(context, "Telegram не найден — выберите другое приложение", Toast.LENGTH_LONG).show()
            context.startActivity(Intent.createChooser(intent, "Отправить баг"))
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось отправить: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
