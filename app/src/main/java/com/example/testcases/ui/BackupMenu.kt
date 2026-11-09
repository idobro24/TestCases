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
import kotlinx.coroutines.launch

private class PendingRestore(
    val uri: Uri,
    val contents: BackupContents,
    val current: Triple<Int, Int, Int>
)

/**
 * Меню «⋮» на главном экране: сохранить полную копию данных одним файлом
 * и восстановить данные из такой копии.
 */
@Composable
fun BackupMenu(vm: TestCaseViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cases by vm.all.collectAsStateWithLifecycle()
    val sections by vm.sections.collectAsStateWithLifecycle()

    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingRestore?>(null) }

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

    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Резервная копия")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
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
