package com.example.testcases.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.testcases.data.CsvExport
import com.example.testcases.data.Priority
import com.example.testcases.data.Status
import com.example.testcases.data.TestCase
import com.example.testcases.ui.theme.color
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaseListScreen(
    vm: TestCaseViewModel,
    sectionId: Long,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    onRun: () -> Unit
) {
    val all by vm.all.collectAsStateWithLifecycle()
    val sections by vm.sections.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf<Status?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val section = sections.firstOrNull { it.id == sectionId }
    val title = when (sectionId) {
        SECTION_ALL -> "Все кейсы"
        SECTION_NONE -> "Без раздела"
        else -> section?.name.orEmpty()
    }
    val sectionNames = remember(sections) { sections.associate { it.id to it.name } }
    val scoped = remember(all, sectionId) { all.orEmpty().inSection(sectionId) }
    val visible = remember(scoped, query, statusFilter) {
        scoped.filter { tc ->
            (statusFilter == null || tc.status == statusFilter) &&
                (query.isBlank() ||
                    tc.title.contains(query, ignoreCase = true) ||
                    tc.code.contains(query, ignoreCase = true) ||
                    tc.description.contains(query, ignoreCase = true))
        }
    }

    fun deleteWithUndo(tc: TestCase) {
        vm.delete(tc)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = "${tc.code} удалён",
                actionLabel = "Отменить",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) vm.restore(tc)
        }
    }

    val context = LocalContext.current

    /** Экспорт всех кейсов текущего раздела (без учёта поиска и фильтра). */
    fun exportCases() {
        val fileName = when (sectionId) {
            SECTION_ALL -> "test-cases.csv"
            SECTION_NONE -> "test-cases-no-section.csv"
            else -> title.replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').ifEmpty { "section" } + ".csv"
        }
        CsvExport.share(context, fileName, CsvExport.build(scoped, sectionNames))
    }

    fun copyCase(tc: TestCase) {
        vm.duplicate(tc) { copy ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(
                    message = "Создана копия ${copy.code}",
                    actionLabel = "Открыть",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) onOpen(copy.id)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = ::exportCases, enabled = scoped.isNotEmpty()) {
                        Icon(Icons.Rounded.Share, contentDescription = "Поделиться кейсами")
                    }
                    if (section != null) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Действия с разделом")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Переименовать") },
                                    onClick = {
                                        menuOpen = false
                                        showRename = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Удалить раздел") },
                                    onClick = {
                                        menuOpen = false
                                        showDelete = true
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Новый кейс") }
            )
        }
    ) { padding ->
        if (all == null) {
            Box(Modifier.fillMaxSize())
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (scoped.isNotEmpty()) {
                item(key = "progress") { Progress(scoped, onRun) }
                item(key = "filters") { FilterRow(scoped, statusFilter) { statusFilter = it } }
                item(key = "search") { SearchField(query) { query = it } }
            }

            when {
                scoped.isEmpty() -> item(key = "empty") { EmptyState(onCreate) }
                visible.isEmpty() -> item(key = "nothing") { NoResults() }
                else -> items(visible, key = { it.id }) { tc ->
                    SwipeActionBox(
                        modifier = Modifier.animateItem(),
                        onDelete = { deleteWithUndo(tc) },
                        onCopy = { copyCase(tc) }
                    ) {
                        TestCaseCard(
                            tc = tc,
                            sectionName = if (sectionId == SECTION_ALL) {
                                tc.sectionId?.let { sectionNames[it] }
                            } else {
                                null
                            },
                            onClick = { onOpen(tc.id) },
                            onStatusChange = { vm.setStatus(tc, it) }
                        )
                    }
                }
            }
        }
    }

    if (showRename && section != null) {
        SectionNameDialog(
            title = "Переименовать раздел",
            initial = section.name,
            confirmLabel = "Сохранить",
            onConfirm = {
                vm.renameSection(section, it)
                showRename = false
            },
            onDismiss = { showRename = false }
        )
    }

    if (showDelete && section != null) {
        val count = scoped.size
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Удалить раздел «${section.name}»?") },
            text = {
                Text(
                    if (count == 0) {
                        "В разделе нет кейсов."
                    } else {
                        "В нём $count ${plural(count, "кейс", "кейса", "кейсов")}. " +
                            "Их можно оставить без раздела или удалить вместе с ним."
                    }
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (count > 0) {
                        TextButton(onClick = {
                            showDelete = false
                            vm.deleteSection(section, withCases = true)
                            onBack()
                        }) {
                            Text("Удалить вместе с кейсами", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = {
                            showDelete = false
                            vm.deleteSection(section, withCases = false)
                            onBack()
                        }) {
                            Text("Удалить раздел, кейсы оставить")
                        }
                    } else {
                        TextButton(onClick = {
                            showDelete = false
                            vm.deleteSection(section, withCases = false)
                            onBack()
                        }) {
                            Text("Удалить", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { showDelete = false }) { Text("Отмена") }
                }
            }
        )
    }
}

// ---------------------------------------------------------------- progress

@Composable
private fun Progress(cases: List<TestCase>, onRun: () -> Unit) {
    val passed = cases.count { it.status == Status.PASSED }
    val unrun = cases.count { it.status == Status.NOT_RUN }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Пройдено $passed из ${cases.size}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StatusBar(cases)
        FilledTonalButton(
            onClick = onRun,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (unrun in 1 until cases.size) "Продолжить прогон (осталось $unrun)" else "Начать прогон"
            )
        }
    }
}

