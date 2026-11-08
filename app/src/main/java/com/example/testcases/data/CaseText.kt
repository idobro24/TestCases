package com.example.testcases.data

import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Один кейс обычным текстом — удобно отправить в чат или вставить в багтрекер. */
object CaseText {

    fun build(tc: TestCase, sectionName: String?): String {
        val sb = StringBuilder()
        sb.append(tc.code).append(" — ").append(tc.title).append('\n')

        val meta = listOfNotNull(
            sectionName?.takeIf { it.isNotBlank() }?.let { "Раздел: $it" },
            "Приоритет: ${tc.priority.label}",
            "Статус: ${tc.status.label}"
        )
        sb.append(meta.joinToString(" · ")).append('\n')

        fun block(title: String, body: String) {
            if (body.isBlank()) return
            sb.append('\n').append(title).append(":\n").append(body.trim()).append('\n')
        }

        block("Описание", tc.description)
        block("Предусловия", tc.preconditions)
        block(
            "Шаги",
            tc.stepList.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
        )
        block("Ожидаемый результат", tc.expectedResult)
        return sb.toString().trimEnd()
    }

    /** Открывает системное окно «Поделиться» с текстом кейса. */
    fun share(context: Context, tc: TestCase, sectionName: String?) {
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "${tc.code} — ${tc.title}")
                putExtra(Intent.EXTRA_TEXT, build(tc, sectionName))
            }
            context.startActivity(Intent.createChooser(send, "Поделиться кейсом"))
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось поделиться: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
