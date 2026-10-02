package app.pocketos.ui.design

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pocketos.data.prefs.GlassIntensity
import app.pocketos.ui.theme.LocalPocketColors
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * Surface material levels.
 *  - L1: quiet grouped surface (no shadow) for chips, fields and secondary cards.
 *  - L2: primary card surface; in light theme it floats on a soft shadow.
 *  - L3: floating chrome / modal surfaces (real backdrop blur on Android 12+).
 */
enum class GlassLevel(val tintDark: Float, val tintLight: Float, val blurRadius: Dp, val shadow: Dp) {
    L1(0.62f, 0.78f, 0.dp, 0.dp),
    L2(0.94f, 1.00f, 0.dp, 14.dp),
    L3(0.80f, 0.86f, 28.dp, 24.dp),
}

@Immutable
class GlassSettings(val intensity: GlassIntensity, val reducedMotion: Boolean) {
    /** Backdrop blur requires RenderEffect (Android 12+). */
    val blurSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun tintAlpha(level: GlassLevel, dark: Boolean): Float {
        val base = if (dark) level.tintDark else level.tintLight
        val adjust = when (intensity) {
            GlassIntensity.SUBTLE -> 0.08f // more opaque, calmer
            GlassIntensity.BALANCED -> 0f
            GlassIntensity.VIVID -> -0.12f
        }
        return (base + adjust).coerceIn(0.35f, 1f)
    }

    fun blurScale(): Float = when (intensity) {
        GlassIntensity.SUBTLE -> 0.7f
        GlassIntensity.BALANCED -> 1f
        GlassIntensity.VIVID -> 1.3f
    }
}

val LocalGlassSettings = staticCompositionLocalOf { GlassSettings(GlassIntensity.BALANCED, false) }

/** Source of backdrop content for blurred chrome (navigation bar, toolbars, FAB). */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Applies the PocketOS surface material: a near-opaque card fill (or real
 * backdrop blur for floating chrome), a faint top sheen, a hairline border and,
 * in the light theme, a soft ink-tinted shadow. A custom [tint] is laid over
 * the surface as a gentle colour wash rather than replacing it, so tinted
 * cards stay legible in both themes.
 */
@Composable
fun Modifier.glass(
    level: GlassLevel,
    shape: Shape,
    blur: Boolean = level == GlassLevel.L3,
    tint: Color? = null,
    borderWidth: Dp = 1.dp,
): Modifier {
    val colors = LocalPocketColors.current
    val settings = LocalGlassSettings.current
    val haze = LocalHazeState.current
    val base = colors.glassTint
    val alpha = settings.tintAlpha(level, colors.isDark)
    val useBlur = blur && haze != null && settings.blurSupported && level.blurRadius > 0.dp

    var m = this
    if (!colors.isDark && level.shadow > 0.dp) {
        m = m.shadow(level.shadow, shape, clip = false, ambientColor = colors.glassShadow.copy(alpha = 0.08f), spotColor = colors.glassShadow.copy(alpha = 0.14f))
    }
    m = m.clip(shape)
    m = if (useBlur) {
        val style = HazeBlurStyle {
            blurRadius(level.blurRadius * settings.blurScale())
            backgroundColor(colors.background)
            colorEffects(listOf(HazeColorEffect.tint(base.copy(alpha = alpha))))
            noiseFactor(if (colors.isDark) 0.03f else 0.015f)
        }
        m.hazeBlur(HazeInput.Sources(haze!!), style)
    } else {
        // Without real blur, compensate with a more opaque fill so text stays legible.
        val fallback = if (blur) (alpha + 0.16f).coerceAtMost(0.98f) else alpha
        m.background(base.copy(alpha = fallback))
    }
    if (tint != null) {
        m = m.background(
            Brush.linearGradient(
                listOf(tint.copy(alpha = if (colors.isDark) 0.20f else 0.10f), tint.copy(alpha = if (colors.isDark) 0.06f else 0.03f)),
            )
        )
    }
    val sheen = Brush.verticalGradient(
        0f to colors.glassHighlight.copy(alpha = if (colors.isDark) 0.05f else 0.0f),
        0.5f to Color.Transparent,
    )
    val border = Brush.verticalGradient(
        listOf(
            (tint ?: colors.glassBorder).copy(alpha = if (tint != null) 0.32f else colors.glassBorder.alpha * 1.25f),
            colors.glassBorder.copy(alpha = colors.glassBorder.alpha * 0.5f),
        ),
    )
    return m.background(sheen).border(BorderStroke(borderWidth, border), shape)
}

/**
 * Physical press response shared by every tappable glass element: a subtle
 * scale-down, a brightness lift shaped to the element, and a visible focus
 * ring for keyboard / switch-access users.
 */
@Composable
fun Modifier.pressFeedback(interactionSource: MutableInteractionSource, shape: Shape, enabled: Boolean = true): Modifier {
    val motion = LocalMotion.current
    val colors = LocalPocketColors.current
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) motion.pressedScale else 1f, motion.press(), label = "pressScale")
    val lift by animateFloatAsState(if (pressed && enabled) 1f else 0f, motion.press(), label = "pressLift")
    val focusRing = colors.accentSoft
    val highlight = if (colors.isDark) Color.White else colors.accent
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            val outline = shape.createOutline(size, layoutDirection, this)
            if (lift > 0f) drawOutline(outline, highlight.copy(alpha = 0.06f * lift))
            if (focused) drawOutline(outline, focusRing, style = Stroke(width = 2.dp.toPx()))
        }
}
