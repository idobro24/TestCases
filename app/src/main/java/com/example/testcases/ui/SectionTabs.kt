package com.example.testcases.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.testcases.data.Block
import com.example.testcases.data.Section

/** Раздел входит в выбранную вкладку? TAB_ALL — показываем все разделы. */
internal fun Section.inTab(tab: Long): Boolean = when (tab) {
    TAB_ALL -> true
    TAB_NONE -> blockId == null
    else -> blockId == tab
}

/** Если выбранной вкладки больше нет (её удалили), показываем все разделы. */
internal fun effectiveTab(selected: Long, blocks: List<Block>): Long =
    if (selected > 0 && blocks.none { it.id == selected }) TAB_ALL else selected

private enum class TabDialog { NONE, NEW, RENAME, DELETE }

/**
 * Ряд вкладок над списком разделов: «Все», ваши вкладки и «Без вкладки».
 * «+» создаёт вкладку, «⋮» (у выбранной вкладки) переименовывает, сдвигает и удаляет её.
 */
@Composable
fun SectionTabs(vm: TestCaseViewModel, hasUnassigned: Boolean) {
    val blocks by vm.blocks.collectAsStateWithLifecycle()
    val sections by vm.sections.collectAsStateWithLifecycle()
    val selectedRaw by vm.selectedTab.collectAsStateWithLifecycle()
    val selected = effectiveTab(selectedRaw, blocks)
    val current = blocks.firstOrNull { it.id == selected }
    val index = blocks.indexOfFirst { it.id == selected }

    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(TabDialog.NONE) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selected == TAB_ALL,
                onClick = { vm.selectTab(TAB_ALL) },
                label = { Text("Все") }
            )
            blocks.forEach { b ->
                FilterChip(
                    selected = selected == b.id,
                    onClick = { vm.selectTab(b.id) },
                    label = { Text(b.name) }
                )
            }
            if (blocks.isNotEmpty() && (hasUnassigned || selected == TAB_NONE)) {
                FilterChip(
                    selected = selected == TAB_NONE,
                    onClick = { vm.selectTab(TAB_NONE) },
                    label = { Text("Без вкладки") }
                )
            }
        }

        if (current != null) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Действия с вкладкой")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Переименовать вкладку") },
                        onClick = {
                            menuOpen = false
                            dialog = TabDialog.RENAME
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Сдвинуть влево") },
                        enabled = index > 0,
                        onClick = {
                            menuOpen = false
                            vm.moveBlock(current, -1)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Сдвинуть вправо") },
                        enabled = index in 0 until blocks.lastIndex,
                        onClick = {
                            menuOpen = false
                            vm.moveBlock(current, 1)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить вкладку") },
                        onClick = {
                            menuOpen = false
                            dialog = TabDialog.DELETE
                        }
                    )
                }
            }
        }

        IconButton(onClick = { dialog = TabDialog.NEW }) {
            Icon(Icons.Rounded.Add, contentDescription = "Новая вкладка")
        }
    }

    when (dialog) {
        TabDialog.NEW -> SectionNameDialog(
            title = "Новая вкладка",
            initial = "",
            confirmLabel = "Создать",
            label = "Название вкладки",
            onConfirm = {
                vm.addBlock(it)
                dialog = TabDialog.NONE
            },
            onDismiss = { dialog = TabDialog.NONE }
        )

        TabDialog.RENAME -> current?.let { b ->
            SectionNameDialog(
                title = "Переименовать вкладку",
                initial = b.name,
                confirmLabel = "Сохранить",
                label = "Название вкладки",
                onConfirm = {
                    vm.renameBlock(b, it)
                    dialog = TabDialog.NONE
                },
                onDismiss = { dialog = TabDialog.NONE }
            )
        }

        TabDialog.DELETE -> current?.let { b ->
            val count = sections.count { it.blockId == b.id }
            AlertDialog(
                onDismissRequest = { dialog = TabDialog.NONE },
                title = { Text("Удалить вкладку «${b.name}»?") },
                text = {
                    Text(
                        "Разделы из неё ($count) и их кейсы не удаляются: они перейдут во вкладку «Без вкладки»."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = TabDialog.NONE
                        vm.deleteBlock(b)
                    }) {
                        Text("Удалить вкладку", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { dialog = TabDialog.NONE }) { Text("Отмена") } }
            )
        }

        TabDialog.NONE -> {}
    }
}
