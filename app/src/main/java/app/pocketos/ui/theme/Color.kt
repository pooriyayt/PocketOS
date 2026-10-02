package app.pocketos.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.pocketos.data.prefs.AccentColor

/** Accent palettes: primary, soft (secondary), highlight, and deep shades. */
data class AccentPalette(val primary: Color, val secondary: Color, val highlight: Color, val deep: Color)

fun accentPalette(accent: AccentColor): AccentPalette = when (accent) {
    AccentColor.EMERALD -> AccentPalette(
        primary = Color(0xFF10B981),
        secondary = Color(0xFF34D399),
        highlight = Color(0xFF6EE7B7),
        deep = Color(0xFF059669),
    )
    AccentColor.TEAL -> AccentPalette(
        primary = Color(0xFF14B8A6),
        secondary = Color(0xFF2DD4BF),
        highlight = Color(0xFF99F6E4),
        deep = Color(0xFF0F766E),
    )
    AccentColor.BLUE -> AccentPalette(
        primary = Color(0xFF3B82F6),
        secondary = Color(0xFF60A5FA),
        highlight = Color(0xFFBFDBFE),
        deep = Color(0xFF1D4ED8),
    )
    AccentColor.INDIGO -> AccentPalette(
        primary = Color(0xFF6366F1),
        secondary = Color(0xFF818CF8),
        highlight = Color(0xFFC7D2FE),
        deep = Color(0xFF4338CA),
    )
    AccentColor.VIOLET -> AccentPalette(
        primary = Color(0xFF8B5CF6),
        secondary = Color(0xFFA78BFA),
        highlight = Color(0xFFC4B5FD),
        deep = Color(0xFF6D28D9),
    )
    AccentColor.ROSE -> AccentPalette(
        primary = Color(0xFFF43F5E),
        secondary = Color(0xFFFB7185),
        highlight = Color(0xFFFECDD3),
        deep = Color(0xFFBE123C),
    )
    AccentColor.AMBER -> AccentPalette(
        primary = Color(0xFFF59E0B),
        secondary = Color(0xFFFBBF24),
        highlight = Color(0xFFFDE68A),
        deep = Color(0xFFB45309),
    )
}

/**
 * PocketOS semantic colors: Luxury Emerald / Mint tones over calm neutral dark/light surfaces.
 */
@Immutable
data class PocketColors(
    val isDark: Boolean,
    val background: Color,
    val backgroundAlt: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentHighlight: Color,
    val accentDeep: Color,
    val onAccent: Color,
    val glassTint: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val glassShadow: Color,
    val divider: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val ambientA: Color,
    val ambientB: Color,
    val ambientC: Color,
    val skeleton: Color,
    val scrim: Color,
)

fun darkPocketColors(accent: AccentPalette) = PocketColors(
    isDark = true,
    background = Color(0xFF090E0B),
    backgroundAlt = Color(0xFF0F1713),
    surface = Color(0xFF14201A),
    surfaceElevated = Color(0xFF1B2A22),
    textPrimary = Color(0xFFF2FBF6),
    textSecondary = Color(0xFF98AFA3),
    textTertiary = Color(0xFF61756B),
    accent = accent.primary,
    accentSoft = accent.secondary,
    accentHighlight = accent.highlight,
    accentDeep = accent.deep,
    onAccent = Color(0xFF042F1E),
    glassTint = Color(0xFF132019),
    glassBorder = Color(0x2E34D399),
    glassHighlight = Color(0x24FFFFFF),
    glassShadow = Color(0xFF000000),
    divider = Color(0x18FFFFFF),
    success = Color(0xFF34D399),
    warning = Color(0xFFFBBF24),
    danger = Color(0xFFF87171),
    info = Color(0xFF38BDF8),
    ambientA = accent.primary.copy(alpha = 0.28f),
    ambientB = Color(0xFF047857).copy(alpha = 0.24f),
    ambientC = accent.highlight.copy(alpha = 0.12f),
    skeleton = Color(0x1AFFFFFF),
    scrim = Color(0xB3000000),
)

fun lightPocketColors(accent: AccentPalette) = PocketColors(
    isDark = false,
    background = Color(0xFFF5F9F7),
    backgroundAlt = Color(0xFFEAF2EE),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF0FDF4),
    textPrimary = Color(0xFF0D1D16),
    textSecondary = Color(0xFF435A50),
    textTertiary = Color(0xFF6B8277),
    accent = accent.deep,
    accentSoft = accent.primary,
    accentHighlight = accent.secondary,
    accentDeep = Color(0xFF047857),
    onAccent = Color.White,
    glassTint = Color(0xFFFFFFFF),
    glassBorder = Color(0x1E059669),
    glassHighlight = Color(0xE6FFFFFF),
    glassShadow = Color(0x12047857),
    divider = Color(0x140D1D16),
    success = Color(0xFF059669),
    warning = Color(0xFFD97706),
    danger = Color(0xFFDC2626),
    info = Color(0xFF0284C7),
    ambientA = accent.primary.copy(alpha = 0.18f),
    ambientB = Color(0xFF34D399).copy(alpha = 0.14f),
    ambientC = Color(0xFFD1FAE5).copy(alpha = 0.35f),
    skeleton = Color(0x140D1D16),
    scrim = Color(0x660D1D16),
)

val LocalPocketColors = staticCompositionLocalOf { darkPocketColors(accentPalette(AccentColor.EMERALD)) }
