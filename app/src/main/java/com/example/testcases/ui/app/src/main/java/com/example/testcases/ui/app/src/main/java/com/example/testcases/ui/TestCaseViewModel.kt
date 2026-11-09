package com.example.testcases.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.testcases.data.AppDatabase
import com.example.testcases.data.Backup
import com.example.testcases.data.BackupContents
import com.example.testcases.data.BugFiles
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
import java.io.File

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

    init {
        // При запуске убираем скриншоты, на которые больше не ссылается ни один кейс
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val used = dao.allBugScreens().flatMap { it.split(TestCase.STEP_SEP) }.toSet()
                BugFiles.cleanup(getApplication<Application>(), used)
            } catch (e: Exception) {
                // уборка не критична
            }
        }
    }

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

    // ---------------------------------------------------------- резервная копия

    suspend fun createBackupFile(): File {
        val allSections = sectionDao.getAll()
        val allCases = dao.getAll()
        return Backup.write(getApplication<Application>(), allSections, allCases)
    }

    suspend fun readBackup(uri: Uri): BackupContents = Backup.read(getApplication<Application>(), uri)

    /** Заменяет все данные содержимым копии: скриншоты, разделы и кейсы с прежними номерами. */
    fun restoreBackup(uri: Uri, contents: BackupContents, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = try {
                Backup.extractScreens(getApplication<Application>(), uri)
                val sectionIds = contents.sections.map { it.id }.toSet()
                val restored = contents.cases.map { c ->
                    val sid = c.sectionId
                    if (sid != null && sid !in sectionIds) c.copy(sectionId = null) else c
                }
                db.withTransaction {
                    dao.deleteAll()
                    sectionDao.deleteAll()
                    sectionDao.insertAll(contents.sections)
                    dao.insertAll(restored)
                }
                true
            } catch (e: Exception) {
                false
            }
            onDone(ok)
        }
    }

    // ---------------------------------------------------------- разделы

    fun addSection(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        // Новый раздел встаёт в конец списка
        val order = (sections.value.maxOfOrNull { it.sortOrder } ?: 0) + 1
        viewModelScope.launch { sectionDao.insert(Section(name = clean, sortOrder = order)) }
    }

    /** Сохраняет новый порядок разделов: [ids] — id разделов сверху вниз. */
    fun reorderSections(ids: List<Long>) {
        viewModelScope.launch {
            db.withTransaction {
                ids.forEachIndexed { i, id -> sectionDao.setSortOrder(id, i + 1) }
            }
        }
    }

    fun renameSection(section: Section, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { sectionDao.update(section.copy(name = clean)) }
    }

    /** Удаляет сразу несколько разделов; кейсы либо удаляются вместе с ними, либо остаются без раздела. */
    fun deleteSections(ids: Set<Long>, withCases: Boolean) {
        val targets = sections.value.filter { it.id in ids }
        viewModelScope.launch {
            db.withTransaction {
                for (s in targets) {
                    if (withCases) {
                        dao.deleteBySection(s.id)
                    } else {
                        dao.detachFromSection(s.id)
                    }
                    sectionDao.delete(s)
                }
            }
        }
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
                val existingSections = sectionDao.getAll()
                for (s in existingSections) byName[s.name.trim().lowercase()] = s.id
                var nextOrder = (existingSections.maxOfOrNull { it.sortOrder } ?: 0) + 1

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
                            val newId = sectionDao.insert(Section(name = name, sortOrder = nextOrder++))
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
                            hasBug = p.hasBug,
                            bugDescription = p.bugDescription,
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
