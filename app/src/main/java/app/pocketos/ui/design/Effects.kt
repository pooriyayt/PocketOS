package app.pocketos.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.pocketos.ui.theme.LocalPocketColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Slow, living aurora behind every screen: three soft light pools drifting
 * on long, out-of-phase loops. Only the background layer redraws; with
 * reduced motion the pools stay still.
 */
@Composable
fun Modifier.auroraBackdrop(): Modifier {
    val c = LocalPocketColors.current
    val motion = LocalMotion.current
    val phase = if (motion.reduced) {
        0.25f
    } else {
        val t = rememberInfiniteTransition(label = "aurora")
        val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(26_000, easing = LinearEasing)), label = "auroraPhase")
        p
    }
    return this.drawBehind {
        val w = size.width
        val h = size.height
        val a = phase * 2f * Math.PI.toFloat()
        drawRect(c.background)
        drawRect(Brush.verticalGradient(listOf(c.backgroundAlt, c.background), endY = h * 0.55f))
        // Violet pool, top-start.
        drawRect(
            Brush.radialGradient(
                listOf(c.ambientA, Color.Transparent),
                center = Offset(w * (0.12f + 0.10f * sin(a)), h * (0.02f + 0.04f * cos(a))),
                radius = w * 1.05f,
            )
        )
        // Sky pool, top-end.
        drawRect(
            Brush.radialGradient(
                listOf(c.ambientB, Color.Transparent),
                center = Offset(w * (0.95f + 0.08f * cos(a * 1.3f)), h * (0.16f + 0.05f * sin(a * 1.3f))),
                radius = w * 0.85f,
            )
        )
        // Pink ember low on the page.
        drawRect(
            Brush.radialGradient(
                listOf(c.tones.pink.copy(alpha = if (c.isDark) 0.07f else 0.06f), Color.Transparent),
                center = Offset(w * (0.2f + 0.15f * sin(a * 0.7f)), h * (0.78f + 0.04f * cos(a))),
                radius = w * 0.9f,
            )
        )
    }
}

/**
 * Staggered entrance: content fades up into place the first time it is
 * shown. [index] spaces neighbours out so lists cascade in.
 */
@Composable
fun Modifier.appear(index: Int = 0): Modifier {
    val motion = LocalMotion.current
    if (motion.reduced) return this
    var shown by rememberSaveable { mutableStateOfBoolean(false) }
    val progress = remember { Animatable(if (shown) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!shown) {
            kotlinx.coroutines.delay((index.coerceAtMost(10) * 45L))
            progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            shown = true
        }
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 28.dp.toPx()
        scaleX = 0.97f + 0.03f * p
        scaleY = 0.97f + 0.03f * p
    }
}

private fun mutableStateOfBoolean(value: Boolean) = androidx.compose.runtime.mutableStateOf(value)

/** Counts smoothly between integer values (stats, totals). */
@Composable
fun animatedInt(target: Int): State<Int> {
    val motion = LocalMotion.current
    return animateIntAsState(target, if (motion.reduced) tween(0) else tween(900, easing = FastOutSlowInEasing), label = "count")
}

/**
 * A soft light sweep that glides across a surface every few seconds, used
 * on hero cards to make them feel polished and alive.
 */
@Composable
fun Modifier.sheenSweep(color: Color = Color.White, intensity: Float = 0.16f): Modifier {
    val motion = LocalMotion.current
    if (motion.reduced) return this
    val t = rememberInfiniteTransition(label = "sheen")
    val x by t.animateFloat(
        -0.6f, 1.6f,
        infiniteRepeatable(tween(5200, delayMillis = 1800, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "sheenX",
    )
    return this.drawWithContent {
        drawContent()
        val band = size.width * 0.35f
        val cx = size.width * x
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, color.copy(alpha = intensity), Color.Transparent),
                start = Offset(cx - band, 0f),
                end = Offset(cx + band, size.height),
            )
        )
    }
}

/** Gentle breathing scale/alpha for focal glyphs (empty states, onboarding orb). */
@Composable
fun rememberBreath(): Float {
    val motion = LocalMotion.current
    if (motion.reduced) return 0.5f
    val t = rememberInfiniteTransition(label = "breath")
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathV")
    return v
}
