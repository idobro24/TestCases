package com.example.testcases.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.testcases.data.Priority
import com.example.testcases.data.Status
import com.example.testcases.data.TestCase
import com.example.testcases.ui.theme.color

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    vm: TestCaseViewModel,
    id: Long,
    initialSectionId: Long?,
    onClose: () -> Unit
) {
    val isNew = id == 0L
    val context = LocalContext.current
    val sections by vm.sections.collectAsStateWithLifecycle()

    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var original by remember { mutableStateOf<TestCase?>(null) }

    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var preconditions by rememberSaveable { mutableStateOf("") }
    var steps by rememberSaveable { mutableStateOf(listOf("")) }
    var expected by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf(Priority.MEDIUM) }
    var status by rememberSaveable { mutableStateOf(Status.NOT_RUN) }
    var sectionId by rememberSaveable { mutableStateOf(initialSectionId) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (isNew) return@LaunchedEffect
        val tc = vm.get(id)
        if (tc == null) {
            onClose()
            return@LaunchedEffect
        }
        original = tc
        if (!loaded) {
            title = tc.title
            description = tc.description
            preconditions = tc.preconditions
            steps = tc.stepList.ifEmpty { listOf("") }
            expected = tc.expectedResult
            priority = tc.priority
            status = tc.status
            sectionId = tc.sectionId
            loaded = true
        }
    }

    fun buildCase(): TestCase? {
        if (title.isBlank()) {
            showErrors = true
            return null
        }
        if (!isNew && original == null) return null
        val base = original ?: TestCase(title = "")
        return base.copy(
            title = title.trim(),
            description = description.trim(),
            preconditions = preconditions.trim(),
            steps = steps.map { it.trim() }.filter { it.isNotEmpty() }
                .joinToString(TestCase.STEP_SEP),
            expectedResult = expected.trim(),
            priority = priority,
            status = status,
            sectionId = sectionId
        )
    }

    fun save() {
        val tc = buildCase() ?: return
        vm.save(tc, onDone = onClose)
    }

    /** Копия того, что сейчас в форме (даже если ещё не сохранено). */
    fun copyCase() {
        val tc = buildCase() ?: return
        vm.duplicate(tc) { copy ->
            Toast.makeText(context, "Создана копия ${copy.code}", Toast.LENGTH_SHORT).show()
            onClose()
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Новый тест-кейс" else original?.code.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = ::copyCase) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Копировать кейс")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Удалить кейс")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(
                    Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = ::save,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (isNew) "Создать кейс" else "Сохранить изменения")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            AppField(
                value = title,
                onChange = { title = it },
                label = "Название",
                isError = showErrors && title.isBlank(),
                supporting = if (showErrors && title.isBlank()) "Введите название кейса" else null
            )

            FormSection("Раздел") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = sectionId == null,
                        onClick = { sectionId = null },
                        label = { Text("Без раздела") }
                    )
                    sections.forEach { s ->
                        FilterChip(
                            selected = sectionId == s.id,
                            onClick = { sectionId = s.id },
                            label = { Text(s.name) }
                        )
                    }
                }
            }

            FormSection("Приоритет") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Priority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.label) }
                        )
                    }
                }
            }

            FormSection("Статус") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Status.entries.forEach { s ->
                        val c = s.color()
                        val selected = status == s
                        FilterChip(
                            selected = selected,
                            onClick = { status = s },
                            label = { Text(s.label) },
                            leadingIcon = {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = c.copy(alpha = 0.16f),
                                selectedLabelColor = c
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                selectedBorderColor = c
                            )
                        )
                    }
                }
            }

            AppField(description, { description = it }, "Описание", minLines = 2)
            AppField(preconditions, { preconditions = it }, "Предусловия", minLines = 2)

            FormSection("Шаги") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    steps.forEachIndexed { index, text ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 14.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(Modifier.size(10.dp))
                            AppField(
                                value = text,
                                onChange = { new ->
                                    steps = steps.toMutableList().also { it[index] = new }
                                },
                                label = "Действие",
                                modifier = Modifier.weight(1f)
                            )
                            if (steps.size > 1) {
                                IconButton(
                                    onClick = {
                                        steps = steps.toMutableList().also { it.removeAt(index) }
                                    },
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Убрать шаг")
                                }
                            }
                        }
                    }
                    TextButton(onClick = { steps = steps + "" }) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Добавить шаг")
                    }
                }
            }

            AppField(expected, { expected = it }, "Ожидаемый результат", minLines = 3)

            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить тест-кейс?") },
            text = { Text("«${original?.title.orEmpty()}» будет удалён без возможности восстановления.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        original?.let { vm.delete(it) }
                        onClose()
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@Composable
private fun AppField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    isError: Boolean = false,
    supporting: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = minLines,
        isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}
