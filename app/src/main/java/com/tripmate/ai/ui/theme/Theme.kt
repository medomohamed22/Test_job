package com.tripmate.ai.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tripmate.ai.data.prefs.ThemeMode

private val LightColors = lightColorScheme(
    primary = TealDeep, onPrimary = Color.White, primaryContainer = TealContainerLight, onPrimaryContainer = Color(0xFF00201D),
    secondary = CoralDeep, onSecondary = Color.White, secondaryContainer = CoralContainerLight, onSecondaryContainer = Color(0xFF3B0A00),
    tertiary = GoldDeep, onTertiary = Color.White, tertiaryContainer = GoldContainerLight, onTertiaryContainer = Color(0xFF261A00),
    background = Ivory, onBackground = Navy, surface = SurfaceLight, onSurface = Navy,
    surfaceVariant = SurfaceVariantLight, onSurfaceVariant = MutedLight, outline = OutlineLight, outlineVariant = Color(0xFFE3DDD3),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFCFBF8), surfaceContainer = Color(0xFFF6F3EE),
    surfaceContainerHigh = Color(0xFFF0ECE5), surfaceContainerHighest = Color(0xFFEAE5DC),
    error = Color(0xFFB3261E), onError = Color.White, inverseSurface = Navy, inverseOnSurface = Ivory, inversePrimary = TealGlow,
)

private val DarkColors = darkColorScheme(
    primary = TealGlow, onPrimary = Color(0xFF00201D), primaryContainer = TealContainerDark, onPrimaryContainer = Color(0xFFB8F3EC),
    secondary = CoralSoft, onSecondary = Color(0xFF3B0A00), secondaryContainer = CoralContainerDark, onSecondaryContainer = Color(0xFFFFDBD1),
    tertiary = GoldSoft, onTertiary = Color(0xFF261A00), tertiaryContainer = GoldContainerDark, onTertiaryContainer = Color(0xFFFBEAC3),
    background = NavyDeep, onBackground = OnDark, surface = Navy, onSurface = OnDark,
    surfaceVariant = NavySurfaceHigh, onSurfaceVariant = MutedDark, outline = OutlineDark, outlineVariant = Color(0xFF243A52),
    surfaceContainerLowest = NavyDeep, surfaceContainerLow = Color(0xFF0F1F31), surfaceContainer = NavySurface,
    surfaceContainerHigh = NavySurfaceHigh, surfaceContainerHighest = Color(0xFF223B57),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005), inverseSurface = Ivory, inverseOnSurface = Navy, inversePrimary = TealDeep,
)

@Immutable
data class ExtendedColors(val success: Color, val warning: Color, val heroStart: Color, val heroEnd: Color, val isDark: Boolean)

val LocalExtendedColors = staticCompositionLocalOf { ExtendedColors(SuccessLight, WarningLight, Teal, Navy, false) }

/** True when the user or the system asked for reduced motion. Animations check this. */
val LocalReducedMotion = staticCompositionLocalOf { false }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TripMateTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, reducedMotion: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (themeMode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val context = LocalContext.current
    val systemReduced = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
    val ext = if (dark) ExtendedColors(SuccessDark, WarningDark, Color(0xFF0B5E58), NavyDeep, true)
    else ExtendedColors(SuccessLight, WarningLight, Teal, Navy, false)
    CompositionLocalProvider(LocalExtendedColors provides ext, LocalReducedMotion provides (reducedMotion || systemReduced)) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
