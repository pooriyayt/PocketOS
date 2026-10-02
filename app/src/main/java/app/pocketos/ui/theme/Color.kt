package app.pocketos.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.pocketos.data.prefs.AccentColor

/**
 * Accent palettes: primary, soft (secondary), highlight and deep shades, plus
 * the second stop of the signature gradient used on hero surfaces and the FAB.
 */
data class AccentPalette(
    val primary: Color,
    val secondary: Color,
    val highlight: Color,
    val deep: Color,
    val gradientEnd: Color,
)

fun accentPalette(accent: AccentColor): AccentPalette = when (accent) {
    AccentColor.AURORA -> AccentPalette(
        primary = Color(0xFF7B6CFF),
        secondary = Color(0xFF9D90FF),
        highlight = Color(0xFFCBC4FF),
        deep = Color(0xFF5A48F0),
        gradientEnd = Color(0xFF3FA9F5),
    )
    AccentColor.EMERALD -> AccentPalette(
        primary = Color(0xFF10B981),
        secondary = Color(0xFF34D399),
        highlight = Color(0xFFA7F3D0),
        deep = Color(0xFF047857),
        gradientEnd = Color(0xFF0EA5E9),
    )
    AccentColor.TEAL -> AccentPalette(
        primary = Color(0xFF14B8A6),
        secondary = Color(0xFF2DD4BF),
        highlight = Color(0xFF99F6E4),
        deep = Color(0xFF0F766E),
        gradientEnd = Color(0xFF6366F1),
    )
    AccentColor.BLUE -> AccentPalette(
        primary = Color(0xFF3B82F6),
        secondary = Color(0xFF60A5FA),
        highlight = Color(0xFFBFDBFE),
        deep = Color(0xFF1D4ED8),
        gradientEnd = Color(0xFF06B6D4),
    )
    AccentColor.INDIGO -> AccentPalette(
        primary = Color(0xFF6366F1),
        secondary = Color(0xFF818CF8),
        highlight = Color(0xFFC7D2FE),
        deep = Color(0xFF4338CA),
        gradientEnd = Color(0xFFA855F7),
    )
    AccentColor.VIOLET -> AccentPalette(
        primary = Color(0xFF8B5CF6),
        secondary = Color(0xFFA78BFA),
        highlight = Color(0xFFDDD6FE),
        deep = Color(0xFF6D28D9),
        gradientEnd = Color(0xFFEC4899),
    )
    AccentColor.ROSE -> AccentPalette(
        primary = Color(0xFFF43F5E),
        secondary = Color(0xFFFB7185),
        highlight = Color(0xFFFECDD3),
        deep = Color(0xFFBE123C),
        gradientEnd = Color(0xFFF59E0B),
    )
    AccentColor.AMBER -> AccentPalette(
        primary = Color(0xFFF59E0B),
        secondary = Color(0xFFFBBF24),
        highlight = Color(0xFFFDE68A),
        deep = Color(0xFFB45309),
        gradientEnd = Color(0xFFEF4444),
    )
}

/**
 * Fixed, theme-aware "tones" for iconography. Each kind of thing in the app
 * gets its own colour so screens read at a glance instead of being one hue.
 */
@Immutable
data class PocketTones(
    val violet: Color,
    val blue: Color,
    val cyan: Color,
    val green: Color,
    val amber: Color,
    val orange: Color,
    val pink: Color,
    val red: Color,
)

private val darkTones = PocketTones(
    violet = Color(0xFFA394FF),
    blue = Color(0xFF60A5FA),
    cyan = Color(0xFF22D3EE),
    green = Color(0xFF34D399),
    amber = Color(0xFFFBBF24),
    orange = Color(0xFFFB923C),
    pink = Color(0xFFF472B6),
    red = Color(0xFFFB7185),
)

private val lightTones = PocketTones(
    violet = Color(0xFF6A55F5),
    blue = Color(0xFF2563EB),
    cyan = Color(0xFF0891B2),
    green = Color(0xFF059669),
    amber = Color(0xFFD97706),
    orange = Color(0xFFEA580C),
    pink = Color(0xFFDB2777),
    red = Color(0xFFE11D48),
)

/**
 * PocketOS semantic colors: "Aurora" — deep ink surfaces with a violet → sky
 * signature gradient (dark), or airy porcelain with crisp white cards (light).
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
    val accentGradientEnd: Color,
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
    val tones: PocketTones,
) {
    /** Signature gradient for hero cards, the FAB and primary buttons. */
    val brandGradient: Brush get() = Brush.linearGradient(listOf(accent, accentGradientEnd))
}

fun darkPocketColors(accent: AccentPalette) = PocketColors(
    isDark = true,
    background = Color(0xFF0A0B14),
    backgroundAlt = Color(0xFF0F1120),
    surface = Color(0xFF151829),
    surfaceElevated = Color(0xFF1D2136),
    textPrimary = Color(0xFFF5F6FF),
    textSecondary = Color(0xFFA6AAC6),
    textTertiary = Color(0xFF6E7395),
    accent = accent.primary,
    accentSoft = accent.secondary,
    accentHighlight = accent.highlight,
    accentDeep = accent.deep,
    accentGradientEnd = accent.gradientEnd,
    onAccent = Color.White,
    glassTint = Color(0xFF181C2F),
    glassBorder = Color(0x1FFFFFFF),
    glassHighlight = Color(0x1FFFFFFF),
    glassShadow = Color(0xFF000000),
    divider = Color(0x14FFFFFF),
    success = Color(0xFF34D399),
    warning = Color(0xFFFBBF24),
    danger = Color(0xFFFB7185),
    info = Color(0xFF38BDF8),
    ambientA = accent.primary.copy(alpha = 0.22f),
    ambientB = accent.gradientEnd.copy(alpha = 0.14f),
    ambientC = accent.highlight.copy(alpha = 0.05f),
    skeleton = Color(0x14FFFFFF),
    scrim = Color(0xB3050610),
    tones = darkTones,
)

fun lightPocketColors(accent: AccentPalette) = PocketColors(
    isDark = false,
    background = Color(0xFFF5F6FB),
    backgroundAlt = Color(0xFFECEEF8),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF13152B),
    textSecondary = Color(0xFF545A7A),
    textTertiary = Color(0xFF8C91AE),
    accent = accent.deep,
    accentSoft = accent.primary,
    accentHighlight = accent.primary,
    accentDeep = accent.deep,
    accentGradientEnd = accent.gradientEnd,
    onAccent = Color.White,
    glassTint = Color(0xFFFFFFFF),
    glassBorder = Color(0x1413152B),
    glassHighlight = Color(0xFFFFFFFF),
    glassShadow = Color(0xFF2A2F5C),
    divider = Color(0x1213152B),
    success = Color(0xFF059669),
    warning = Color(0xFFD97706),
    danger = Color(0xFFE11D48),
    info = Color(0xFF0284C7),
    ambientA = accent.primary.copy(alpha = 0.14f),
    ambientB = accent.gradientEnd.copy(alpha = 0.10f),
    ambientC = accent.highlight.copy(alpha = 0.18f),
    skeleton = Color(0x1213152B),
    scrim = Color(0x6613152B),
    tones = lightTones,
)

val LocalPocketColors = staticCompositionLocalOf { darkPocketColors(accentPalette(AccentColor.AURORA)) }
