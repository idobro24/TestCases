package com.example.testcases.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.testcases.data.Priority
import com.example.testcases.data.Status
import com.example.testcases.data.TestCase
import com.example.testcases.ui.theme.color
import kotlinx.coroutines.launch

@Composable
fun ListScreen(
    vm: TestCaseViewModel,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit
) {
    val all by vm.all.collectAsStateWithLifecycle()
    val visible by vm.visible.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
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
        val list = all
        if (list == null) {
            Box(Modifier.fillMaxSize())
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") { Header(list) }

            if (list.isNotEmpty()) {
                item(key = "filters") { FilterRow(list, filter, vm::setFilter) }
                item(key = "search") { SearchField(query, vm::setQuery) }
            }

            when {
                list.isEmpty() -> item(key = "empty") { EmptyState(onCreate) }
                visible.isEmpty() -> item(key = "nothing") { NoResults() }
                else -> items(visible, key = { it.id }) { tc ->
                    SwipeToDeleteBox(
                        modifier = Modifier.animateItem(),
                        onDelete = { deleteWithUndo(tc) }
                    ) {
                        TestCaseCard(
                            tc = tc,
                            onClick = { onOpen(tc.id) },
                            onStatusChange = { vm.setStatus(tc, it) }
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- header

@Composable
private fun Header(cases: List<TestCase>) {
    val passed = cases.count { it.status == Status.PASSED }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Тест-кейсы", style = MaterialTheme.typography.headlineMedium)
        if (cases.isNotEmpty()) {
            Text(
                "Пройдено $passed из ${cases.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusBar(cases)
        }
    }
}

/** Полоса с долями статусов: сразу видно, как идёт тестирование. */
@Composable
private fun StatusBar(cases: List<TestCase>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
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
        Text("Тест-кейсов пока нет", style = MaterialTheme.typography.titleMedium)
        Text(
            "Опишите шаги и ожидаемый результат — так проверку можно повторить в любой момент.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onCreate) { Text("Создать первый кейс") }
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
private fun PriorityTag(priority: Priority) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteBox(
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                currentOnDelete()
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Box(
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
            }
        }
    ) {
        content()
    }
}
