package com.example.testcases.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class ProjectDialog { NONE, NEW, RENAME, DELETE }

/**
 * Название текущего проекта вверху главного экрана. Нажатие открывает список проектов
 * и действия: создать, переименовать, удалить.
 */
@Composable
fun ProjectSwitcher(vm: TestCaseViewModel) {
    val projects by vm.projects.collectAsStateWithLifecycle()
    val currentId by vm.currentProjectId.collectAsStateWithLifecycle()
    val cases by vm.all.collectAsStateWithLifecycle()
    val sections by vm.sections.collectAsStateWithLifecycle()
    val current = projects.firstOrNull { it.id == currentId }

    var open by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(ProjectDialog.NONE) }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { open = true }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                current?.name ?: "Тест-кейсы",
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Сменить проект")
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            projects.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.name) },
                    leadingIcon = {
                        if (p.id == currentId) {
                            Icon(Icons.Rounded.Check, contentDescription = "Текущий проект")
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    onClick = {
                        open = false
                        if (p.id != currentId) vm.selectProject(p.id)
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Новый проект") },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                onClick = {
                    open = false
                    dialog = ProjectDialog.NEW
                }
            )
            DropdownMenuItem(
                text = { Text("Переименовать проект") },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                enabled = current != null,
                onClick = {
                    open = false
                    dialog = ProjectDialog.RENAME
                }
            )
            DropdownMenuItem(
                text = { Text("Удалить проект") },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                enabled = projects.size > 1,
                onClick = {
                    open = false
                    dialog = ProjectDialog.DELETE
                }
            )
        }
    }

    when (dialog) {
        ProjectDialog.NEW -> SectionNameDialog(
            title = "Новый проект",
            initial = "",
            confirmLabel = "Создать",
            label = "Название проекта",
            onConfirm = {
                vm.addProject(it)
                dialog = ProjectDialog.NONE
            },
            onDismiss = { dialog = ProjectDialog.NONE }
        )

        ProjectDialog.RENAME -> current?.let { p ->
            SectionNameDialog(
                title = "Переименовать проект",
                initial = p.name,
                confirmLabel = "Сохранить",
                label = "Название проекта",
                onConfirm = {
                    vm.renameProject(p, it)
                    dialog = ProjectDialog.NONE
                },
                onDismiss = { dialog = ProjectDialog.NONE }
            )
        }

        ProjectDialog.DELETE -> current?.let { p ->
            val caseCount = cases.orEmpty().size
            AlertDialog(
                onDismissRequest = { dialog = ProjectDialog.NONE },
                title = { Text("Удалить проект «${p.name}»?") },
                text = {
                    Text(
                        "Вместе с проектом удалятся его кейсы ($caseCount) и разделы (${sections.size}). " +
                            "Отменить это нельзя. Если нужно, сначала сохраните копию: " +
                            "⋮ → «Сохранить копию»."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = ProjectDialog.NONE
                        vm.deleteProject(p)
                    }) {
                        Text("Удалить проект", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { dialog = ProjectDialog.NONE }) { Text("Отмена") } }
            )
        }

        ProjectDialog.NONE -> {}
    }
}
