package com.example.testcases.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.testcases.data.ImportResult
import com.example.testcases.data.ParsedCase
import com.example.testcases.data.Section
import com.example.testcases.data.Status
import com.example.testcases.ui.theme.color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    vm: TestCaseViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val preview by vm.importPreview.collectAsStateWithLifecycle()
    val sections by vm.sections.collectAsStateWithLifecycle()
    var target by rememberSaveable { mutableStateOf<Long?>(null) }
    var importing by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.loadImport(uri)
    }
    val pickFile: () -> Unit = { picker.launch(arrayOf("*/*")) }

    val p = preview
    val readyResult = p?.result?.takeIf { it.fatal == null && it.cases.isNotEmpty() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Импорт из файла") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (readyResult != null) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(
                        Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        val n = readyResult.cases.size
                        Button(
                            onClick = {
                                importing = true
                                vm.importCases(readyResult.cases, target) { count ->
                                    Toast.makeText(context, "Импортировано: $count", Toast.LENGTH_SHORT).show()
                                    vm.clearImport()
                                    onDone()
                                }
                            },
                            enabled = !importing,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Импортировать $n ${plural(n, "кейс", "кейса", "кейсов")}")
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (p == null) {
                item(key = "intro") { Instructions(pickFile) }
            } else {
                val res = p.result
                val fatal = res.fatal
                if (fatal != null) {
                    item(key = "error") { FatalBox(p.fileName, fatal, pickFile) }
                } else {
                    item(key = "summary") { Summary(p.fileName, res.cases.size, pickFile) }
                    if (res.warnings.isNotEmpty()) {
                        item(key = "warnings") { Warnings(res.warnings) }
                    }
                    item(key = "target") {
                        TargetPicker(res, sections, target) { target = it }
                    }
                    items(res.cases) { c -> PreviewRow(c) }
                }
            }
        }
    }
}

@Composable
private fun Instructions(onPick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Загрузите файл с кейсами. Перед импортом вы увидите их список и сможете всё проверить.",
            style = MaterialTheme.typography.bodyMedium
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Формат файла", style = MaterialTheme.typography.titleSmall)
                Text(
                    "CSV в кодировке UTF-8. Первая строка — названия столбцов:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Раздел; Название; Описание; Предусловия; Шаги; Ожидаемый результат; Приоритет; Статус",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "Обязателен только «Название». Порядок столбцов не важен.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Шаги: каждый шаг с новой строки внутри ячейки (или через знак |).",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Приоритет: Низкий, Средний, Высокий, Критичный. " +
                        "Статус: Не запущен, Пройден, Провален, Заблокирован.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Из Excel: Файл → Сохранить как → CSV UTF-8. " +
                        "Из Google Таблиц: Файл → Скачать → CSV.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Button(
            onClick = onPick,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Выбрать файл") }
    }
}

@Composable
private fun FatalBox(fileName: String, message: String, onPick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Status.FAILED.color().copy(alpha = 0.10f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Не удалось загрузить кейсы", style = MaterialTheme.typography.titleMedium)
            Text(
                fileName,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onPick) { Text("Выбрать другой файл") }
        }
    }
}

@Composable
private fun Summary(fileName: String, count: Int, onPick: () -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Найдено $count ${plural(count, "кейс", "кейса", "кейсов")}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                fileName,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onPick) { Text("Другой файл") }
    }
}

@Composable
private fun Warnings(warnings: List<String>) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Status.BLOCKED.color().copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Обратите внимание", style = MaterialTheme.typography.titleSmall)
            warnings.take(5).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (warnings.size > 5) {
                Text(
                    "и ещё ${warnings.size - 5}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetPicker(
    res: ImportResult,
    sections: List<Section>,
    target: Long?,
    onSelect: (Long?) -> Unit
) {
    val fromFile = res.cases
        .mapNotNull { c -> c.sectionName?.trim()?.takeIf { it.isNotEmpty() } }
        .groupBy { it.lowercase() }
    val loose = res.cases.count { it.sectionName.isNullOrBlank() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (fromFile.isNotEmpty()) {
            Text(
                "Разделы из файла",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                fromFile.values.joinToString(", ") { "${it.first()} (${it.size})" } +
                    ". Если такого раздела ещё нет, он будет создан.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (loose > 0) {
            Text(
                "Куда добавить кейсы без раздела ($loose)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = target == null,
                    onClick = { onSelect(null) },
                    label = { Text("Без раздела") }
                )
                sections.forEach { s ->
                    FilterChip(
                        selected = target == s.id,
                        onClick = { onSelect(s.id) },
                        label = { Text(s.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewRow(c: ParsedCase) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                c.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val details = listOfNotNull(
                c.sectionName?.takeIf { it.isNotBlank() },
                c.priority.label,
                c.status.label,
                c.steps.size.takeIf { it > 0 }?.let { "$it ${plural(it, "шаг", "шага", "шагов")}" }
            ).joinToString(", ")
            Text(
                details,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
