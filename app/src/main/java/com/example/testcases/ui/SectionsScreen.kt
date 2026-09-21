package com.example.testcases.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.testcases.data.Status
import com.example.testcases.data.TestCase
import com.example.testcases.ui.theme.LocalDarkTheme
import com.example.testcases.ui.theme.ThemeMode

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Новый раздел") }
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
            item(key = "header") { HomeHeader(list, themeMode, vm::setThemeMode, onImport) }

            if (list.isEmpty() && sections.isEmpty()) {
                item(key = "empty") {
                    EmptyHome(onCreateSection = { showCreate = true }, onImport = onImport)
                }
            } else {
                item(key = "all") {
                    SectionCard(
                        title = "Все кейсы",
                        cases = list,
                        highlighted = true,
                        onClick = { onOpenSection(SECTION_ALL) }
                    )
                }
                items(sections, key = { it.id }) { s ->
                    SectionCard(
                        title = s.name,
                        cases = list.filter { it.sectionId == s.id },
                        onClick = { onOpenSection(s.id) }
                    )
                }
                val loose = list.filter { it.sectionId == null }
                if (loose.isNotEmpty()) {
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
}

@Composable
private fun HomeHeader(
    cases: List<TestCase>,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onImport: () -> Unit
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
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (cases.isEmpty()) "" else "Пройдено $passed из ${cases.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
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

@Composable
private fun SectionCard(
    title: String,
    cases: List<TestCase>,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    val passed = cases.count { it.status == Status.PASSED }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
