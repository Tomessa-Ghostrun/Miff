package com.tomessa.miff.data.notification

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The wiring of the remembered Miffs (contract 4.9) and the order of the teardown of the notifications, with doubles. */
class KnownMiffsWiringTest {
    private class MemoryKnown : KnownMiffs {
        var uid: String? = null
        var ids = emptySet<String>()
        var clears = 0

        override fun remember(
            uid: String,
            miffIds: Set<String>,
            onlyAdd: Boolean,
        ) {
            ids = if (onlyAdd && this.uid == uid) ids + miffIds else miffIds
            this.uid = uid
        }

        override fun forget(
            uid: String,
            miffId: String,
        ) {
            if (this.uid == uid) ids = ids - miffId
        }

        override fun contains(
            uid: String,
            miffId: String,
        ) = this.uid == uid && miffId in ids

        override fun clear() {
            uid = null
            ids = emptySet()
            clears++
        }
    }

    private val known = MemoryKnown()
    private var uid: String? = "u1"
    private val keeper = KnownMiffsKeeper(known, currentUid = { uid })

    @Test
    fun aListReadFromTheServerReplacesTheOldOne() {
        keeper.onDirectory(setOf("a", "b"), fromCache = false)
        keeper.onDirectory(setOf("b", "c"), fromCache = false)
        assertEquals(setOf("b", "c"), known.ids)
        assertTrue(known.contains("u1", "c"))
        assertFalse(known.contains("u1", "a"))
    }

    @Test
    fun aListFromTheCacheCanAddButNeverRemove() {
        keeper.onDirectory(setOf("a", "b"), fromCache = false)
        keeper.onDirectory(setOf("a"), fromCache = true)
        assertEquals("the cache may be older: nothing is removed", setOf("a", "b"), known.ids)
        keeper.onDirectory(setOf("a", "n"), fromCache = true)
        assertEquals(setOf("a", "b", "n"), known.ids)
    }

    @Test
    fun aListFromTheCacheOfAnotherAccountStartsOver() {
        keeper.onDirectory(setOf("a"), fromCache = false)
        uid = "u2"
        keeper.onDirectory(setOf("z"), fromCache = true)
        assertEquals(setOf("z"), known.ids)
        assertFalse(known.contains("u1", "a"))
    }

    @Test
    fun leavingAMiffForgetsIt() {
        keeper.onDirectory(setOf("a", "b"), fromCache = false)
        keeper.onLeft("a")
        assertEquals(setOf("b"), known.ids)
    }

    @Test
    fun nobodySignedIn_nothingIsRemembered() {
        uid = null
        keeper.onDirectory(setOf("a"), fromCache = false)
        keeper.onLeft("a")
        assertEquals(null, known.uid)
    }

    @Test
    fun theEndOfTheSessionClearsTheList() {
        keeper.onDirectory(setOf("a"), fromCache = false)
        keeper.onSessionEnded()
        assertEquals(1, known.clears)
        assertFalse(known.contains("u1", "a"))
    }

    // --- The order of the teardown -------------------------------------------------------------------------------------------------

    @Test
    fun theTeardownClosesThePushFirst_thenDoesEveryLocalStep_andOnlyThenTalksToTheServer() =
        runTest {
            val order = mutableListOf<String>()
            val steps =
                NotificationTeardownSteps(
                    closePush = { order += "close" },
                    local = listOf({ order += "known" }, { order += "alarms" }, { order += "centre" }),
                    forgetPush = { order += "forget" },
                )
            steps.stop()
            assertEquals(listOf("close", "known", "alarms", "centre", "forget"), order)
        }

    @Test
    fun aFailingStepNeverStopsTheNextOnes_andClearsTheListOfMiffs() =
        runTest {
            val steps =
                NotificationTeardownSteps(
                    closePush = { error("push") },
                    local = listOf({ error("alarms") }, known::clear),
                    forgetPush = { error("server") },
                )
            known.remember("u1", setOf("a"))
            steps.stop()
            assertEquals("the remembered Miffs are gone whatever failed before", 1, known.clears)
            assertFalse(known.contains("u1", "a"))
        }
}
