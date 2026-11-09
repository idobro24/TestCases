package com.example.testcases.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.testcases.data.Section
import com.example.testcases.data.Status
import com.example.testcases.data.TestCase
import com.example.testcases.ui.theme.LocalDarkTheme
import com.example.testcases.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun SectionsScreen(
    vm: TestCaseViewModel,
    onOpenSection: (Long) -> Unit,
    onImport: () -> Unit
) {
    val all by vm.all.collectAsStateWithLifecycle()
    val sections by vm.sections.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val spacingPx = with(density) { 12.dp.toPx() }
    val edgePx = with(density) { 72.dp.toPx() }
    val scrollStepPx = with(density) { 14.dp.toPx() }

    // Порядок на время перетаскивания; в базу он пишется, когда палец отпущен
    var localOrder by remember { mutableStateOf<List<Section>?>(null) }
    LaunchedEffect(sections) { localOrder = null }
    val shown = localOrder ?: sections
    val shownState = rememberUpdatedState(shown)
    val savedState = rememberUpdatedState(sections)
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    // Режим выбора: отмечаем несколько разделов и удаляем их разом
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<Set<Long>>(emptySet()) }
    var showDeleteSelected by rememberSaveable { mutableStateOf(false) }

    fun exitSelection() {
        selectionMode = false
        selected = emptySet()
    }

    fun toggle(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    BackHandler(enabled = selectionMode) { exitSelection() }

    /** Когда перетаскиваемый раздел пересёк середину соседа — меняем их местами. */
    fun checkSwap() {
        val id = draggingId ?: return
        val list = shownState.value
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        val infos = listState.layoutInfo.visibleItemsInfo
        val me = infos.firstOrNull { it.key == id } ?: return
        val center = me.offset + me.size / 2f + dragOffset
        val toIndex = when {
            dragOffset > 0 && index < list.lastIndex -> index + 1
            dragOffset < 0 && index > 0 -> index - 1
            else -> return
        }
        val neighbor = infos.firstOrNull { it.key == list[toIndex].id } ?: return
        val middle = neighbor.offset + neighbor.size / 2f
        val passed = if (toIndex > index) center > middle else center < middle
        if (!passed) return

        val moved = list.toMutableList()
        moved.add(toIndex, moved.removeAt(index))
        localOrder = moved
        val shift = neighbor.size + spacingPx
        dragOffset += if (toIndex > index) -shift else shift
    }

    fun finishDrag() {
        val ids = shownState.value.map { it.id }
        draggingId = null
        dragOffset = 0f
        if (ids == savedState.value.map { it.id }) localOrder = null else vm.reorderSections(ids)
    }

    // Автопрокрутка: у края экрана список едет сам, чтобы можно было дотащить раздел далеко
    LaunchedEffect(draggingId) {
        if (draggingId == null) return@LaunchedEffect
        while (isActive) {
            val id = draggingId ?: break
            val info = listState.layoutInfo
            val me = info.visibleItemsInfo.firstOrNull { it.key == id }
            if (me != null) {
                val top = me.offset + dragOffset
                val bottom = top + me.size
                val step = when {
                    top < info.viewportStartOffset + edgePx -> -scrollStepPx
                    bottom > info.viewportEndOffset - edgePx -> scrollStepPx
                    else -> 0f
                }
                if (step != 0f) {
                    val used = listState.scrollBy(step)
                    dragOffset += used
                    checkSwap()
                }
            }
            delay(16)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!selectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { showCreate = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("Новый раздел") }
                )
            }
        }
    ) { padding ->
        val list = all
        if (list == null) {
            Box(Modifier.fillMaxSize())
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                if (selectionMode) {
                    SelectionHeader(
                        count = selected.size,
                        total = sections.size,
                        onCancel = ::exitSelection,
                        onSelectAll = {
                            selected = if (selected.size == sections.size) {
                                emptySet()
                            } else {
                                sections.map { it.id }.toSet()
                            }
                        },
                        onDelete = { showDeleteSelected = true }
                    )
                } else {
                    HomeHeader(
                        cases = list,
                        themeMode = themeMode,
                        onThemeChange = vm::setThemeMode,
                        onImport = onImport,
                        canSelect = sections.isNotEmpty(),
                        onSelect = { selectionMode = true },
                        backupSlot = { BackupMenu(vm) }
                    )
                }
            }

            if (list.isEmpty() && sections.isEmpty()) {
                item(key = "empty") {
                    EmptyHome(onCreateSection = { showCreate = true }, onImport = onImport)
                }
            } else {
                if (!selectionMode) item(key = "all") {
                    SectionCard(
                        title = "Все кейсы",
                        cases = list,
                        highlighted = true,
                        onClick = { onOpenSection(SECTION_ALL) }
                    )
                }
                items(shown, key = { it.id }) { s ->
                    val dragging = draggingId == s.id
                    SectionCard(
                        title = s.name,
                        cases = list.filter { it.sectionId == s.id },
                        modifier = if (dragging) {
                            Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset }
                        } else {
                            Modifier.animateItem()
                        },
                        dragging = dragging,
                        dragHandleModifier = if (selectionMode) null else Modifier.pointerInput(s.id) {
                            detectDragGestures(
                                onDragStart = {
                                    draggingId = s.id
                                    dragOffset = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = { finishDrag() },
                                onDragCancel = { finishDrag() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    checkSwap()
                                }
                            )
                        },
                        selectionMode = selectionMode,
                        selected = s.id in selected,
                        onLongClick = {
                            selectionMode = true
                            selected = selected + s.id
                        },
                        onClick = { if (selectionMode) toggle(s.id) else onOpenSection(s.id) }
                    )
                }
                val loose = list.filter { it.sectionId == null }
                if (loose.isNotEmpty() && !selectionMode) {
                    item(key = "none") {
                        SectionCard(
                            title = "Без раздела",
                            cases = loose,
                            onClick = { onOpenSection(SECTION_NONE) }
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        SectionNameDialog(
            title = "Новый раздел",
            initial = "",
            confirmLabel = "Создать",
            onConfirm = {
                vm.addSection(it)
                showCreate = false
            },
            onDismiss = { showCreate = false }
        )
    }

    if (showDeleteSelected) {
        val targets = sections.filter { it.id in selected }
        val casesCount = all.orEmpty().count { it.sectionId != null && it.sectionId in selected }
        val names = targets.take(5).joinToString("\n") { "• ${it.name}" } +
            if (targets.size > 5) "\nи ещё ${targets.size - 5}" else ""
        val note = if (casesCount == 0) {
            "В выбранных разделах нет кейсов."
        } else {
            "В них $casesCount ${plural(casesCount, "кейс", "кейса", "кейсов")}. " +
                "Их можно оставить без раздела или удалить вместе с разделами."
        }
        AlertDialog(
            onDismissRequest = { showDeleteSelected = false },
            title = { Text("Удалить ${targets.size} ${plural(targets.size, "раздел", "раздела", "разделов")}?") },
            text = { Text("$names\n\n$note") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (casesCount > 0) {
                        TextButton(onClick = {
                            vm.deleteSections(selected, withCases = true)
                            showDeleteSelected = false
                            exitSelection()
                        }) {
                            Text("Удалить вместе с кейсами", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = {
                            vm.deleteSections(selected, withCases = false)
                            showDeleteSelected = false
                            exitSelection()
                        }) {
                            Text("Удалить разделы, кейсы оставить")
                        }
                    } else {
                        TextButton(onClick = {
                            vm.deleteSections(selected, withCases = false)
                            showDeleteSelected = false
                            exitSelection()
                        }) {
                            Text("Удалить", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { showDeleteSelected = false }) { Text("Отмена") }
                }
            }
        )
    }
}

@Composable
private fun HomeHeader(
    cases: List<TestCase>,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onImport: () -> Unit,
    canSelect: Boolean,
    onSelect: () -> Unit,
    backupSlot: @Composable () -> Unit
) {
    val passed = cases.count { it.status == Status.PASSED }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Тест-кейсы",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            ThemeMenu(themeMode, onThemeChange)
            backupSlot()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (cases.isEmpty()) "" else "Пройдено $passed из ${cases.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (canSelect) {
                TextButton(onClick = onSelect) { Text("Выбрать") }
            }
            FilledTonalButton(onClick = onImport) {
                Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Импорт")
            }
        }
        if (cases.isNotEmpty()) StatusBar(cases)
    }
}

/** Выбор темы: как в системе / светлая / тёмная. */
@Composable
private fun ThemeMenu(mode: ThemeMode, onChange: (ThemeMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val dark = LocalDarkTheme.current
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                contentDescription = "Тема оформления"
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ThemeMode.entries.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.label) },
                    trailingIcon = {
                        if (m == mode) Icon(Icons.Rounded.Check, contentDescription = null)
                    },
                    onClick = {
                        open = false
                        onChange(m)
                    }
                )
            }
        }
    }
}

