package com.example.testcases.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.testcases.data.Backup
import com.example.testcases.data.BackupContents
import com.example.testcases.data.BackupException
import com.example.testcases.data.CsvExport
import com.example.testcases.data.ResultsFile
import com.example.testcases.data.ResultsPlan
import com.example.testcases.data.RunResults
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class PendingRestore(
    val uri: Uri,
    val contents: BackupContents,
    val current: Triple<Int, Int, Int>
)

private class PendingResults(
    val uri: Uri,
    val file: ResultsFile,
    val plan: ResultsPlan
)

/**
 * Меню «⋮» на главном экране: выгрузить все кейсы проекта в Excel,
 * обменяться результатами прогона с другим устройством,
 * сохранить полную копию данных одним файлом и восстановить данные из такой копии.
 */
@Composable
fun BackupMenu(vm: TestCaseViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cases by vm.all.collectAsStateWithLifecycle()
    val sections by vm.sections.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val currentProjectId by vm.currentProjectId.collectAsStateWithLifecycle()

    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingRestore?>(null) }
    var pendingResults by remember { mutableStateOf<PendingResults?>(null) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                try {
                    pending = PendingRestore(uri, vm.readBackup(uri), vm.totalCounts())
                } catch (e: BackupException) {
                    toast(e.message ?: "Не удалось прочитать файл.")
                } catch (e: Exception) {
                    toast("Не удалось прочитать файл.")
                } finally {
                    busy = false
                }
            }
        }
    }

    val resultsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                try {
                    val file = vm.readResults(uri)
                    pendingResults = PendingResults(uri, file, vm.planResults(file))
                } catch (e: BackupException) {
                    toast(e.message ?: "Не удалось прочитать файл.")
                } catch (e: Exception) {
                    toast("Не удалось прочитать файл.")
                } finally {
                    busy = false
                }
            }
        }
    }

    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Меню")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Экспорт в Excel (все кейсы)") },
                onClick = {
                    open = false
                    val list = cases.orEmpty()
                    if (list.isEmpty()) {
                        toast("В проекте пока нет кейсов")
                    } else {
                        val projectName = projects.firstOrNull { it.id == currentProjectId }?.name.orEmpty()
                        val baseName = projectName
                            .replace(Regex("[^\\p{L}\\p{N}]+"), "_")
                            .trim('_')
                            .ifEmpty { "test-cases" }
                        CsvExport.shareXlsx(
                            context,
                            "$baseName.xlsx",
                            list,
                            sections.associate { it.id to it.name }
                        )
                    }
                }
            )
            DropdownMenuItem(
                text = { Text("Отправить результаты") },
                onClick = {
                    open = false
                    if (cases.orEmpty().isEmpty()) {
                        toast("В проекте пока нет кейсов")
                    } else {
                        scope.launch {
                            busy = true
                            try {
                                RunResults.share(context, vm.createResultsFile())
                            } catch (e: Exception) {
                                toast("Не удалось создать файл: ${e.message}")
                            } finally {
                                busy = false
                            }
                        }
                    }
                }
            )
            DropdownMenuItem(
                text = { Text("Принять результаты") },
                onClick = {
                    open = false
                    resultsPicker.launch(arrayOf("*/*"))
                }
            )
            DropdownMenuItem(
                text = { Text("Сохранить копию") },
                onClick = {
                    open = false
                    scope.launch {
                        busy = true
                        try {
                            Backup.share(context, vm.createBackupFile())
                        } catch (e: Exception) {
                            toast("Не удалось создать копию: ${e.message}")
                        } finally {
                            busy = false
                        }
                    }
                }
            )
            DropdownMenuItem(
                text = { Text("Восстановить из копии") },
                onClick = {
                    open = false
                    picker.launch(arrayOf("*/*"))
                }
            )
        }
    }

    if (busy) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Подождите…") },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        )
    }

    pendingResults?.let { r ->
        val plan = r.plan
        val currentName = projects.firstOrNull { it.id == currentProjectId }?.name.orEmpty()
        val date = if (r.file.exportedAt > 0) {
            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(r.file.exportedAt))
        } else {
            ""
        }
        val changed = plan.updated.size
        AlertDialog(
            onDismissRequest = { pendingResults = null },
            title = { Text("Принять результаты?") },
            text = {
                Text(
                    buildString {
                        append("Файл: проект «${r.file.project}»")
                        if (date.isNotEmpty()) append(", $date")
                        append(".\nПрименится к проекту «$currentName».\n\n")
                        append("Изменится: $changed ${plural(changed, "кейс", "кейса", "кейсов")}")
                        if (changed > 0) {
                            val byStatus = plan.updated.groupingBy { it.status }.eachCount()
                            append(" (")
                            append(byStatus.entries.joinToString(", ") { "${it.key.label.lowercase()}: ${it.value}" })
                            append(")")
                        }
                        append(".\nБез изменений: ${plan.unchanged}.")
                        if (plan.notFound > 0) {
                            append("\nНе найдено в этом проекте: ${plan.notFound} — такие кейсы пропускаются.")
                        }
                        append("\n\nМеняются только статусы и баги, остальное не трогается.")
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = changed > 0,
                    onClick = {
                        pendingResults = null
                        busy = true
                        vm.applyResults(r.uri, plan) { ok ->
                            busy = false
                            toast(
                                if (ok) {
                                    "Обновлено: $changed ${plural(changed, "кейс", "кейса", "кейсов")}"
                                } else {
                                    "Не удалось применить результаты"
                                }
                            )
                        }
                    }
                ) {
                    Text("Применить")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingResults = null }) {
                    Text(if (changed > 0) "Отмена" else "Закрыть")
                }
            }
        )
    }

    pending?.let { p ->
        val c = p.contents
        val (curProjects, curSections, curCases) = p.current
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Восстановить из копии?") },
            text = {
                Text(
                    "В копии: ${c.projects.size} ${plural(c.projects.size, "проект", "проекта", "проектов")}, " +
                        "${c.sections.size} ${plural(c.sections.size, "раздел", "раздела", "разделов")}, " +
                        "${c.cases.size} ${plural(c.cases.size, "кейс", "кейса", "кейсов")}, " +
                        "скриншотов: ${c.screenCount}.\n\n" +
                        "Сейчас в приложении: $curProjects ${plural(curProjects, "проект", "проекта", "проектов")}, " +
                        "$curSections ${plural(curSections, "раздел", "раздела", "разделов")}, " +
                        "$curCases ${plural(curCases, "кейс", "кейса", "кейсов")}. " +
                        "Всё это, включая все проекты, будет заменено содержимым копии. Отменить это нельзя."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    busy = true
                    vm.restoreBackup(p.uri, p.contents) { ok ->
                        busy = false
                        toast(if (ok) "Копия восстановлена" else "Не удалось восстановить копию")
                    }
                }) {
                    Text("Заменить данные", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Отмена") } }
        )
    }
}
