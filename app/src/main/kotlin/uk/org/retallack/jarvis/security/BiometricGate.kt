package uk.org.retallack.jarvis.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Result of a biometric authentication attempt.
 */
sealed class BiometricResult {
    data object Success : BiometricResult()
    data object Cancelled : BiometricResult()
    data class Error(val code: Int, val message: String) : BiometricResult()
    data object NotAvailable : BiometricResult()
}

/**
 * Manages biometric authentication for sensitive operations.
 */
@Singleton
class BiometricGate @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var isEnabled: Boolean = false

    /**
     * Check if biometric authentication is available on this device.
     */
    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Whether biometric gate is enabled by user preference.
     */
    fun isEnabled(): Boolean = isEnabled

    /**
     * Enable or disable biometric gate.
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    /**
     * Request biometric authentication.
     * @param activity the FragmentActivity to show the prompt on
     * @param title prompt title
     * @param subtitle prompt subtitle
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "Authentication Required",
        subtitle: String = "Verify your identity to control this device",
    ): BiometricResult {
        if (!isBiometricAvailable()) return BiometricResult.NotAvailable

        return suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(activity)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) {
                        continuation.resume(BiometricResult.Success)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) {
                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        ) {
                            continuation.resume(BiometricResult.Cancelled)
                        } else {
                            continuation.resume(BiometricResult.Error(errorCode, errString.toString()))
                        }
                    }
                }

                override fun onAuthenticationFailed() {
                    // Called on failed attempt but prompt stays open — don't resume yet
                }
            }

            val prompt = BiometricPrompt(activity, executor, callback)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
                .build()

            prompt.authenticate(promptInfo)
        }
    }

    /**
     * Check if authentication is needed for an entity and return whether to proceed.
     * @return true if operation should proceed, false if blocked
     */
    suspend fun gateCheck(
        activity: FragmentActivity,
        entityId: String,
        domainChecker: SensitiveDomainChecker,
    ): Boolean {
        if (!isEnabled) return true
        if (!domainChecker.isSensitive(entityId)) return true

        return when (authenticate(activity)) {
            is BiometricResult.Success -> true
            else -> false
        }
    }
}