/** Шапка в режиме выбора: сколько отмечено, «выбрать все» и удаление. */
@Composable
private fun SelectionHeader(
    count: Int,
    total: Int,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Rounded.Close, contentDescription = "Отменить выбор")
        }
        Text(
            "Выбрано: $count",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onSelectAll) {
            Text(if (count == total) "Снять все" else "Выбрать все")
        }
        IconButton(onClick = onDelete, enabled = count > 0) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = "Удалить выбранные разделы",
                tint = if (count > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun EmptyHome(onCreateSection: () -> Unit, onImport: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 56.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Пока ничего нет", style = MaterialTheme.typography.titleMedium)
        Text(
            "Создайте раздел и складывайте в него кейсы или загрузите готовые кейсы из файла.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onCreateSection) { Text("Создать раздел") }
        OutlinedButton(onClick = onImport) { Text("Загрузить из файла") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SectionCard(
    title: String,
    cases: List<TestCase>,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
    dragging: Boolean = false,
    dragHandleModifier: Modifier? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val passed = cases.count { it.status == Status.PASSED }
    Surface(
        modifier = modifier,
        shadowElevation = if (dragging) 10.dp else 0.dp,
        shape = RoundedCornerShape(16.dp),
        color = if (highlighted || selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (cases.isEmpty()) {
                            "Пока пусто"
                        } else {
                            "${cases.size} ${plural(cases.size, "кейс", "кейса", "кейсов")}, пройдено $passed"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selectionMode) {
                    Checkbox(checked = selected, onCheckedChange = null)
                } else if (dragHandleModifier != null) {
                    Box(
                        modifier = dragHandleModifier
                            .size(width = 36.dp, height = 40.dp)
                            .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.DragHandle,
                            contentDescription = "Потяните, чтобы переместить раздел",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!selectionMode) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (cases.isNotEmpty()) StatusBar(cases, height = 6.dp)
        }
    }
}

/** Общий диалог: создание и переименование раздела. */
@Composable
internal fun SectionNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Название раздела") },
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
