package app.pocketos.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.navigation.NavController
import app.pocketos.R
import app.pocketos.core.security.BiometricUnlock
import app.pocketos.core.security.PinResult
import app.pocketos.core.security.PinVault
import app.pocketos.ui.LocalAppContainer
import app.pocketos.ui.LocalAppUi
import app.pocketos.ui.design.GlassChip
import app.pocketos.ui.design.GlassDialog
import app.pocketos.ui.design.GlassSwitchRow
import app.pocketos.ui.design.GlassTextField
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.LocalHaptics
import app.pocketos.ui.screens.lock.findFragmentActivity
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Spacing
import kotlinx.coroutines.launch

private enum class PinFlow { NONE, SET, CONFIRM_DISABLE, CHANGE_VERIFY, CHANGE_NEW }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecurityScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsState(initial = container.latestSettings)
    val c = LocalPocketColors.current
    val ui = LocalAppUi.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current.findFragmentActivity()
    var flow by remember { mutableStateOf(PinFlow.NONE) }
    val availability = activity?.let { container.biometric.availability(it) } ?: BiometricUnlock.Availability.UNAVAILABLE
    val lockOnText = stringResource(R.string.app_lock_enabled_message)
    val biometricTitle = stringResource(R.string.enable_biometrics_title)
    val usePin = stringResource(R.string.use_pin)

    SettingsPage(stringResource(R.string.settings_security), nav) {
        SettingsGroup(
            stringResource(R.string.app_lock),
            footer = stringResource(R.string.app_lock_footer),
        ) {
            GlassSwitchRow(
                stringResource(R.string.require_pin), settings.appLockEnabled,
                { enable -> flow = if (enable) PinFlow.SET else PinFlow.CONFIRM_DISABLE },
                subtitle = stringResource(R.string.require_pin_subtitle), icon = Icons.Rounded.Lock,
            )
            if (settings.appLockEnabled) {
                GlassSwitchRow(
                    stringResource(R.string.unlock_with_biometrics),
                    settings.biometricUnlock && availability == BiometricUnlock.Availability.AVAILABLE,
                    { enable ->
                        if (!enable) {
                            scope.launch { container.settings.update { it.copy(biometricUnlock = false) } }
                            container.biometric.resetKey()
                        } else if (activity != null) {
                            // Confirm once so the key is created and proven usable before relying on it.
                            container.biometric.resetKey()
                            container.biometric.authenticate(activity, biometricTitle, null, usePin) { outcome ->
                                if (outcome == BiometricUnlock.Outcome.Success) {
                                    haptics.perform(HapticType.Success)
                                    scope.launch { container.settings.update { it.copy(biometricUnlock = true) } }
                                }
                            }
                        }
                    },
                    subtitle = stringResource(
                        when (availability) {
                            BiometricUnlock.Availability.AVAILABLE -> R.string.biometrics_available
                            BiometricUnlock.Availability.NONE_ENROLLED -> R.string.biometrics_none_enrolled
                            BiometricUnlock.Availability.UNAVAILABLE -> R.string.biometrics_unavailable
                        }
                    ),
                    enabled = availability == BiometricUnlock.Availability.AVAILABLE,
                    icon = Icons.Rounded.Fingerprint,
                )
                SettingsRow(Icons.Rounded.Password, stringResource(R.string.change_pin)) { flow = PinFlow.CHANGE_VERIFY }
                SettingsRow(Icons.Rounded.LockClock, stringResource(R.string.lock_now)) {
                    haptics.perform(HapticType.Confirm)
                    container.appLock.lockNow()
                }
            }
        }
        if (settings.appLockEnabled) {
            Text(stringResource(R.string.relock_after), style = MaterialTheme.typography.labelLarge, color = c.textSecondary, modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xl, bottom = Spacing.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(0 to R.string.relock_immediately, 30 to R.string.relock_30s, 60 to R.string.relock_1m, 300 to R.string.relock_5m, 900 to R.string.relock_15m).forEach { (sec, label) ->
                    GlassChip(stringResource(label), settings.relockAfterSeconds == sec, { scope.launch { container.settings.update { it.copy(relockAfterSeconds = sec) } } })
                }
            }
            SettingsGroup(stringResource(R.string.privacy_screen)) {
                GlassSwitchRow(
                    stringResource(R.string.hide_in_recents), settings.hideInRecents,
                    { v -> scope.launch { container.settings.update { it.copy(hideInRecents = v) } } },
                    subtitle = stringResource(R.string.hide_in_recents_subtitle), icon = Icons.Rounded.VisibilityOff,
                )
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(stringResource(R.string.biometric_scope_note), style = MaterialTheme.typography.bodySmall, color = c.textTertiary, modifier = Modifier.fillMaxWidth())
    }

    when (flow) {
        PinFlow.SET, PinFlow.CHANGE_NEW -> NewPinDialog(onDismiss = { flow = PinFlow.NONE }) { pin ->
            container.pinVault.setPin(pin)
            scope.launch {
                container.settings.update { it.copy(appLockEnabled = true, hideInRecents = true) }
                container.appLock.configure(true, settings.relockAfterSeconds)
                container.appLock.unlockWithPin(pin)
            }
            haptics.perform(HapticType.Success)
            ui.message(lockOnText)
            flow = PinFlow.NONE
        }
        PinFlow.CONFIRM_DISABLE -> VerifyPinDialog(onDismiss = { flow = PinFlow.NONE }) {
            container.pinVault.clear()
            container.biometric.resetKey()
            scope.launch { container.settings.update { it.copy(appLockEnabled = false, biometricUnlock = false) } }
            flow = PinFlow.NONE
        }
        PinFlow.CHANGE_VERIFY -> VerifyPinDialog(onDismiss = { flow = PinFlow.NONE }) { flow = PinFlow.CHANGE_NEW }
        PinFlow.NONE -> Unit
    }
}

@Composable
private fun NewPinDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHaptics.current
    val tooShort = stringResource(R.string.pin_rules)
    val mismatch = stringResource(R.string.pin_mismatch)
    GlassDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.set_pin),
        message = stringResource(R.string.pin_rules),
        confirmText = stringResource(R.string.save),
        onConfirm = {
            when {
                !PinVault.isValidPin(first) -> { error = tooShort; haptics.perform(HapticType.Error) }
                first != second -> { error = mismatch; haptics.perform(HapticType.Error) }
                else -> onSet(first)
            }
        },
        dismissText = stringResource(R.string.cancel),
    ) {
        GlassTextField(first, { first = it.filter(Char::isDigit).take(12); error = null }, label = stringResource(R.string.new_pin), password = true, keyboardType = KeyboardType.NumberPassword)
        Spacer(Modifier.height(Spacing.sm))
        GlassTextField(second, { second = it.filter(Char::isDigit).take(12); error = null }, label = stringResource(R.string.confirm_pin), password = true, keyboardType = KeyboardType.NumberPassword, error = error)
    }
}

@Composable
private fun VerifyPinDialog(onDismiss: () -> Unit, onVerified: () -> Unit) {
    val container = LocalAppContainer.current
    val haptics = LocalHaptics.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val wrong = stringResource(R.string.pin_wrong)
    val locked = stringResource(R.string.pin_locked_out)
    GlassDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.enter_current_pin),
        confirmText = stringResource(R.string.continue_label),
        onConfirm = {
            when (container.pinVault.verify(pin)) {
                PinResult.Success -> onVerified()
                is PinResult.Wrong -> { error = wrong; pin = ""; haptics.perform(HapticType.Error) }
                is PinResult.LockedOut -> { error = locked; pin = ""; haptics.perform(HapticType.Error) }
            }
        },
        dismissText = stringResource(R.string.cancel),
    ) {
        GlassTextField(pin, { pin = it.filter(Char::isDigit).take(12); error = null }, password = true, keyboardType = KeyboardType.NumberPassword, error = error)
    }
}
