package app.pocketos.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LockState { UNKNOWN, LOCKED, UNLOCKED }

/**
 * App lock state machine. The unlocked state exists only in memory: a new
 * process always starts locked (when the lock is enabled), and nothing like
 * "isUnlocked=true" is ever persisted. Protected UI is not composed at all
 * while locked.
 */
class AppLockManager(
    private val pinVault: PinVault,
    private val elapsed: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) {
    private val _state = MutableStateFlow(LockState.UNKNOWN)
    val state: StateFlow<LockState> = _state.asStateFlow()

    private var enabled = false
    private var relockAfterMs = 0L
    private var backgroundedAt: Long? = null
    private var suppressNextRelock = false

    /** Applies settings; the first call resolves the initial state. */
    fun configure(lockEnabled: Boolean, relockAfterSeconds: Int) {
        enabled = lockEnabled && pinVault.hasPin
        relockAfterMs = relockAfterSeconds * 1000L
        when {
            !enabled -> _state.value = LockState.UNLOCKED
            _state.value == LockState.UNKNOWN -> _state.value = LockState.LOCKED
        }
    }

    val isEnabled: Boolean get() = enabled

    fun onBackground() {
        backgroundedAt = elapsed()
    }

    fun onForeground() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (suppressNextRelock) {
            suppressNextRelock = false
            return
        }
        if (enabled && _state.value == LockState.UNLOCKED && elapsed() - since >= relockAfterMs) {
            _state.value = LockState.LOCKED
        }
    }

    /** Call before launching a system UI (file picker, settings) that briefly backgrounds the app. */
    fun allowExternalActivity() {
        suppressNextRelock = true
    }

    fun unlockWithPin(pin: String): PinResult {
        val result = pinVault.verify(pin)
        if (result is PinResult.Success) _state.value = LockState.UNLOCKED
        return result
    }

    /** Called only after a BiometricPrompt CryptoObject operation succeeded. */
    fun unlockWithVerifiedBiometric() {
        _state.value = LockState.UNLOCKED
    }

    fun lockNow() {
        if (enabled) _state.value = LockState.LOCKED
    }

    fun lockedOutUntil(): Long? = pinVault.lockedOutUntil()
}
