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
import com.example.testcases.data.Project
import com.example.testcases.data.Section
import com.example.testcases.data.Status
import com.example.testcases.data.TestCase
import com.example.testcases.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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
    private val projectDao = db.projects()
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

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

    // ---------------------------------------------------------- проекты

    private val _currentProjectId = MutableStateFlow(prefs.getLong("project", 1L))

    /** Проект, с которым работает приложение сейчас. */
    val currentProjectId: StateFlow<Long> = _currentProjectId.asStateFlow()

    val projects: StateFlow<List<Project>> = projectDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Кейсы текущего проекта. null — данные ещё загружаются. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val all: StateFlow<List<TestCase>?> = _currentProjectId
        .flatMapLatest { pid ->
            dao.observeByProject(pid)
                .map<List<TestCase>, List<TestCase>?> { it }
                .onStart { emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Разделы текущего проекта. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val sections: StateFlow<List<Section>> = _currentProjectId
        .flatMapLatest { pid -> sectionDao.observeByProject(pid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Всегда должен быть хотя бы один проект, а выбранный — существовать
        viewModelScope.launch {
            if (projectDao.getAll().isEmpty()) projectDao.insert(Project(id = 1, name = "Проект 1"))
            projects.collect { list ->
                if (list.isNotEmpty() && list.none { it.id == _currentProjectId.value }) {
                    selectProject(list.first().id)
                }
            }
        }
    }

    fun selectProject(id: Long) {
        _currentProjectId.value = id
        prefs.edit().putLong("project", id).apply()
    }

    fun addProject(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val id = projectDao.insert(Project(name = clean))
            selectProject(id)
        }
    }

    fun renameProject(project: Project, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { projectDao.update(project.copy(name = clean)) }
    }

    /** Удаляет проект со всеми его разделами и кейсами. Последний проект удалить нельзя. */
    fun deleteProject(project: Project) {
        viewModelScope.launch {
            val list = projectDao.getAll()
            if (list.size <= 1) return@launch
            db.withTransaction {
                dao.deleteByProject(project.id)
                sectionDao.deleteByProject(project.id)
                projectDao.delete(project)
            }
            if (_currentProjectId.value == project.id) {
                selectProject(list.first { it.id != project.id }.id)
            }
        }
    }

    // ---------------------------------------------------------- тема

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
                val pid = _currentProjectId.value
                db.withTransaction {
                    // Новый кейс попадает в текущий проект и получает следующий номер после последнего
                    dao.insert(testCase.copy(projectId = pid, number = dao.maxNumber(pid) + 1))
                }
            } else {
                dao.update(testCase.copy(updatedAt = System.currentTimeMillis()))
            }
            onDone()
        }
    }

    fun delete(testCase: TestCase) {
        viewModelScope.launch {
            db.withTransaction {
                dao.delete(testCase)
                renumber(testCase.projectId)
            }
        }
    }

    /** Возвращает удалённый кейс на прежнее место: с тем же номером, остальные сдвигаются обратно. */
    fun restore(testCase: TestCase) {
        viewModelScope.launch {
            db.withTransaction {
                if (testCase.number > 0) dao.shiftFrom(testCase.projectId, testCase.number)
                dao.insert(testCase)
            }
        }
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
            val saved = db.withTransaction {
                val numbered = draft.copy(number = dao.maxNumber(draft.projectId) + 1)
                numbered.copy(id = dao.insert(numbered))
            }
            onDone(saved)
        }
    }

    // ---------------------------------------------------------- действия над несколькими кейсами

    fun setStatuses(ids: Collection<Long>, status: Status) {
        viewModelScope.launch { db.withTransaction { setStatusInChunks(ids, status) } }
    }

    fun setBugFlag(ids: Collection<Long>, hasBug: Boolean) {
        viewModelScope.launch { db.withTransaction { setBugInChunks(ids, hasBug) } }
    }

    fun restoreBugFlags(previous: Map<Long, Boolean>) {
        viewModelScope.launch {
            db.withTransaction {
                previous.entries
                    .groupBy({ it.value }, { it.key })
                    .forEach { (flag, ids) -> setBugInChunks(ids, flag) }
            }
        }
    }

    /** Переносит кейсы в раздел [sectionId] (null — «Без раздела»). */
    fun moveCases(ids: Collection<Long>, sectionId: Long?) {
        viewModelScope.launch { db.withTransaction { setSectionInChunks(ids, sectionId) } }
    }

    fun restoreSections(previous: Map<Long, Long?>) {
        viewModelScope.launch {
            db.withTransaction {
                previous.entries
                    .groupBy({ it.value }, { it.key })
                    .forEach { (sectionId, ids) -> setSectionInChunks(ids, sectionId) }
            }
        }
    }

    fun deleteCases(cases: List<TestCase>) {
        viewModelScope.launch {
            db.withTransaction {
                cases.forEach { dao.delete(it) }
                cases.map { it.projectId }.toSet().forEach { renumber(it) }
            }
        }
    }

    /** Отмена удаления нескольких кейсов: возвращаем по порядку номеров, чтобы каждый встал на своё место. */
    fun restoreCases(cases: List<TestCase>) {
        viewModelScope.launch {
            db.withTransaction {
                for (c in cases.sortedBy { it.number }) {
                    if (c.number > 0) dao.shiftFrom(c.projectId, c.number)
                    dao.insert(c)
                }
            }
        }
    }

    private suspend fun setBugInChunks(ids: Collection<Long>, hasBug: Boolean) {
        val now = System.currentTimeMillis()
        ids.chunked(500).forEach { dao.setBug(it, hasBug, now) }
    }

    private suspend fun setSectionInChunks(ids: Collection<Long>, sectionId: Long?) {
        val now = System.currentTimeMillis()
        ids.chunked(500).forEach { dao.setSection(it, sectionId, now) }
    }

    /** Возвращает кейсам статус «Не запущен» — для нового прогона. */
    fun resetStatuses(ids: Collection<Long>) {
        viewModelScope.launch {
            db.withTransaction { setStatusInChunks(ids, Status.NOT_RUN) }
        }
    }

    /** Отмена сброса: каждому кейсу возвращается прежний статус. */
    fun restoreStatuses(previous: Map<Long, Status>) {
        viewModelScope.launch {
            db.withTransaction {
                previous.entries
                    .groupBy({ it.value }, { it.key })
                    .forEach { (status, ids) -> setStatusInChunks(ids, status) }
            }
        }
    }

    /** Смыкает номера кейсов без пропусков: 1, 2, 3… в прежнем порядке. */
    private suspend fun renumber(projectId: Long) {
        dao.getByProject(projectId).forEachIndexed { index, c ->
            if (c.number != index + 1) dao.setNumber(c.id, index + 1)
        }
    }

    // В SQLite ограничено число параметров в одном запросе, поэтому идём порциями
    private suspend fun setStatusInChunks(ids: Collection<Long>, status: Status) {
        val now = System.currentTimeMillis()
        ids.chunked(500).forEach { dao.setStatus(it, status.name, now) }
    }

    fun setStatus(testCase: TestCase, status: Status) {
        viewModelScope.launch {
            dao.update(testCase.copy(status = status, updatedAt = System.currentTimeMillis()))
        }
    }

    // ---------------------------------------------------------- резервная копия

    suspend fun createBackupFile(): File =
        Backup.write(getApplication<Application>(), projectDao.getAll(), sectionDao.getAll(), dao.getAll())

    /** Сколько сейчас проектов, разделов и кейсов во всей базе (проекты, разделы, кейсы). */
    suspend fun totalCounts(): Triple<Int, Int, Int> =
        Triple(projectDao.getAll().size, sectionDao.getAll().size, dao.getAll().size)

    suspend fun readBackup(uri: Uri): BackupContents = Backup.read(getApplication<Application>(), uri)

    /** Заменяет все данные (все проекты) содержимым копии: скриншоты, проекты, разделы и кейсы с прежними номерами. */
    fun restoreBackup(uri: Uri, contents: BackupContents, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = try {
                Backup.extractScreens(getApplication<Application>(), uri)

                val projectIds = contents.projects.map { it.id }.toSet()
                val fallback = contents.projects.first().id
                val restoredSections = contents.sections.map { sec ->
                    if (sec.projectId in projectIds) sec else sec.copy(projectId = fallback)
                }
                val sectionProject = restoredSections.associate { it.id to it.projectId }
                val restored = contents.cases.map { c ->
                    val pid = if (c.projectId in projectIds) c.projectId else fallback
                    val sid = c.sectionId
                    // Раздел должен существовать и относиться к тому же проекту, иначе кейс остаётся без раздела
                    val sectionOk = sid != null && sectionProject[sid] == pid
                    c.copy(projectId = pid, sectionId = if (sectionOk) sid else null)
                }
                // Номера нужны подряд в каждом проекте; в старых копиях их нет, тогда нумеруем по порядку создания
                val numbered = restored.groupBy { it.projectId }.flatMap { (_, list) ->
                    if (list.any { it.number <= 0 } || list.map { it.number }.toSet().size != list.size) {
                        list.sortedBy { it.id }.mapIndexed { i, c -> c.copy(number = i + 1) }
                    } else {
                        list
                    }
                }
                db.withTransaction {
                    dao.deleteAll()
                    sectionDao.deleteAll()
                    projectDao.deleteAll()
                    projectDao.insertAll(contents.projects)
                    sectionDao.insertAll(restoredSections)
                    dao.insertAll(numbered)
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
        val pid = _currentProjectId.value
        viewModelScope.launch { sectionDao.insert(Section(name = clean, sortOrder = order, projectId = pid)) }
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
                if (withCases) renumber(_currentProjectId.value)
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
                if (withCases) renumber(_currentProjectId.value)
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
            val pid = _currentProjectId.value
            db.withTransaction {
                val byName = HashMap<String, Long>()
                val existingSections = sectionDao.getByProject(pid)
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
                            val newId = sectionDao.insert(Section(name = name, sortOrder = nextOrder++, projectId = pid))
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
                            sectionId = sectionId,
                            projectId = pid
                        )
                    )
                }
                val start = dao.maxNumber(pid) + 1
                dao.insertAll(cases.mapIndexed { i, c -> c.copy(number = start + i) })
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
