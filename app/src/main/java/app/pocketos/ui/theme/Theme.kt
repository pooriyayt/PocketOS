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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import app.pocketos.R
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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

/** Vazirmatn: one family with first-class Persian and Latin glyphs, so both languages look designed. */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
    Font(R.font.vazirmatn_extrabold, FontWeight.ExtraBold),
)

private val Base = Vazirmatn

/**
 * Type scale. Line heights are generous because Persian glyphs need room
 * above and below; tracking stays at zero so cursive Persian joins cleanly.
 */
val PocketTypography = Typography(
    displayLarge = TextStyle(fontFamily = Base, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, lineHeight = 60.sp),
    displayMedium = TextStyle(fontFamily = Base, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 46.sp),
    headlineLarge = TextStyle(fontFamily = Base, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 42.sp),
    headlineMedium = TextStyle(fontFamily = Base, fontWeight = FontWeight.ExtraBold, fontSize = 27.sp, lineHeight = 38.sp),
    headlineSmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Base, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Base, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = Base, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = Base, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Base, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
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
            accentGradientEnd = dynamicScheme.tertiary,
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

    // Status / navigation bar icons follow the app theme, not the system one.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

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
