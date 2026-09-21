package com.example.testcases.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.testcases.data.Status
import com.example.testcases.data.TestCase
import com.example.testcases.ui.theme.LocalDarkTheme
import com.example.testcases.ui.theme.color

/**
 * Прогон: кейсы идут по очереди, выбор статуса сразу открывает следующий кейс.
 * Стартует с первого «Не запущен» в выбранном разделе.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunScreen(
    vm: TestCaseViewModel,
    sectionId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val all by vm.all.collectAsStateWithLifecycle()
    val queue = remember(all, sectionId) { all.orEmpty().inSection(sectionId) }
    var index by rememberSaveable { mutableStateOf(-1) }

    // Пока идёт прогон, экран не гаснет
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(all != null) {
        if (all != null && index < 0) {
            val firstUnrun = queue.indexOfFirst { it.status == Status.NOT_RUN }
            index = if (firstUnrun < 0) 0 else firstUnrun
        }
    }

    val total = queue.size
    val current = queue.getOrNull(index)
    val finished = total > 0 && index >= total

    fun mark(status: Status) {
        val tc = queue.getOrNull(index) ?: return
        vm.setStatus(tc, status)
        index += 1
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Прогон") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (current != null) {
                        IconButton(onClick = { onEdit(current.id) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Редактировать кейс")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (current != null) {
                RunActions(
                    canGoBack = index > 0,
                    onPass = { mark(Status.PASSED) },
                    onFail = { mark(Status.FAILED) },
                    onBlock = { mark(Status.BLOCKED) },
                    onPrev = { index -= 1 },
                    onSkip = { index += 1 }
                )
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                all == null || index < 0 -> Box(Modifier.fillMaxSize())
                total == 0 -> RunEmpty(onBack)
                finished -> RunSummary(
                    queue = queue,
                    onRestart = { index = 0 },
                    onUnrun = { index = queue.indexOfFirst { it.status == Status.NOT_RUN }.coerceAtLeast(0) },
                    onBack = onBack
                )
                else -> {
                    RunProgress(index, queue)
                    Crossfade(
                        targetState = index,
                        modifier = Modifier.weight(1f),
                        label = "run-case"
                    ) { i ->
                        val tc = queue.getOrNull(i)
                        if (tc != null) RunCase(tc)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- top progress

@Composable
private fun RunProgress(index: Int, queue: List<TestCase>) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Кейс ${index + 1} из ${queue.size}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StatusBar(queue, height = 6.dp)
    }
}

// ---------------------------------------------------------------- current case

@Composable
private fun RunCase(tc: TestCase) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 27.sp)
        )
        StatusChip(tc.status)

        if (tc.description.isNotBlank()) {
            Text(
                tc.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (tc.preconditions.isNotBlank()) {
            RunBlock("Предусловия") {
                Text(tc.preconditions, style = MaterialTheme.typography.bodyLarge)
            }
        }

        val steps = tc.stepList
        if (steps.isNotEmpty()) {
            RunBlock("Шаги") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    steps.forEachIndexed { i, step ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${i + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                step,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        if (tc.expectedResult.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Ожидаемый результат",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        tc.expectedResult,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RunBlock(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@Composable
private fun StatusChip(status: Status) {
    val color = status.color()
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(status.label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

// ---------------------------------------------------------------- bottom actions

@Composable
private fun RunActions(
    canGoBack: Boolean,
    onPass: () -> Unit,
    onFail: () -> Unit,
    onBlock: () -> Unit,
    onPrev: () -> Unit,
    onSkip: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusButton(Status.PASSED, "Пройден", onPass, Modifier.fillMaxWidth().height(56.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusButton(Status.FAILED, "Провален", onFail, Modifier.weight(1f).height(48.dp))
                StatusButton(Status.BLOCKED, "Заблокирован", onBlock, Modifier.weight(1f).height(48.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onPrev, enabled = canGoBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Назад")
                }
                TextButton(onClick = onSkip) {
                    Text("Пропустить")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusButton(status: Status, label: String, onClick: () -> Unit, modifier: Modifier) {
    val onColor = if (LocalDarkTheme.current) Color(0xFF10131F) else Color.White
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = status.color(),
            contentColor = onColor
        )
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

// ---------------------------------------------------------------- finish / empty

@Composable
private fun RunEmpty(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Здесь нет кейсов для прогона", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onBack) { Text("Вернуться") }
    }
}

@Composable
private fun RunSummary(
    queue: List<TestCase>,
    onRestart: () -> Unit,
    onUnrun: () -> Unit,
    onBack: () -> Unit
) {
    val order = listOf(Status.PASSED, Status.FAILED, Status.BLOCKED, Status.NOT_RUN)
    val unrun = queue.count { it.status == Status.NOT_RUN }
    val passed = queue.count { it.status == Status.PASSED }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Прогон завершён", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Пройдено $passed из ${queue.size}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StatusBar(queue)

        order.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(s.color()))
                Spacer(Modifier.width(10.dp))
                Text(s.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("${queue.count { it.status == s }}", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.height(8.dp))
        if (unrun > 0) {
            Button(
                onClick = onUnrun,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("К непройденным ($unrun)") }
        }
        OutlinedButton(
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Пройти сначала") }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("К списку") }
    }
}
