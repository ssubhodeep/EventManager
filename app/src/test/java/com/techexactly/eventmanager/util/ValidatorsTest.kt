package com.techexactly.eventmanager.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for the validation logic used by the auth screens and the add/edit event
 * screen (email/password rules, required title, "date can't be in the past"). Pure JVM
 * tests - no Android framework, no Firebase, no mocking required.
 */
class ValidatorsTest {

    // --- email ---

    @Test
    fun `blank email is rejected`() {
        assertEquals("Email is required", Validators.emailError(""))
    }

    @Test
    fun `malformed email is rejected`() {
        assertNotNull(Validators.emailError("not-an-email"))
    }

    @Test
    fun `well-formed email is accepted`() {
        assertNull(Validators.emailError("subhodeep@example.com"))
    }

    // --- password ---

    @Test
    fun `short password is rejected`() {
        assertNotNull(Validators.passwordError("12345"))
    }

    @Test
    fun `six character password is accepted`() {
        assertNull(Validators.passwordError("123456"))
    }

    @Test
    fun `mismatched confirmation is rejected`() {
        assertNotNull(Validators.confirmPasswordError("password1", "password2"))
    }

    @Test
    fun `matching confirmation is accepted`() {
        assertNull(Validators.confirmPasswordError("password1", "password1"))
    }

    // --- event title ---

    @Test
    fun `blank event title is rejected`() {
        assertNotNull(Validators.eventTitleError("   "))
    }

    @Test
    fun `non-blank event title is accepted`() {
        assertNull(Validators.eventTitleError("Team standup"))
    }

    // --- event date ---

    @Test
    fun `unset event date is rejected`() {
        assertNotNull(Validators.eventDateError(dateTimeMillis = 0L, nowMillis = 1_000_000L))
    }

    @Test
    fun `past event date is rejected`() {
        val now = 1_000_000L
        assertNotNull(Validators.eventDateError(dateTimeMillis = now - 1, nowMillis = now))
    }

    @Test
    fun `future event date is accepted`() {
        val now = 1_000_000L
        assertNull(Validators.eventDateError(dateTimeMillis = now + 1, nowMillis = now))
    }
}
