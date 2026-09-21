package com.example.testcases.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.testcases.data.Status

private val LightColors = lightColorScheme(
    primary = Color(0xFF3648C9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E5FF),
    onPrimaryContainer = Color(0xFF10195E),
    secondaryContainer = Color(0xFFE1E5FF),
    onSecondaryContainer = Color(0xFF10195E),
    background = Color(0xFFF3F5FA),
    onBackground = Color(0xFF161A26),
    surface = Color.White,
    onSurface = Color(0xFF161A26),
    surfaceVariant = Color(0xFFE8EBF3),
    onSurfaceVariant = Color(0xFF585E72),
    outline = Color(0xFF8B91A5),
    outlineVariant = Color(0xFFDADEE9),
    error = Color(0xFFC42B3E),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7C0FF),
    onPrimary = Color(0xFF0F1A6B),
    primaryContainer = Color(0xFF2A3596),
    onPrimaryContainer = Color(0xFFDFE3FF),
    secondaryContainer = Color(0xFF2A3596),
    onSecondaryContainer = Color(0xFFDFE3FF),
    background = Color(0xFF0F1220),
    onBackground = Color(0xFFE4E6F0),
    surface = Color(0xFF181C2C),
    onSurface = Color(0xFFE4E6F0),
    surfaceVariant = Color(0xFF232838),
    onSurfaceVariant = Color(0xFFA9AEC2),
    outline = Color(0xFF6E748A),
    outlineVariant = Color(0xFF2E3448),
    error = Color(0xFFFF7A88),
    onError = Color(0xFF3B0810)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 30.sp, lineHeight = 36.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp
    ),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(
        fontSize = 14.sp, lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/** Выбор темы пользователем. */
enum class ThemeMode(val label: String) {
    SYSTEM("Как в системе"),
    LIGHT("Светлая"),
    DARK("Тёмная")
}

/** Включена ли сейчас тёмная тема (с учётом ручного выбора). */
val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun TestCasesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content
        )
    }
}

/** Цвет статуса — главный смысловой акцент всего приложения. */
@Composable
fun Status.color(): Color {
    val dark = LocalDarkTheme.current
    return when (this) {
        Status.PASSED -> if (dark) Color(0xFF4CD08C) else Color(0xFF12804B)
        Status.FAILED -> if (dark) Color(0xFFFF7A88) else Color(0xFFC42B3E)
        Status.BLOCKED -> if (dark) Color(0xFFF2B84B) else Color(0xFFB9740A)
        Status.NOT_RUN -> if (dark) Color(0xFF9AA0B4) else Color(0xFF656B7E)
    }
}
