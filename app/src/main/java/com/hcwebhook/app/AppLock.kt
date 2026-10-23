package com.hcwebhook.app

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * App lock with the device's own screen lock: fingerprint, face, or the PIN, pattern or password.
 * The app keeps no secret of its own. It locks at a cold start and after [GRACE_MS] in the
 * background, so the Health Connect permission screen or the file picker does not lock it.
 */
object AppLock {
    const val GRACE_MS = 60_000L
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    @Volatile private var unlocked = false
    @Volatile private var backgroundedAt: Long? = null

    /** True when the device has a screen lock, so app lock can work. */
    fun canUse(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun isLocked(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        if (!PreferencesManager(context).isAppLockEnabled()) return false
        val locked = lockedAfter(unlocked, backgroundedAt, now)
        if (locked) unlocked = false
        return locked
    }

    /** Locked when never unlocked in this process, or when away for longer than [GRACE_MS]. */
    internal fun lockedAfter(unlocked: Boolean, backgroundedAt: Long?, now: Long): Boolean =
        !unlocked || (backgroundedAt != null && now - backgroundedAt > GRACE_MS)

    fun onBackground(now: Long = System.currentTimeMillis()) {
        backgroundedAt = now
    }

    /**
     * Call in onStart after [isLocked]: the app is in front again, so the time away ends here.
     * Without this, a short trip away followed by a long session would lock at the next onStart.
     */
    fun onForeground() {
        backgroundedAt = null
    }

    /** Shows the system prompt. [onResult] gets true when the user unlocked. */
    fun authenticate(activity: FragmentActivity, title: String, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true
                    backgroundedAt = null
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(false)
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
        )
    }
}

/** Hides the app's preview in Recents while app lock is on (Android 13+). Screenshots still work. */
fun applyRecentsPreview(activity: android.app.Activity) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        activity.setRecentsScreenshotEnabled(!PreferencesManager(activity).isAppLockEnabled())
    }
}
