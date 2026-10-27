package com.tomessa.miff.domain

import com.tomessa.miff.domain.auth.AuthForm
import com.tomessa.miff.domain.auth.LoginThrottle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthFormTest {
    @Test
    fun buttonsActivateAsSoonAsBothFieldsAreFilled_S1_C1() {
        assertFalse(AuthForm.hasBothFields("", ""))
        assertFalse(AuthForm.hasBothFields("a@b.fr", ""))
        assertFalse(AuthForm.hasBothFields("  ", "x"))
        assertTrue(AuthForm.hasBothFields("a", "x"))
    }

    @Test
    fun emailFormat_isCheckedLocally_S2() {
        assertTrue(AuthForm.isEmailFormatValid("a@b.fr"))
        assertTrue("capitals are fine", AuthForm.isEmailFormatValid("Jean.Dupont@Mail.COM"))
        assertTrue("pasted spaces are removed", AuthForm.isEmailFormatValid("  a@b.fr \n"))
        assertFalse(AuthForm.isEmailFormatValid("abc"))
        assertFalse(AuthForm.isEmailFormatValid("a@b"))
        assertFalse(AuthForm.isEmailFormatValid("a b@c.fr"))
        assertFalse(AuthForm.isEmailFormatValid("a".repeat(250) + "@b.fr"))
        assertEquals("a@b.fr", AuthForm.normalizeEmail("  a@b.fr "))
    }

    @Test
    fun passwordMinimumIsEightCharacters_andNothingElse_S3() {
        assertFalse(AuthForm.isPasswordLongEnough("1234567"))
        assertTrue(AuthForm.isPasswordLongEnough("12345678"))
        assertTrue("no complexity rule", AuthForm.isPasswordLongEnough("aaaaaaaa"))
        assertTrue("spaces and non latin characters are accepted", AuthForm.isPasswordLongEnough("pass word 密码密码"))
    }

    @Test
    fun anEmojiCountsAsOneCharacter() {
        assertFalse(AuthForm.isPasswordLongEnough("😀".repeat(7)))
        assertTrue(AuthForm.isPasswordLongEnough("😀".repeat(8)))
    }

    @Test
    fun inputIsBoundedToATechnicalLimit() {
        assertEquals(AuthForm.MAX_EMAIL_LENGTH, AuthForm.limitEmailInput("a".repeat(1000)).length)
        assertEquals(AuthForm.MAX_PASSWORD_LENGTH, AuthForm.limitPasswordInput("a".repeat(5000)).length)
    }

    @Test
    fun fiveWrongAnswersInARow_startAShortPause() {
        var now = 1_000L
        val throttle = LoginThrottle({ now })
        repeat(4) { throttle.recordWrongCredentials() }
        assertEquals(0L, throttle.remainingPauseMillis())
        throttle.recordWrongCredentials()
        assertEquals(30_000L, throttle.remainingPauseMillis())
        now += 10_000
        assertEquals(20_000L, throttle.remainingPauseMillis())
        now += 20_000
        assertEquals(0L, throttle.remainingPauseMillis())
    }

    @Test
    fun aSuccessResetsTheCount() {
        val throttle = LoginThrottle({ 0L })
        repeat(4) { throttle.recordWrongCredentials() }
        throttle.recordSuccess()
        repeat(4) { throttle.recordWrongCredentials() }
        assertEquals(0L, throttle.remainingPauseMillis())
    }
}
