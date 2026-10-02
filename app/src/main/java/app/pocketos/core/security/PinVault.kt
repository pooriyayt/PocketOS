package app.pocketos.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/** Keyed MAC used to verify the PIN. */
interface PinMac {
    fun mac(data: ByteArray): ByteArray
    fun reset() {}
}

/**
 * HMAC-SHA256 with a non-exportable Keystore key. Because the key never
 * leaves the Keystore, a copied preferences file cannot be brute-forced
 * offline - every guess must run on this device and is rate-limited below.
 */
class KeystorePinMac(private val alias: String = "pocketos_pin_mac") : PinMac {
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN).build())
        return generator.generateKey()
    }

    override fun mac(data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(key())
        doFinal(data)
    }

    override fun reset() {
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias) }
    }
}

sealed interface PinResult {
    data object Success : PinResult
    data class Wrong(val attemptsBeforeDelay: Int) : PinResult
    data class LockedOut(val untilElapsedMs: Long) : PinResult
}

/**
 * Stores only salt + MAC of the PIN (never the PIN). Failed attempts are
 * persisted with exponential delays (30 s, 1 min, 2 min ... up to 1 h)
 * starting after five consecutive failures.
 */
class PinVault(
    context: Context,
    private val mac: PinMac,
    private val elapsed: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    private val prefs = context.applicationContext.getSharedPreferences("app_lock", Context.MODE_PRIVATE)

    val hasPin: Boolean get() = prefs.contains(KEY_MAC)

    fun setPin(pin: String) {
        require(isValidPin(pin)) { "PIN must be 4-12 digits" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, salt.b64())
            .putString(KEY_MAC, mac.mac(salt + pin.toByteArray()).b64())
            .putInt(KEY_FAILS, 0)
            .remove(KEY_LOCKED_UNTIL)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
        mac.reset()
    }

    fun lockedOutUntil(): Long? {
        val until = prefs.getLong(KEY_LOCKED_UNTIL, 0)
        if (until == 0L) return null
        val remaining = until - wallClock()
        return if (remaining > 0) elapsed() + remaining else null
    }

    fun verify(pin: String): PinResult {
        lockedOutUntil()?.let { return PinResult.LockedOut(it) }
        val salt = prefs.getString(KEY_SALT, null)?.unb64() ?: return PinResult.Wrong(0)
        val expected = prefs.getString(KEY_MAC, null)?.unb64() ?: return PinResult.Wrong(0)
        val ok = isValidPin(pin) && MessageDigest.isEqual(expected, mac.mac(salt + pin.toByteArray()))
        if (ok) {
            prefs.edit().putInt(KEY_FAILS, 0).remove(KEY_LOCKED_UNTIL).apply()
            return PinResult.Success
        }
        val fails = prefs.getInt(KEY_FAILS, 0) + 1
        val editor = prefs.edit().putInt(KEY_FAILS, fails)
        return if (fails >= FREE_ATTEMPTS) {
            val step = fails - FREE_ATTEMPTS
            val delayMs = (30_000L shl step.coerceAtMost(7)).coerceAtMost(3_600_000L)
            editor.putLong(KEY_LOCKED_UNTIL, wallClock() + delayMs).apply()
            PinResult.LockedOut(elapsed() + delayMs)
        } else {
            editor.apply()
            PinResult.Wrong(FREE_ATTEMPTS - fails)
        }
    }

    private fun ByteArray.b64() = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.unb64() = runCatching { Base64.decode(this, Base64.NO_WRAP) }.getOrNull()

    companion object {
        private const val KEY_SALT = "pin_salt"
        private const val KEY_MAC = "pin_mac"
        private const val KEY_FAILS = "pin_failures"
        private const val KEY_LOCKED_UNTIL = "pin_locked_until"
        const val FREE_ATTEMPTS = 5

        fun isValidPin(pin: String) = pin.length in 4..12 && pin.all { it in '0'..'9' }
    }
}
