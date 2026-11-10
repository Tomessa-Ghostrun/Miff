package com.tomessa.miff.domain.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The external feedback form ("Peux faire mieux...", decision of the PM of 2026-11-08): an HTTPS address as it is, or nothing. */
class FeedbackFormTest {
    @Test
    fun emptyOrMissing_meansNoForm() {
        assertNull(FeedbackForm.openableUrl(""))
        assertNull(FeedbackForm.openableUrl("   "))
        assertNull(FeedbackForm.openableUrl(null))
    }

    @Test
    fun anHttpsAddress_isOpenedAsItIs() {
        assertEquals("https://forms.example.org/miff", FeedbackForm.openableUrl("https://forms.example.org/miff"))
        assertEquals(
            "https://forms.example.org/miff?lang=fr#top",
            FeedbackForm.openableUrl("  https://forms.example.org/miff?lang=fr#top \n"),
        )
    }

    @Test
    fun anythingElse_isRefused_neverClearText_neverAnotherScheme() {
        for (bad in listOf(
            "http://forms.example.org/miff",
            "HTTP://forms.example.org",
            "javascript:alert(1)",
            "intent://forms.example.org#Intent;scheme=https;end",
            "market://details?id=x",
            "mailto:a@b.c",
            "forms.example.org/miff",
            "https://",
            "https:///path",
            "https://forms.example.org/a b",
            "file:///sdcard/x",
        )) {
            assertNull(bad, FeedbackForm.openableUrl(bad))
        }
    }
}
