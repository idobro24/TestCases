package com.example.testcases.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale

enum class Status(val label: String) {
    NOT_RUN("Не запущен"),
    PASSED("Пройден"),
    FAILED("Провален"),
    BLOCKED("Заблокирован")
}

enum class Priority(val label: String) {
    LOW("Низкий"),
    MEDIUM("Средний"),
    HIGH("Высокий"),
    CRITICAL("Критичный")
}

@Entity(tableName = "sections")
data class Section(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Порядок на главном экране: меньше — выше. */
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "test_cases")
data class TestCase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val preconditions: String = "",
    /** Шаги хранятся одной строкой, разделитель — [STEP_SEP]. */
    val steps: String = "",
    val expectedResult: String = "",
    val priority: Priority = Priority.MEDIUM,
    val status: Status = Status.NOT_RUN,
    /** null — кейс без раздела. */
    val sectionId: Long? = null,
    /** Отмечен ли в кейсе баг. */
    val hasBug: Boolean = false,
    /** Что именно не так (фактический результат). */
    val bugDescription: String = "",
    /** Имена файлов скриншотов в папке приложения, разделитель — [STEP_SEP]. */
    val bugScreens: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Порядковый номер для показа (TC-007). При удалении кейсов номера смыкаются без пропусков. */
    val number: Int = 0
) {
    val code: String get() = String.format(Locale.ROOT, "TC-%03d", if (number > 0) number.toLong() else id)

    val stepList: List<String>
        get() = if (steps.isEmpty()) emptyList() else steps.split(STEP_SEP)

    val bugScreenList: List<String>
        get() = if (bugScreens.isEmpty()) emptyList() else bugScreens.split(STEP_SEP)

    companion object {
        const val STEP_SEP = "\u001F"
    }
}
