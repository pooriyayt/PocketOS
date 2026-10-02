package app.pocketos.core.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Biometric / device-credential unlock bound to a Keystore key that can only
 * be used after the user authenticates. Unlock succeeds only if the cipher
 * released by BiometricPrompt actually works - a hooked or faked callback
 * without real authentication cannot produce a usable cipher.
 */
class BiometricUnlock(private val alias: String = "pocketos_biometric_unlock") {

    enum class Availability { AVAILABLE, NONE_ENROLLED, UNAVAILABLE }

    sealed interface Outcome {
        data object Success : Outcome
        data object Cancelled : Outcome
        data object UsePin : Outcome
        /** Biometrics changed (e.g. a new fingerprint): the key was invalidated. */
        data object KeyInvalidated : Outcome
        data class Error(val message: CharSequence) : Outcome
    }

    private val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_STRONG

    fun availability(activity: FragmentActivity): Availability =
        when (BiometricManager.from(activity).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NONE_ENROLLED
            else -> Availability.UNAVAILABLE
        }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(builder.build())
            generateKey()
        }
    }

    fun resetKey() {
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias) }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: CharSequence,
        subtitle: CharSequence?,
        usePinLabel: CharSequence,
        onResult: (Outcome) -> Unit,
    ) {
        val cipher = try {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        } catch (_: KeyPermanentlyInvalidatedException) {
            resetKey()
            onResult(Outcome.KeyInvalidated)
            return
        } catch (e: Exception) {
            onResult(Outcome.Error(e.message ?: "Biometric unavailable"))
            return
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val verified = runCatching { result.cryptoObject?.cipher?.doFinal(CHALLENGE) != null }.getOrDefault(false)
                onResult(if (verified) Outcome.Success else Outcome.Error("Verification failed"))
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> Outcome.UsePin
                        BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED -> Outcome.Cancelled
                        else -> Outcome.Error(errString)
                    }
                )
            }
            // onAuthenticationFailed (non-matching finger) is handled by the system prompt itself.
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { subtitle?.let(::setSubtitle) }
            .setAllowedAuthenticators(authenticators)
            .setConfirmationRequired(false)
            .apply { if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) setNegativeButtonText(usePinLabel) }
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    companion object {
        private val CHALLENGE = "pocketos-unlock".toByteArray()
    }
}
