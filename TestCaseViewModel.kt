package com.example.testcases.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.testcases.data.AppDatabase
import com.example.testcases.data.Status
import com.example.testcases.data.TestCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TestCaseViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).dao()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow<Status?>(null)
    val filter: StateFlow<Status?> = _filter.asStateFlow()

    /** null — данные ещё загружаются. */
    val all: StateFlow<List<TestCase>?> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val visible: StateFlow<List<TestCase>> = combine(all, _query, _filter) { list, q, status ->
        list.orEmpty().filter { tc ->
            (status == null || tc.status == status) &&
                (q.isBlank() ||
                    tc.title.contains(q, ignoreCase = true) ||
                    tc.code.contains(q, ignoreCase = true) ||
                    tc.description.contains(q, ignoreCase = true))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) { _query.value = value }
    fun setFilter(value: Status?) { _filter.value = value }

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

    fun setStatus(testCase: TestCase, status: Status) {
        viewModelScope.launch {
            dao.update(testCase.copy(status = status, updatedAt = System.currentTimeMillis()))
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
