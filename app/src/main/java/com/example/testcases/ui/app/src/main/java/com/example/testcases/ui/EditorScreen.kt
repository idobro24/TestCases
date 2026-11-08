package com.example.testcases.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.testcases.data.BugReport
import com.example.testcases.data.CaseText
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
    var steps by rememberSaveable(stateSaver = StepsSaver) {
        mutableStateOf(listOf(StepItem(1, "")))
    }
    var expected by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf(Priority.MEDIUM) }
    var status by rememberSaveable { mutableStateOf(Status.NOT_RUN) }
    var sectionId by rememberSaveable { mutableStateOf(initialSectionId) }
    var hasBug by rememberSaveable { mutableStateOf(false) }
    var bugDescription by rememberSaveable { mutableStateOf("") }
    var bugScreens by rememberSaveable { mutableStateOf(listOf<String>()) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }

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
                .mapIndexed { i, text -> StepItem(i + 1, text) }
            expected = tc.expectedResult
            priority = tc.priority
            status = tc.status
            sectionId = tc.sectionId
            hasBug = tc.hasBug
            bugDescription = tc.bugDescription
            bugScreens = tc.bugScreenList
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
            steps = steps.map { it.text.trim() }.filter { it.isNotEmpty() }
                .joinToString(TestCase.STEP_SEP),
            expectedResult = expected.trim(),
            priority = priority,
            status = status,
            sectionId = sectionId,
            hasBug = hasBug,
            bugDescription = bugDescription.trim(),
            bugScreens = bugScreens.joinToString(TestCase.STEP_SEP)
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

    // Что сейчас в форме и с чем это сравнивать, чтобы понять, есть ли несохранённые правки
    val current = FormSnapshot(
        title = title.trim(),
        description = description.trim(),
        preconditions = preconditions.trim(),
        steps = steps.map { it.text.trim() }.filter { it.isNotEmpty() },
        expected = expected.trim(),
        priority = priority,
        status = status,
        sectionId = sectionId,
        hasBug = hasBug,
        bugDescription = bugDescription.trim(),
        bugScreens = bugScreens
    )
    val baseline: FormSnapshot? = if (isNew) {
        FormSnapshot(
            title = "", description = "", preconditions = "", steps = emptyList(), expected = "",
            priority = Priority.MEDIUM, status = Status.NOT_RUN, sectionId = initialSectionId,
            hasBug = false, bugDescription = "", bugScreens = emptyList()
        )
    } else {
        original?.toSnapshot()
    }
    val dirty = loaded && baseline != null && current != baseline

    fun requestClose() {
        if (dirty) showDiscard = true else onClose()
    }

    BackHandler(enabled = dirty) { showDiscard = true }

    /** Отправляет отчёт о баге в Telegram: текст и скриншоты из формы. */
    fun sendBug() {
        val tc = buildCase() ?: return
        BugReport.sendToTelegram(context, tc, sections.firstOrNull { it.id == tc.sectionId }?.name)
    }

    /** Отправляет кейс текстом — таким, каким он сейчас в форме. */
    fun shareCase() {
        val tc = buildCase() ?: return
        CaseText.share(context, tc, sections.firstOrNull { it.id == tc.sectionId }?.name)
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Новый тест-кейс" else original?.code.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = ::requestClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = ::shareCase) {
                            Icon(Icons.Rounded.Share, contentDescription = "Поделиться кейсом текстом")
                        }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppField(
                value = title,
                onChange = { title = it },
                label = "Название",
                isError = showErrors && title.isBlank(),
                supporting = if (showErrors && title.isBlank()) "Введите название кейса" else null
            )

            DropdownField(
                label = "Раздел",
                value = sectionId,
                options = listOf<Long?>(null) + sections.map { it.id },
                optionText = { id -> sections.firstOrNull { it.id == id }?.name ?: "Без раздела" },
                onSelect = { sectionId = it }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownField(
                    label = "Приоритет",
                    value = priority,
                    options = Priority.entries.toList(),
                    optionText = { it.label },
                    onSelect = { priority = it },
                    modifier = Modifier.weight(1f)
                )
                DropdownField(
                    label = "Статус",
                    value = status,
                    options = Status.entries.toList(),
                    optionText = { it.label },
                    onSelect = { status = it },
                    modifier = Modifier.weight(1f),
                    leading = { st -> Box(Modifier.size(10.dp).clip(CircleShape).background(st.color())) }
                )
            }

            FormSection("Шаги") {
                StepsEditor(steps = steps, onChange = { steps = it })
            }

            AppField(expected, { expected = it }, "Ожидаемый результат", minLines = 2)
            AppField(preconditions, { preconditions = it }, "Предусловия", minLines = 2)
            AppField(description, { description = it }, "Описание", minLines = 2)

            BugSection(
                hasBug = hasBug,
                onHasBugChange = { hasBug = it },
                description = bugDescription,
                onDescriptionChange = { bugDescription = it },
                screens = bugScreens,
                onScreensChange = { bugScreens = it },
                onSend = ::sendBug
            )

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Сохранить изменения?") },
            text = { Text("В кейсе есть несохранённые правки. Если выйти сейчас, они пропадут.") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        showDiscard = false
                        save()
                    }) { Text("Сохранить") }
                    TextButton(onClick = {
                        showDiscard = false
                        onClose()
                    }) {
                        Text("Выйти без сохранения", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showDiscard = false }) { Text("Продолжить редактирование") }
                }
            }
        )
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
internal fun AppField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    isError: Boolean = false,
    supporting: String? = null,
    onClear: (() -> Unit)? = null
) {
    val clearButton: (@Composable () -> Unit)? =
        if (onClear != null && value.isNotEmpty()) {
            {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Close, contentDescription = "Очистить поле")
                }
            }
        } else {
            null
        }

    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = minLines,
        isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        trailingIcon = clearButton,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

