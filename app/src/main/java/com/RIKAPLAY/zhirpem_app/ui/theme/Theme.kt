package com.RIKAPLAY.zhirpem_app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.RIKAPLAY.zhirpem_app.AppThemeMode
import com.RIKAPLAY.zhirpem_app.ThemeManager

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8DE3B5),
    background = Color(0xFF121413),
    surface = Color(0xFF1A1D1C),
    onBackground = Color(0xFFE2E3E1),
    onSurface = Color(0xFFE2E3E1),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006B44),
    background = Color(0xFFF7FBF8),
    surface = Color(0xFFEEF2EE),
    onBackground = Color(0xFF1A1D1C),
    onSurface = Color(0xFF1A1D1C),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6)
)

private fun lerpScheme(a: ColorScheme, b: ColorScheme, f: Float): ColorScheme = ColorScheme(
        primary = lerp(a.primary, b.primary, f),
        onPrimary = lerp(a.onPrimary, b.onPrimary, f),
        primaryContainer = lerp(a.primaryContainer, b.primaryContainer, f),
        onPrimaryContainer = lerp(a.onPrimaryContainer, b.onPrimaryContainer, f),
        inversePrimary = lerp(a.inversePrimary, b.inversePrimary, f),
        secondary = lerp(a.secondary, b.secondary, f),
        onSecondary = lerp(a.onSecondary, b.onSecondary, f),
        secondaryContainer = lerp(a.secondaryContainer, b.secondaryContainer, f),
        onSecondaryContainer = lerp(a.onSecondaryContainer, b.onSecondaryContainer, f),
        tertiary = lerp(a.tertiary, b.tertiary, f),
        onTertiary = lerp(a.onTertiary, b.onTertiary, f),
        tertiaryContainer = lerp(a.tertiaryContainer, b.tertiaryContainer, f),
        onTertiaryContainer = lerp(a.onTertiaryContainer, b.onTertiaryContainer, f),
        background = lerp(a.background, b.background, f),
        onBackground = lerp(a.onBackground, b.onBackground, f),
        surface = lerp(a.surface, b.surface, f),
        onSurface = lerp(a.onSurface, b.onSurface, f),
        surfaceVariant = lerp(a.surfaceVariant, b.surfaceVariant, f),
        onSurfaceVariant = lerp(a.onSurfaceVariant, b.onSurfaceVariant, f),
        surfaceTint = lerp(a.surfaceTint, b.surfaceTint, f),
        inverseSurface = lerp(a.inverseSurface, b.inverseSurface, f),
        inverseOnSurface = lerp(a.inverseOnSurface, b.inverseOnSurface, f),
        error = lerp(a.error, b.error, f),
        onError = lerp(a.onError, b.onError, f),
        errorContainer = lerp(a.errorContainer, b.errorContainer, f),
        onErrorContainer = lerp(a.onErrorContainer, b.onErrorContainer, f),
        outline = lerp(a.outline, b.outline, f),
        outlineVariant = lerp(a.outlineVariant, b.outlineVariant, f),
        scrim = lerp(a.scrim, b.scrim, f),
        surfaceBright = lerp(a.surfaceBright, b.surfaceBright, f),
        surfaceDim = lerp(a.surfaceDim, b.surfaceDim, f),
        surfaceContainer = lerp(a.surfaceContainer, b.surfaceContainer, f),
        surfaceContainerHigh = lerp(a.surfaceContainerHigh, b.surfaceContainerHigh, f),
        surfaceContainerHighest = lerp(a.surfaceContainerHighest, b.surfaceContainerHighest, f),
        surfaceContainerLow = lerp(a.surfaceContainerLow, b.surfaceContainerLow, f),
        surfaceContainerLowest = lerp(a.surfaceContainerLowest, b.surfaceContainerLowest, f),
        primaryFixed = lerp(a.primaryFixed, b.primaryFixed, f),
        onPrimaryFixed = lerp(a.onPrimaryFixed, b.onPrimaryFixed, f),
        primaryFixedDim = lerp(a.primaryFixedDim, b.primaryFixedDim, f),
        onPrimaryFixedVariant = lerp(a.onPrimaryFixedVariant, b.onPrimaryFixedVariant, f),
        secondaryFixed = lerp(a.secondaryFixed, b.secondaryFixed, f),
        onSecondaryFixed = lerp(a.onSecondaryFixed, b.onSecondaryFixed, f),
        secondaryFixedDim = lerp(a.secondaryFixedDim, b.secondaryFixedDim, f),
        onSecondaryFixedVariant = lerp(a.onSecondaryFixedVariant, b.onSecondaryFixedVariant, f),
        tertiaryFixed = lerp(a.tertiaryFixed, b.tertiaryFixed, f),
        onTertiaryFixed = lerp(a.onTertiaryFixed, b.onTertiaryFixed, f),
        tertiaryFixedDim = lerp(a.tertiaryFixedDim, b.tertiaryFixedDim, f),
        onTertiaryFixedVariant = lerp(a.onTertiaryFixedVariant, b.onTertiaryFixedVariant, f)
)

