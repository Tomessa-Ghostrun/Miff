package com.tomessa.miff.data.review

import android.app.Activity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The request of the store after "J'adore !": Google Play In-App Review first; the page of the app in the store ONLY when the API cannot be used at all; never both
 * (the card of the store may already be on screen when the end of the flow fails).
 */
class StoreReviewTest {
    private class FakeApi(
        private val requestFails: Boolean = false,
        private val launchFails: Boolean = false,
    ) : InAppReviewApi {
        var requested = 0
        var launched = 0

        override suspend fun requestFlow(activity: Activity): Any {
            requested++
            if (requestFails) throw IllegalStateException("no Play")
            return Any()
        }

        override suspend fun launchFlow(
            activity: Activity,
            flow: Any,
        ) {
            launched++
            if (launchFails) throw IllegalStateException("closed")
        }
    }

    private var listings = 0
    private val activity = Activity()

    @Test
    fun whenTheApiWorks_itsCardIsShown_andThePageOfTheStoreIsNotOpened() =
        runTest {
            val api = FakeApi()
            val shown =
                PlayStoreReviewRequester(api) {
                    listings++
                    true
                }.request(activity)
            assertTrue(shown)
            assertEquals(1, api.requested)
            assertEquals(1, api.launched)
            assertEquals(0, listings)
        }

    @Test
    fun whenTheApiCannotBeUsed_thePageOfTheStoreIsOpenedInstead() =
        runTest {
            val api = FakeApi(requestFails = true)
            val shown =
                PlayStoreReviewRequester(api) {
                    listings++
                    true
                }.request(activity)
            assertTrue(shown)
            assertEquals(0, api.launched)
            assertEquals(1, listings)
        }

    @Test
    fun whenNothingCanOpenAnything_theAnswerIsFalse_soThatTheScreenSaysSo() =
        runTest {
            val shown = PlayStoreReviewRequester(FakeApi(requestFails = true)) { false }.request(activity)
            assertFalse(shown)
        }

    @Test
    fun whenTheApiFailsAfterItsCardMayHaveAppeared_thePageIsNotOpenedOnTopOfIt() =
        runTest {
            val api = FakeApi(launchFails = true)
            val shown =
                PlayStoreReviewRequester(api) {
                    listings++
                    true
                }.request(activity)
            assertTrue(shown)
            assertEquals(1, api.launched)
            assertEquals(0, listings)
        }

    @Test
    fun theStorePage_triesTheStoreApplicationThenTheWebPage_andStopsAtTheFirstThatOpens() {
        val tried = mutableListOf<String>()
        val opened =
            StoreListing.openFirst(StoreListing.candidates("com.tomessa.miff")) {
                tried += it
                it.startsWith("https://")
            }
        assertTrue(opened)
        assertEquals(
            listOf(
                "market://details?id=com.tomessa.miff",
                "https://play.google.com/store/apps/details?id=com.tomessa.miff",
            ),
            tried,
        )
        tried.clear()
        assertTrue(
            StoreListing.openFirst(StoreListing.candidates("com.tomessa.miff")) {
                tried += it
                true
            },
        )
        assertEquals(1, tried.size)
        assertFalse(StoreListing.openFirst(StoreListing.candidates("com.tomessa.miff")) { false })
    }
}
