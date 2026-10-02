package app.pocketos.ui.design

import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * The single source of motion for PocketOS. Every animation in the app uses
 * one of these specs, so durations, springs and reduced-motion behaviour
 * stay consistent. With reduced motion, movement and scaling are removed
 * and only short fades remain.
 */
@Immutable
class MotionScheme(val reduced: Boolean) {
    object Durations {
        const val SHORT = 140
        const val MEDIUM = 240
        const val LONG = 380
    }

    /** Touch response: fast, barely-damped spring (no visible bounce). */
    fun <T> press(): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.85f, stiffness = 900f)

    /** Default UI state changes (toggles, selection, colours). */
    fun <T> standard(): FiniteAnimationSpec<T> =
        if (reduced) tween(Durations.SHORT) else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /** Important moments (task completion, success). Slightly livelier. */
    fun <T> emphasized(): FiniteAnimationSpec<T> =
        if (reduced) tween(Durations.SHORT) else spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow)

    fun <T> fade(): FiniteAnimationSpec<T> = tween(if (reduced) Durations.SHORT else Durations.MEDIUM, easing = FastOutSlowInEasing)

    fun placement(): FiniteAnimationSpec<IntOffset> =
        if (reduced) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    fun size(): FiniteAnimationSpec<IntSize> =
        if (reduced) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /** Pressed scale for cards and buttons. */
    val pressedScale: Float get() = if (reduced) 1f else 0.97f

    // ---- Screen transitions -------------------------------------------

    fun screenEnter(): EnterTransition =
        if (reduced) fadeIn(tween(Durations.SHORT))
        else fadeIn(tween(Durations.MEDIUM)) + slideInVertically(tween(Durations.LONG, easing = FastOutSlowInEasing)) { it / 24 }

    fun screenExit(): ExitTransition = fadeOut(tween(if (reduced) Durations.SHORT else 180))

    fun tabEnter(): EnterTransition = fadeIn(tween(if (reduced) Durations.SHORT else 220))
    fun tabExit(): ExitTransition = fadeOut(tween(if (reduced) Durations.SHORT else 160))

    // ---- Content transitions ------------------------------------------

    fun cardEnter(): EnterTransition =
        if (reduced) fadeIn(tween(Durations.SHORT))
        else fadeIn(tween(Durations.MEDIUM)) + slideInVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow)) { it / 6 }

    fun expandEnter(): EnterTransition =
        if (reduced) fadeIn(tween(Durations.SHORT)) else fadeIn(tween(Durations.MEDIUM)) + expandVertically(size())

    fun expandExit(): ExitTransition =
        if (reduced) fadeOut(tween(Durations.SHORT)) else fadeOut(tween(160)) + shrinkVertically(size())

    fun popEnter(): EnterTransition =
        if (reduced) fadeIn(tween(Durations.SHORT)) else fadeIn(tween(Durations.SHORT)) + scaleIn(emphasized(), initialScale = 0.85f)

    fun popExit(): ExitTransition =
        if (reduced) fadeOut(tween(Durations.SHORT)) else fadeOut(tween(Durations.SHORT)) + scaleOut(tween(Durations.SHORT), targetScale = 0.9f)

    fun bannerEnter(): EnterTransition =
        if (reduced) fadeIn(tween(Durations.SHORT)) else fadeIn(tween(Durations.MEDIUM)) + slideInVertically(standard()) { -it / 2 }

    fun bannerExit(): ExitTransition =
        if (reduced) fadeOut(tween(Durations.SHORT)) else fadeOut(tween(160)) + slideOutVertically(tween(Durations.MEDIUM)) { -it / 2 }

    fun <T> infinite(): AnimationSpec<T> = tween(1200, easing = FastOutSlowInEasing)
}

val LocalMotion = staticCompositionLocalOf { MotionScheme(reduced = false) }

/** True when the system "Remove animations" accessibility setting is on. */
@Composable
fun systemPrefersReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
}
