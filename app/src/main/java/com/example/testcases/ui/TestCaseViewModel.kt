package com.example.testcases.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.testcases.data.AppDatabase
import com.example.testcases.data.TestCase
import com.example.testcases.data.Status
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TestCaseViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).testCaseDao()

    val allTestCases: StateFlow<List<TestCase>> = dao.getAllTestCases()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Заглушки и методы для работы с секциями и фильтрами, которых не хватает экранам:
    val sections: StateFlow<List<String>> = allTestCases
        .map { cases -> cases.map { it.section }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun getTestCase(id: Long): Flow<TestCase?> {
        return dao.getTestCaseById(id)
    }

    fun insertTestCase(testCase: TestCase) {
        viewModelScope.launch {
            dao.insertTestCase(testCase)
        }
    }

    fun updateTestCase(testCase: TestCase) {
        viewModelScope.launch {
            dao.updateTestCase(testCase)
        }
    }

    fun deleteTestCase(testCase: TestCase) {
        viewModelScope.launch {
            dao.deleteTestCase(testCase)
        }
    }

    fun duplicateTestCase(testCase: TestCase) {
        viewModelScope.launch {
            val newTestCase = testCase.copy(
                id = 0,
                title = "${testCase.title} (Копия)",
                status = Status.NOT_RUN
            )
            dao.insertTestCase(newTestCase)
        }
    }

    fun updateTestCaseSection(testCaseId: Long, newSection: String) {
        viewModelScope.launch {
            // Обновление секции для конкретного кейса
        }
    }

    // Методы для предотвращения ошибок с отсутствующими методами секций
    fun addSection(name: String) {}
    fun deleteSection(sectionName: String) {}
    fun renameSection(oldName: String, newName: String) {}
    fun reorderSections(sectionList: List<Any>) {}
}