// ---------------------------------------------------------------- шаги

/** Шаг с постоянным id: благодаря ему поле «едет» вместе с текстом при перетаскивании. */
private data class StepItem(val id: Int, val text: String)

private val StepsSaver = listSaver<List<StepItem>, Any>(
    save = { list -> list.flatMap { listOf(it.id, it.text) } },
    restore = { saved -> saved.chunked(2).map { StepItem(it[0] as Int, it[1] as String) } }
)

private class StepsHolder(var list: List<StepItem>, var onChange: (List<StepItem>) -> Unit)

private val StepSpacing = 10.dp

/**
 * Список шагов: ручка слева перетаскивает шаг вверх/вниз, крестик в поле очищает текст,
 * корзина справа удаляет шаг целиком.
 */
@Composable
private fun StepsEditor(
    steps: List<StepItem>,
    onChange: (List<StepItem>) -> Unit
) {
    // Свежие данные для жеста: он живёт дольше одной перекомпозиции
    val holder = remember { StepsHolder(steps, onChange) }
    holder.list = steps
    holder.onChange = onChange

    val haptics = LocalHapticFeedback.current
    val gapPx = with(LocalDensity.current) { StepSpacing.toPx() }
    val heights = remember { mutableStateMapOf<Int, Int>() }
    var draggingId by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    fun swap(from: Int, to: Int) {
        val updated = holder.list.toMutableList()
        val moved = updated.removeAt(from)
        updated.add(to, moved)
        holder.list = updated
        holder.onChange(updated)
    }

    /** Двигает перетаскиваемый шаг; когда он пересёк середину соседа — меняет их местами. */
    fun dragBy(id: Int, delta: Float) {
        dragOffset += delta
        val list = holder.list
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        if (dragOffset > 0 && index < list.lastIndex) {
            val gap = (heights[list[index + 1].id] ?: 0) + gapPx
            if (dragOffset > gap / 2) {
                swap(index, index + 1)
                dragOffset -= gap
            }
        } else if (dragOffset < 0 && index > 0) {
            val gap = (heights[list[index - 1].id] ?: 0) + gapPx
            if (-dragOffset > gap / 2) {
                swap(index, index - 1)
                dragOffset += gap
            }
        }
    }

    fun stopDrag() {
        draggingId = null
        dragOffset = 0f
    }

    Column(verticalArrangement = Arrangement.spacedBy(StepSpacing)) {
        steps.forEachIndexed { index, step ->
            key(step.id) {
                val dragging = draggingId == step.id
                Surface(
                    modifier = Modifier
                        .onSizeChanged { heights[step.id] = it.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.background,
                    shadowElevation = if (dragging) 10.dp else 0.dp
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(width = 32.dp, height = 40.dp)
                                .pointerInput(step.id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingId = step.id
                                            dragOffset = 0f
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragEnd = { stopDrag() },
                                        onDragCancel = { stopDrag() },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragBy(step.id, amount.y)
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.DragHandle,
                                contentDescription = "Потяните, чтобы переместить шаг",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .size(24.dp)
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
                        Spacer(Modifier.size(6.dp))
                        AppField(
                            value = step.text,
                            onChange = { new ->
                                holder.onChange(
                                    holder.list.map { if (it.id == step.id) it.copy(text = new) else it }
                                )
                            },
                            label = "Действие",
                            modifier = Modifier.weight(1f),
                            onClear = {
                                holder.onChange(
                                    holder.list.map { if (it.id == step.id) it.copy(text = "") else it }
                                )
                            }
                        )
                        if (steps.size > 1) {
                            IconButton(
                                onClick = {
                                    holder.onChange(holder.list.filterNot { it.id == step.id })
                                },
                                modifier = Modifier.padding(top = 4.dp).size(40.dp)
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Удалить шаг")
                            }
                        }
                    }
                }
            }
        }
        TextButton(
            onClick = {
                val nextId = (steps.maxOfOrNull { it.id } ?: 0) + 1
                onChange(steps + StepItem(nextId, ""))
            }
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text("Добавить шаг")
        }
    }
}

// ---------------------------------------------------------------- несохранённые изменения

/** Слепок формы (с обрезанными пробелами) — по нему видно, менял ли человек что-то. */
private data class FormSnapshot(
    val title: String,
    val description: String,
    val preconditions: String,
    val steps: List<String>,
    val expected: String,
    val priority: Priority,
    val status: Status,
    val sectionId: Long?,
    val hasBug: Boolean,
    val bugDescription: String,
    val bugScreens: List<String>
)

private fun TestCase.toSnapshot() = FormSnapshot(
    title = title.trim(),
    description = description.trim(),
    preconditions = preconditions.trim(),
    steps = stepList.map { it.trim() }.filter { it.isNotEmpty() },
    expected = expectedResult.trim(),
    priority = priority,
    status = status,
    sectionId = sectionId,
    hasBug = hasBug,
    bugDescription = bugDescription.trim(),
    bugScreens = bugScreenList
)

// ---------------------------------------------------------------- выпадающий список

/** Поле с выпадающим списком: компактнее набора кнопок и не отодвигает шаги вниз. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownField(
    label: String,
    value: T,
    options: List<T>,
    optionText: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable (T) -> Unit)? = null
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = open,
        onExpandedChange = { open = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = optionText(value),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            leadingIcon = leading?.let { l -> { l(value) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionText(option)) },
                    leadingIcon = leading?.let { l -> { l(option) } },
                    onClick = {
                        onSelect(option)
                        open = false
                    }
                )
            }
        }
    }
}