/**
 * Плавная смена темы. Раньше здесь работали десятки отдельных анимаций цвета, а схема пересоздавалась
 * при каждой перекомпоновке. Теперь одна анимация, и только в момент смены темы.
 */
@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(target) {
        if (target !== to) {
            from = if (progress.value >= 1f) to else lerpScheme(from, to, progress.value)
            to = target
            progress.snapTo(0f)
            progress.animateTo(1f, tween(300))
        }
    }

    return if (progress.value >= 1f) to else lerpScheme(from, to, progress.value)
}

@Composable
fun Zhirpem_appTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val themeManager = remember { ThemeManager(context) }
    val isSystemDark = isSystemInDarkTheme()
    
    // Схема считается заново только при смене темы, а не при каждой перекомпоновке
    val targetColorScheme = remember(themeMode, isSystemDark, themeManager.themeType, themeManager.getCustomColorObj()) {
    when (themeManager.themeType) {
        ThemeManager.TYPE_MY_LIGHT -> {
            themeManager.generateColorScheme(themeManager.getCustomColorObj(), false)
        }
        ThemeManager.TYPE_MY_DARK -> {
            themeManager.generateColorScheme(themeManager.getCustomColorObj(), true)
        }
        else -> {
            val darkTheme = when (themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK, AppThemeMode.AMOLED -> true
                AppThemeMode.SYSTEM -> isSystemDark
                AppThemeMode.MATERIAL_YOU_LIGHT -> false
                AppThemeMode.MATERIAL_YOU_DARK -> true
            }

            when {
                themeMode == AppThemeMode.MATERIAL_YOU_LIGHT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    dynamicLightColorScheme(context)
                }
                themeMode == AppThemeMode.MATERIAL_YOU_DARK && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    dynamicDarkColorScheme(context)
                }
                themeMode == AppThemeMode.AMOLED -> darkColorScheme(
                    primary = Color(0xFF8DE3B5),
                    background = Color(0xFF000000),
                    surface = Color(0xFF0A0A0A)
                )
                darkTheme -> DarkColorScheme
                else -> LightColorScheme
            }
        }
    }

    }

    val colorScheme = animateColorScheme(targetColorScheme)

    // Определяем darkTheme для статус-бара
    val darkThemeForStatus = when (themeManager.themeType) {
        ThemeManager.TYPE_MY_LIGHT -> false
        ThemeManager.TYPE_MY_DARK -> true
        else -> when (themeMode) {
            AppThemeMode.LIGHT -> false
            AppThemeMode.DARK, AppThemeMode.AMOLED -> true
            AppThemeMode.SYSTEM -> isSystemDark
            AppThemeMode.MATERIAL_YOU_LIGHT -> false
            AppThemeMode.MATERIAL_YOU_DARK -> true
        }
    }

    // Покраска статус-бара
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkThemeForStatus
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
