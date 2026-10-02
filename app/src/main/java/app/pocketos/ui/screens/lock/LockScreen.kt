package app.pocketos.ui.screens.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import app.pocketos.R
import app.pocketos.core.security.BiometricUnlock
import app.pocketos.core.security.PinResult
import app.pocketos.ui.LocalAppContainer
import app.pocketos.ui.design.ButtonStyle
import app.pocketos.ui.design.GlassBottomSheet
import app.pocketos.ui.design.GlassDialog
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.GlassTextField
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.LocalHaptics
import app.pocketos.ui.design.LocalMotion
import app.pocketos.ui.design.PocketButton
import app.pocketos.ui.design.glass
import app.pocketos.ui.design.pressFeedback
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Full-screen lock. Protected content is not composed while this is shown.
 * Ads are never shown here.
 */
@Composable
fun LockScreen() {
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsState(initial = container.latestSettings)
    val c = LocalPocketColors.current
    val haptics = LocalHaptics.current
    val motion = LocalMotion.current
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var lockedUntil by remember { mutableLongStateOf(container.appLock.lockedOutUntil() ?: 0L) }
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    var recovery by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val biometricAvailable = settings.biometricUnlock && activity != null &&
        container.biometric.availability(activity) == BiometricUnlock.Availability.AVAILABLE

    val wrongTemplate = stringResource(R.string.pin_wrong)
    val biometricTitle = stringResource(R.string.unlock_pocketos)
    val biometricSubtitle = stringResource(R.string.biometric_subtitle)
    val usePinLabel = stringResource(R.string.use_pin)
    val biometricChanged = stringResource(R.string.biometric_changed)

    fun biometric() {
        val act = activity ?: return
        container.biometric.authenticate(act, biometricTitle, biometricSubtitle, usePinLabel) { outcome ->
            when (outcome) {
                BiometricUnlock.Outcome.Success -> container.appLock.unlockWithVerifiedBiometric()
                BiometricUnlock.Outcome.KeyInvalidated -> {
                    message = biometricChanged
                    scope.launch { container.settings.update { it.copy(biometricUnlock = false) } }
                }
                is BiometricUnlock.Outcome.Error -> message = outcome.message.toString()
                else -> Unit
            }
        }
    }

    LaunchedEffect(biometricAvailable) { if (biometricAvailable) biometric() }
    LaunchedEffect(lockedUntil) {
        while (lockedUntil > android.os.SystemClock.elapsedRealtime()) {
            now = android.os.SystemClock.elapsedRealtime()
            delay(1000)
        }
        now = android.os.SystemClock.elapsedRealtime()
    }
    val lockedOut = lockedUntil > now

    fun submit() {
        if (pin.length < 4 || lockedOut) return
        when (val result = container.appLock.unlockWithPin(pin)) {
            PinResult.Success -> haptics.perform(HapticType.Success)
            is PinResult.Wrong -> {
                haptics.perform(HapticType.Error)
                message = wrongTemplate
                pin = ""
                scope.launch {
                    if (!motion.reduced) {
                        for (x in listOf(14f, -12f, 9f, -6f, 3f, 0f)) shake.animateTo(x, motion.press())
                    }
                }
            }
            is PinResult.LockedOut -> {
                haptics.perform(HapticType.Error)
                pin = ""
                lockedUntil = result.untilElapsedMs
            }
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val breath = app.pocketos.ui.design.rememberBreath()
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(c.accent.copy(alpha = 0.35f + 0.2f * breath), androidx.compose.ui.graphics.Color.Transparent)))
            }
            Box(
                Modifier.size(80.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp)).background(c.brandGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Lock, null, tint = c.onAccent, modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(stringResource(R.string.pocketos_locked), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
        Spacer(Modifier.height(Spacing.xs))
        Text(stringResource(R.string.enter_pin), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        Spacer(Modifier.height(Spacing.xl))
        Row(
            Modifier.offset { IntOffset(shake.value.toInt(), 0) }.height(20.dp).semantics { contentDescription = "" },
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            repeat(maxOf(4, pin.length)) { i ->
                val filled by animateFloatAsState(if (i < pin.length) 1f else 0f, motion.press(), label = "pinDot")
                Box(
                    Modifier.size(16.dp).clip(CircleShape).background(c.textTertiary.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(16.dp * filled).clip(CircleShape).background(c.brandGradient))
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        val status = when {
            lockedOut -> pluralStringResource(R.plurals.try_again_in_seconds, ((lockedUntil - now) / 1000 + 1).toInt(), ((lockedUntil - now) / 1000 + 1).toInt())
            else -> message
        }
        Text(status.orEmpty(), style = MaterialTheme.typography.bodySmall, color = c.warning, textAlign = TextAlign.Center,
            modifier = Modifier.height(36.dp).semantics { liveRegion = LiveRegionMode.Assertive })
        Spacer(Modifier.height(Spacing.md))
        PinPad(
            enabled = !lockedOut,
            onDigit = { d -> if (pin.length < 12) { pin += d; haptics.perform(HapticType.LightTap); message = null } },
            onBackspace = { if (pin.isNotEmpty()) { pin = pin.dropLast(1); haptics.perform(HapticType.LightTap) } },
            onSubmit = ::submit,
            submitEnabled = pin.length >= 4,
        )
        Spacer(Modifier.height(Spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (biometricAvailable) PocketButton(stringResource(R.string.use_biometrics), ::biometric, style = ButtonStyle.Glass, icon = Icons.Rounded.Fingerprint)
            PocketButton(stringResource(R.string.forgot_pin), { recovery = true }, style = ButtonStyle.Text)
        }
    }

    if (recovery) RecoverySheet(biometricAvailable, onBiometric = { recovery = false; biometric() }) { recovery = false }
}

@Composable
private fun PinPad(enabled: Boolean, onDigit: (String) -> Unit, onBackspace: () -> Unit, onSubmit: () -> Unit, submitEnabled: Boolean) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "✓"))
    Column(Modifier.widthIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                row.forEach { key ->
                    when (key) {
                        "⌫" -> PadKey(stringResource(R.string.delete_digit), enabled, onBackspace) { Icon(Icons.AutoMirrored.Rounded.Backspace, null, tint = LocalPocketColors.current.textSecondary) }
                        "✓" -> PadKey(stringResource(R.string.unlock), enabled && submitEnabled, onSubmit, highlighted = submitEnabled) {
                            Icon(Icons.Rounded.Check, null, tint = if (submitEnabled) LocalPocketColors.current.onAccent else LocalPocketColors.current.textTertiary)
                        }
                        else -> PadKey(key, enabled, { onDigit(key) }) {
                            Text(app.pocketos.ui.format.LocalFormatter.current.localizeDigits(key), style = MaterialTheme.typography.headlineSmall, color = LocalPocketColors.current.textPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PadKey(label: String, enabled: Boolean, onClick: () -> Unit, highlighted: Boolean = false, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val c = LocalPocketColors.current
    Box(
        Modifier.size(76.dp)
            .pressFeedback(interaction, CircleShape, enabled)
            .then(if (highlighted) Modifier.clip(CircleShape).background(c.brandGradient) else Modifier.glass(GlassLevel.L2, CircleShape))
            .selectable(false, enabled = enabled, role = Role.Button, interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun RecoverySheet(biometricAvailable: Boolean, onBiometric: () -> Unit, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val c = LocalPocketColors.current
    val scope = rememberCoroutineScope()
    var confirmErase by remember { mutableStateOf(false) }

    GlassBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.forgot_pin)) {
        if (biometricAvailable) {
            PocketButton(stringResource(R.string.use_biometrics), onBiometric, modifier = Modifier.fillMaxWidth(), style = ButtonStyle.Glass, icon = Icons.Rounded.Fingerprint)
            Spacer(Modifier.height(Spacing.lg))
        }
        Text(stringResource(R.string.erase_explanation), style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        Spacer(Modifier.height(Spacing.sm))
        PocketButton(stringResource(R.string.erase_device_data), { confirmErase = true }, modifier = Modifier.fillMaxWidth(), style = ButtonStyle.Text, haptic = HapticType.Warning)
    }
    if (confirmErase) {
        GlassDialog(
            onDismiss = { confirmErase = false },
            title = stringResource(R.string.erase_confirm_title),
            message = stringResource(R.string.erase_confirm_message),
            confirmText = stringResource(R.string.erase),
            onConfirm = {
                confirmErase = false
                scope.launch {
                    container.databases.wipeDatabase()
                    container.pinVault.clear()
                    container.settings.update { it.copy(appLockEnabled = false, biometricUnlock = false) }
                    onDismiss()
                }
            },
            dismissText = stringResource(R.string.cancel),
            destructive = true,
        )
    }
}
