package app.pocketos.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.pocketos.data.prefs.AppSettings
import app.pocketos.data.prefs.MotionPreference
import app.pocketos.data.prefs.ThemeMode
import app.pocketos.ui.design.GlassSettings
import app.pocketos.ui.design.LocalGlassSettings
import app.pocketos.ui.design.LocalHaptics
import app.pocketos.ui.design.LocalMotion
import app.pocketos.ui.design.MotionScheme
import app.pocketos.ui.design.rememberHapticManager
import app.pocketos.ui.design.systemPrefersReducedMotion

private val Base = FontFamily.Default

/** Type scale: generous display sizes, tight tracking, readable body text. */
val PocketTypography = Typography(
    displaySmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.02).em),
    headlineLarge = TextStyle(fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.02).em),
    headlineMedium = TextStyle(fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.015).em),
    headlineSmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em),
    titleLarge = TextStyle(fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.01).em),
    titleMedium = TextStyle(fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Base, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Base, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Base, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.01.em),
    labelMedium = TextStyle(fontFamily = Base, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.02.em),
    labelSmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.03.em),
)

@Composable
fun PocketTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val accent = accentPalette(settings.accent)
    val context = LocalContext.current
    val useDynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicScheme = if (useDynamic) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else null

    var pocket = if (dark) darkPocketColors(accent) else lightPocketColors(accent)
    if (dynamicScheme != null) {
        // Dynamic colour only tints the accent; backgrounds keep the PocketOS identity.
        pocket = pocket.copy(
            accent = dynamicScheme.primary,
            accentSoft = dynamicScheme.primary.copy(alpha = 0.8f),
            accentHighlight = dynamicScheme.primaryContainer,
            accentDeep = dynamicScheme.primary,
            onAccent = dynamicScheme.onPrimary,
            ambientA = dynamicScheme.primary.copy(alpha = if (dark) 0.30f else 0.18f),
        )
    }

    val scheme = if (dark) {
        darkColorScheme(
            primary = pocket.accent, onPrimary = pocket.onAccent, primaryContainer = pocket.accentDeep, onPrimaryContainer = Color.White,
            secondary = pocket.accentSoft, onSecondary = Color.White, tertiary = pocket.accentHighlight,
            background = pocket.background, onBackground = pocket.textPrimary,
            surface = pocket.surface, onSurface = pocket.textPrimary, surfaceVariant = pocket.surfaceElevated, onSurfaceVariant = pocket.textSecondary,
            surfaceContainer = pocket.surface, surfaceContainerHigh = pocket.surfaceElevated, surfaceContainerHighest = pocket.surfaceElevated,
            outline = pocket.glassBorder, outlineVariant = pocket.divider, error = pocket.danger, onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = pocket.accent, onPrimary = pocket.onAccent, primaryContainer = pocket.accentHighlight, onPrimaryContainer = pocket.accentDeep,
            secondary = pocket.accentSoft, onSecondary = Color.White, tertiary = pocket.accentHighlight,
            background = pocket.background, onBackground = pocket.textPrimary,
            surface = pocket.surface, onSurface = pocket.textPrimary, surfaceVariant = pocket.backgroundAlt, onSurfaceVariant = pocket.textSecondary,
            surfaceContainer = pocket.surface, surfaceContainerHigh = pocket.surface, surfaceContainerHighest = pocket.backgroundAlt,
            outline = pocket.glassBorder, outlineVariant = pocket.divider, error = pocket.danger, onError = Color.White,
        )
    }

    val reduced = when (settings.motion) {
        MotionPreference.SYSTEM -> systemPrefersReducedMotion()
        MotionPreference.REDUCED -> true
        MotionPreference.FULL -> false
    }
    val motion = remember(reduced) { MotionScheme(reduced) }
    val haptics = rememberHapticManager(settings.haptics)
    val glass = remember(settings.glass, reduced) { GlassSettings(settings.glass, reducedMotion = reduced) }

    CompositionLocalProvider(
        LocalPocketColors provides pocket,
        LocalMotion provides motion,
        LocalHaptics provides haptics,
        LocalGlassSettings provides glass,
    ) {
        MaterialTheme(colorScheme = scheme, typography = PocketTypography, content = content)
    }
}

object Pocket {
    val colors: PocketColors @Composable get() = LocalPocketColors.current
}
