package app.pocketos.ui.design

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import app.pocketos.data.prefs.HapticsPreference

/** Semantic haptic events. UI code never calls the vibrator directly. */
enum class HapticType(val essential: Boolean) {
    LightTap(false),
    MediumTap(false),
    HeavyTap(true),
    Selection(false),
    Success(true),
    Warning(true),
    Error(true),
    LongPress(true),
    Toggle(false),
    ToggleOff(false),
    Confirm(true),
    Delete(true),
    SwipeThreshold(false),
    SheetOpen(false),
    DragTick(false),
}

/**
 * Centralised haptics built on View.performHapticFeedback, which honours the
 * system "Touch feedback" setting and needs no VIBRATE permission. Newer
 * constants degrade to simpler ones on older Android versions; effects with
 * no reasonable fallback are skipped instead of faked.
 *
 *  - OFF: nothing.
 *  - REDUCED: only essential feedback (success, errors, destructive actions).
 */
@Stable
class HapticManager(private val view: View?, private val preference: HapticsPreference) {

    fun perform(type: HapticType) {
        val v = view ?: return
        if (preference == HapticsPreference.OFF) return
        if (preference == HapticsPreference.REDUCED && !type.essential) return
        val constant = constantFor(type) ?: return
        v.performHapticFeedback(constant)
    }

    private fun constantFor(type: HapticType): Int? {
        val sdk = Build.VERSION.SDK_INT
        return when (type) {
            HapticType.LightTap -> HapticFeedbackConstants.KEYBOARD_TAP
            HapticType.MediumTap -> HapticFeedbackConstants.VIRTUAL_KEY
            HapticType.HeavyTap -> HapticFeedbackConstants.LONG_PRESS
            HapticType.Selection -> if (sdk >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
            HapticType.Success, HapticType.Confirm -> if (sdk >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
            HapticType.Warning -> if (sdk >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.LONG_PRESS
            HapticType.Error -> if (sdk >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
            HapticType.LongPress -> HapticFeedbackConstants.LONG_PRESS
            HapticType.Toggle -> if (sdk >= 34) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.CLOCK_TICK
            HapticType.ToggleOff -> if (sdk >= 34) HapticFeedbackConstants.TOGGLE_OFF else HapticFeedbackConstants.CLOCK_TICK
            HapticType.Delete -> if (sdk >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
            HapticType.SwipeThreshold -> if (sdk >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CLOCK_TICK
            HapticType.SheetOpen -> if (sdk >= 30) HapticFeedbackConstants.GESTURE_START else null
            HapticType.DragTick -> if (sdk >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK
        }
    }
}

val LocalHaptics = staticCompositionLocalOf { HapticManager(null, HapticsPreference.OFF) }

@Composable
fun rememberHapticManager(preference: HapticsPreference): HapticManager {
    val view = LocalView.current
    return remember(view, preference) { HapticManager(view, preference) }
}
