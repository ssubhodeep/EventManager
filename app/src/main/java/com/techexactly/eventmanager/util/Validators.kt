package com.techexactly.eventmanager.util

/**
 * Pure Kotlin validation functions with no Android framework dependency (deliberately not
 * using android.util.Patterns, which is stubbed out in plain JUnit tests) so they're cheap
 * to unit test directly - see app/src/test/.../AddEditEventValidationTest.kt and
 * AuthValidationTest.kt.
 */
object Validators {

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun emailError(email: String): String? = when {
        email.isBlank() -> "Email is required"
        !EMAIL_REGEX.matches(email) -> "Enter a valid email address"
        else -> null
    }

    fun passwordError(password: String): String? = when {
        password.isBlank() -> "Password is required"
        password.length < 6 -> "Password must be at least 6 characters"
        else -> null
    }

    fun confirmPasswordError(password: String, confirmPassword: String): String? = when {
        confirmPassword.isBlank() -> "Please confirm your password"
        confirmPassword != password -> "Passwords do not match"
        else -> null
    }

    fun eventTitleError(title: String): String? =
        if (title.isBlank()) "Title is required" else null

    /** @param dateTimeMillis 0 means "not picked yet". */
    fun eventDateError(dateTimeMillis: Long, nowMillis: Long = System.currentTimeMillis()): String? = when {
        dateTimeMillis <= 0L -> "Please pick a date & time"
        dateTimeMillis < nowMillis -> "Date & time cannot be in the past"
        else -> null
    }
}
