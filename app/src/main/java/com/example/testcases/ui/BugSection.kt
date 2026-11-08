package com.example.testcases.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.testcases.data.BugFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_SCREENS = 10

/**
 * Блок «Баг» в форме кейса: переключатель, описание, скриншоты и отправка в Telegram.
 * Скриншоты копируются в папку приложения, в кейсе хранятся только имена файлов.
 */
@Composable
internal fun BugSection(
    hasBug: Boolean,
    onHasBugChange: (Boolean) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    screens: List<String>,
    onScreensChange: (List<String>) -> Unit,
    onSend: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentScreens by rememberUpdatedState(screens)
    val currentOnChange by rememberUpdatedState(onScreensChange)
    var viewing by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_SCREENS)
    ) { uris ->
        val room = MAX_SCREENS - currentScreens.size
        if (uris.isNotEmpty()) {
            if (room <= 0) {
                Toast.makeText(context, "Максимум $MAX_SCREENS скриншотов", Toast.LENGTH_SHORT).show()
            } else {
                val chosen = uris.take(room)
                scope.launch {
                    val added = withContext(Dispatchers.IO) {
                        chosen.mapNotNull { BugFiles.import(context, it) }
                    }
                    if (added.isNotEmpty()) currentOnChange(currentScreens + added)

                    if (uris.size > room) {
                        Toast.makeText(context, "Добавлено не больше $MAX_SCREENS скриншотов", Toast.LENGTH_SHORT).show()
                    } else if (added.size < chosen.size) {
                        Toast.makeText(
                            context,
                            "Не удалось добавить файлов: ${chosen.size - added.size}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.BugReport,
                    contentDescription = null,
                    tint = if (hasBug) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("В кейсе найден баг", style = MaterialTheme.typography.titleSmall)
                    if (!hasBug) {
                        Text(
                            "Опишите, что не так, приложите скриншоты и отправьте в Telegram",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(checked = hasBug, onCheckedChange = onHasBugChange)
            }

            if (hasBug) {
                AppField(
                    value = description,
                    onChange = onDescriptionChange,
                    label = "Что не так (фактический результат)",
                    minLines = 3
                )

                Text(
                    "Скриншоты",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    screens.forEach { name ->
                        key(name) {
                            Thumb(
                                name = name,
                                onOpen = { viewing = name },
                                onRemove = { onScreensChange(screens.filterNot { it == name }) }
                            )
                        }
                    }
                    if (screens.size < MAX_SCREENS) {
                        AddTile(
                            onClick = {
                                picker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                    }
                }

                Button(
                    onClick = onSend,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Отправить в Telegram")
                }
            }
        }
    }

    viewing?.let { name ->
        ScreenshotViewer(name = name, onDismiss = { viewing = null })
    }
}

// ---------------------------------------------------------------- миниатюры

private sealed interface ThumbState {
    data object Loading : ThumbState
    data object Failed : ThumbState
    data class Ready(val bitmap: ImageBitmap) : ThumbState
}

private suspend fun loadImage(context: Context, name: String, maxSide: Int): ThumbState =
    withContext(Dispatchers.IO) {
        BugFiles.decode(context, name, maxSide)?.let { ThumbState.Ready(it.asImageBitmap()) }
            ?: ThumbState.Failed
    }

@Composable
private fun Thumb(name: String, onOpen: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    val state by produceState<ThumbState>(ThumbState.Loading, name) {
        value = loadImage(context, name, 320)
    }

    Box(Modifier.size(88.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center
        ) {
            when (val s = state) {
                is ThumbState.Ready -> Image(
                    bitmap = s.bitmap,
                    contentDescription = "Скриншот",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                ThumbState.Loading -> {}
                ThumbState.Failed -> Icon(
                    Icons.Rounded.BrokenImage,
                    contentDescription = "Файл недоступен",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Убрать скриншот",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.AddPhotoAlternate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Добавить",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Скриншот на весь экран; закрывается нажатием в любое место. */
@Composable
private fun ScreenshotViewer(name: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state by produceState<ThumbState>(ThumbState.Loading, name) {
        value = loadImage(context, name, 1600)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            when (val s = state) {
                is ThumbState.Ready -> Image(
                    bitmap = s.bitmap,
                    contentDescription = "Скриншот",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
                ThumbState.Loading -> CircularProgressIndicator(color = Color.White)
                ThumbState.Failed -> Text("Не удалось открыть файл", color = Color.White)
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Закрыть", tint = Color.White)
            }
        }
    }
}
