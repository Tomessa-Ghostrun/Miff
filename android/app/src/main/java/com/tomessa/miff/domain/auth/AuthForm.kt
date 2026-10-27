package com.tomessa.miff.domain.auth

/** Local rules of the sign-up and sign-in forms (ticket 7.1 and 7.2). Pure logic, tested without a phone. */
object AuthForm {
    /** Length is the only password rule (no complexity rule: NIST / CNIL recommendation). */
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_EMAIL_LENGTH = 254
    const val MAX_PASSWORD_LENGTH = 1024

    /** Spaces around a pasted address are removed; upper case is accepted (ticket case 16). */
    fun normalizeEmail(raw: String): String = raw.trim()

    fun isEmailFormatValid(raw: String): Boolean {
        val email = normalizeEmail(raw)
        return email.length <= MAX_EMAIL_LENGTH && CredentialsInput.looksLikeEmail(email)
    }

    /** Counts characters the way a person does (an emoji is one character, not two). */
    fun isPasswordLongEnough(password: String): Boolean =
        password.codePointCount(0, password.length) >= MIN_PASSWORD_LENGTH

    /** The buttons are enabled as soon as both fields are not empty: no rule is hidden behind a grey button. */
    fun hasBothFields(
        email: String,
        password: String,
    ): Boolean = email.isNotBlank() && password.isNotEmpty()

    fun limitEmailInput(raw: String): String = raw.take(MAX_EMAIL_LENGTH)

    fun limitPasswordInput(raw: String): String = raw.take(MAX_PASSWORD_LENGTH)
}

/**
 * Comfort limit on sign-in attempts: 5 wrong answers in a row, then a short pause. NOT a security protection
 * (it lives in the app, a modified client skips it): the real protection is Firebase's own temporary block and App Check.
 */
class LoginThrottle(
    private val nowMillis: () -> Long,
    private val maxFailures: Int = DEFAULT_MAX_FAILURES,
    private val pauseMillis: Long = DEFAULT_PAUSE_MILLIS,
) {
    private var failures = 0
    private var pausedUntil = 0L

    /** 0 when a new attempt is allowed. */
    fun remainingPauseMillis(): Long = (pausedUntil - nowMillis()).coerceAtLeast(0L)

    fun recordWrongCredentials() {
        failures++
        if (failures >= maxFailures) {
            failures = 0
            pausedUntil = nowMillis() + pauseMillis
        }
    }

    fun recordSuccess() {
        failures = 0
        pausedUntil = 0L
    }

    companion object {
        const val DEFAULT_MAX_FAILURES = 5
        const val DEFAULT_PAUSE_MILLIS = 30_000L
    }
}
