package com.tomessa.miff.data.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fallback of the store request: the store application first, then the web page, always HTTPS, nothing but the package name. */
class StoreListingTest {
    @Test
    fun theStoreApplicationIsTriedFirst_thenTheWebPage() {
        assertEquals(
            listOf(
                "market://details?id=com.tomessa.miff",
                "https://play.google.com/store/apps/details?id=com.tomessa.miff",
            ),
            StoreListing.candidates("com.tomessa.miff"),
        )
    }

    @Test
    fun theWebPageIsNeverInClearText() {
        assertTrue(StoreListing.webAddress("com.tomessa.miff").startsWith("https://"))
    }
}
