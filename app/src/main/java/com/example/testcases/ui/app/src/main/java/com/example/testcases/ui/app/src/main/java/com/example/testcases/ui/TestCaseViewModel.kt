package com.example.testcases.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.testcases.data.AppDatabase
import com.example.testcases.data.CsvImport
import com.example.testcases.data.ImportResult
import com.example.testcases.data.ParsedCase
import com.example.testcases.data.Section
import com.example.testcases.data.Status
import com.example.testcases.data.TestCase
import com.example.testcases.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Специальные «разделы» для экрана списка. Настоящие id всегда больше нуля. */
const val SECTION_ALL = -1L
const val SECTION_NONE = 0L

fun List<TestCase>.inSection(sectionId: Long): List<TestCase> = when (sectionId) {
    SECTION_ALL -> this
    SECTION_NONE -> filter { it.sectionId == null }
    else -> filter { it.sectionId == sectionId }
}

data class ImportPreview(val fileName: String, val result: ImportResult)

class TestCaseViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val dao = db.dao()
    private val sectionDao = db.sections()

    /** null — данные ещё загружаются. */
    val all: StateFlow<List<TestCase>?> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sections: StateFlow<List<Section>> = sectionDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------------------------------------------------------- тема

    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme", mode.name).apply()
    }

    // ---------------------------------------------------------- кейсы

    suspend fun get(id: Long): TestCase? = dao.getById(id)

    fun save(testCase: TestCase, onDone: () -> Unit) {
        viewModelScope.launch {
            if (testCase.id == 0L) {
                dao.insert(testCase)
            } else {
                dao.update(testCase.copy(updatedAt = System.currentTimeMillis()))
            }
            onDone()
        }
    }

    fun delete(testCase: TestCase) {
        viewModelScope.launch { dao.delete(testCase) }
    }

    /** Возвращает удалённый кейс на прежнее место (id сохраняется). */
    fun restore(testCase: TestCase) {
        viewModelScope.launch { dao.insert(testCase) }
    }

    /**
     * Копия кейса в том же разделе. Статус сбрасывается в «Не запущен»,
     * к названию добавляется «(копия)».
     */
    fun duplicate(testCase: TestCase, onDone: (TestCase) -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val draft = testCase.copy(
                id = 0,
                title = "${testCase.title} (копия)",
                status = Status.NOT_RUN,
                createdAt = now,
                updatedAt = now
            )
            val newId = dao.insert(draft)
            onDone(draft.copy(id = newId))
        }
    }

    fun setStatus(testCase: TestCase, status: Status) {
        viewModelScope.launch {
            dao.update(testCase.copy(status = status, updatedAt = System.currentTimeMillis()))
        }
    }

    // ---------------------------------------------------------- разделы

    fun addSection(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { sectionDao.insert(Section(name = clean)) }
    }

    fun renameSection(section: Section, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { sectionDao.update(section.copy(name = clean)) }
    }

    fun deleteSection(section: Section, withCases: Boolean) {
        viewModelScope.launch {
            db.withTransaction {
                if (withCases) {
                    dao.deleteBySection(section.id)
                } else {
                    dao.detachFromSection(section.id)
                }
                sectionDao.delete(section)
            }
        }
    }

    // ---------------------------------------------------------- импорт

    private val _importPreview = MutableStateFlow<ImportPreview?>(null)
    val importPreview: StateFlow<ImportPreview?> = _importPreview.asStateFlow()

    fun clearImport() {
        _importPreview.value = null
    }

    fun loadImport(uri: Uri) {
        viewModelScope.launch {
            val name = displayName(uri)
            val result = withContext(Dispatchers.IO) {
                try {
                    val bytes = getApplication<Application>().contentResolver
                        .openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null) {
                        ImportResult(emptyList(), emptyList(), "Не удалось открыть файл.")
                    } else {
                        CsvImport.parse(bytes)
                    }
                } catch (e: Exception) {
                    ImportResult(emptyList(), emptyList(), "Не удалось прочитать файл: ${e.message}")
                }
            }
            _importPreview.value = ImportPreview(name, result)
        }
    }

    /**
     * Сохраняет разобранные кейсы. Разделы из файла ищутся по названию
     * (без учёта регистра), недостающие создаются. Кейсы без раздела
     * попадают в [defaultSectionId] (null — «Без раздела»).
     */
    fun importCases(parsed: List<ParsedCase>, defaultSectionId: Long?, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            db.withTransaction {
                val byName = HashMap<String, Long>()
                for (s in sectionDao.getAll()) byName[s.name.trim().lowercase()] = s.id

                val cases = ArrayList<TestCase>()
                for (p in parsed) {
                    val name = p.sectionName?.trim().orEmpty()
                    val sectionId: Long? = if (name.isEmpty()) {
                        defaultSectionId
                    } else {
                        val key = name.lowercase()
                        val existing = byName[key]
                        if (existing != null) {
                            existing
                        } else {
                            val newId = sectionDao.insert(Section(name = name))
                            byName[key] = newId
                            newId
                        }
                    }
                    cases.add(
                        TestCase(
                            title = p.title,
                            description = p.description,
                            preconditions = p.preconditions,
                            steps = p.steps.joinToString(TestCase.STEP_SEP),
                            expectedResult = p.expected,
                            priority = p.priority,
                            status = p.status,
                            sectionId = sectionId
                        )
                    )
                }
                dao.insertAll(cases)
            }
            onDone(parsed.size)
        }
    }

    private fun displayName(uri: Uri): String {
        val fallback = uri.lastPathSegment.orEmpty()
        return try {
            getApplication<Application>().contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                ?: fallback
        } catch (e: Exception) {
            fallback
        }
    }
}

fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}