/** Полоса с долями статусов: сразу видно, как идёт тестирование. */
@Composable
internal fun StatusBar(cases: List<TestCase>, height: androidx.compose.ui.unit.Dp = 10.dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(MaterialTheme.colorScheme.outlineVariant),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf(Status.PASSED, Status.FAILED, Status.BLOCKED, Status.NOT_RUN).forEach { s ->
            val n = cases.count { it.status == s }
            if (n > 0) {
                Box(
                    Modifier
                        .weight(n.toFloat())
                        .fillMaxHeight()
                        .background(s.color())
                )
            }
        }
    }
}

@Composable
private fun FilterRow(cases: List<TestCase>, selected: Status?, onSelect: (Status?) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("Все ${cases.size}") }
        )
        Status.entries.forEach { s ->
            val n = cases.count { it.status == s }
            FilterChip(
                selected = selected == s,
                onClick = { onSelect(if (selected == s) null else s) },
                label = { Text("${s.label} $n") },
                leadingIcon = {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(s.color()))
                }
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        placeholder = { Text("Название или номер, например TC-007") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Очистить поиск")
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

// ---------------------------------------------------------------- empty states

@Composable
private fun EmptyState(onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Здесь пока нет тест-кейсов", style = MaterialTheme.typography.titleMedium)
        Text(
            "Опишите шаги и ожидаемый результат — так проверку можно повторить в любой момент.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onCreate) { Text("Создать кейс") }
    }
}

@Composable
private fun NoResults() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Ничего не найдено", style = MaterialTheme.typography.titleMedium)
        Text(
            "Измените запрос или сбросьте фильтр по статусу.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------- card

@Composable
private fun TestCaseCard(
    tc: TestCase,
    sectionName: String?,
    onClick: () -> Unit,
    onStatusChange: (Status) -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Цветная полоса слева — статус виден даже при быстром скролле
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(tc.status.color())
            )
            Column(
                modifier = Modifier.weight(1f).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tc.code,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sectionName != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            sectionName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    PriorityTag(tc.priority)
                }
                Text(
                    tc.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (tc.description.isNotBlank()) {
                    Text(
                        tc.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(tc.status, onStatusChange)
                    Spacer(Modifier.weight(1f))
                    val n = tc.stepList.size
                    if (n > 0) {
                        Text(
                            "$n ${plural(n, "шаг", "шага", "шагов")}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Приоритет как «уровень сигнала»: 1–4 столбика. */
@Composable
internal fun PriorityTag(priority: Priority) {
    val on = MaterialTheme.colorScheme.onSurfaceVariant
    val off = MaterialTheme.colorScheme.outlineVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .width(3.dp)
                        .height((5 + i * 3).dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (i <= priority.ordinal) on else off)
                )
            }
        }
        Text(priority.label, style = MaterialTheme.typography.labelMedium, color = on)
    }
}

/** Нажмите на статус, чтобы быстро сменить его, не открывая кейс. */
@Composable
private fun StatusPill(status: Status, onSelect: (Status) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val color = status.color()
    Box {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(color.copy(alpha = 0.14f))
                .clickable { open = true }
                .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(status.label, style = MaterialTheme.typography.labelMedium, color = color)
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Сменить статус",
                modifier = Modifier.size(16.dp),
                tint = color
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Status.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label) },
                    leadingIcon = {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(s.color()))
                    },
                    onClick = {
                        open = false
                        onSelect(s)
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------- swipe

/** Свайп влево — удалить, вправо — сделать копию. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeActionBox(
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    content: @Composable () -> Unit
) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    val currentOnCopy by rememberUpdatedState(onCopy)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    currentOnDelete()
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    currentOnCopy()
                    false // карточка возвращается на место
                }
                else -> false
            }
        }
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            when (state.dismissDirection) {
                SwipeToDismissBoxValue.EndToStart -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.error),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Удалить",
                        tint = Color.White,
                        modifier = Modifier.padding(end = 24.dp)
                    )
                }
                SwipeToDismissBoxValue.StartToEnd -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "Копировать",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(start = 24.dp)
                    )
                }
                else -> {}
            }
        }
    ) {
        content()
    }
}
