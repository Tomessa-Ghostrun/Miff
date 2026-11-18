package com.tomessa.miff.domain.miff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lots that delete the alert of a Miff in the same lot (backend/data-model.md 2.18, point 7): stop of sharing, leaving, deletion of a Miff, and the separate lot BEFORE the removal
 * of a member. Without the function (BuildConfig.ALERT_DANGER false) nothing is added: the rules in production do not know the path and would refuse the whole lot.
 */
class MiffWritesAlertTest {
    private fun paths(writes: List<DocWrite>) = writes.map { it.path }

    @Test
    fun withoutTheFunction_noLotMentionsTheAlert() {
        assertFalse(paths(MiffWrites.stopSharing("m", "u")).any { it.contains("alerts") })
        assertFalse(paths(MiffWrites.leave("m", "u", "free")).any { it.contains("alerts") })
        assertFalse(
            paths(MiffWrites.finalDelete("m", "u", "ABCDEFGHJKMN".take(12), true, "free")).any {
                it.contains("alerts")
            },
        )
        assertTrue(MiffWrites.alertDeletion("m", "u", false).isEmpty())
    }

    @Test
    fun theStopOfSharingDeletesTheAlertOfThatMiffOnly() {
        val writes = MiffWrites.stopSharing("m", "u", withAlert = true)
        assertEquals(5, writes.size)
        val alert = writes.single { it.path == "miffs/m/alerts/u" }
        assertEquals(WriteKind.DELETE, alert.kind)
        assertTrue(writes.all { it.path.startsWith("miffs/m/") })
    }

    @Test
    fun leavingDeletesTheAlertBeforeTheCard() {
        val writes = MiffWrites.leave("m", "u", "free", withAlert = true)
        val paths = paths(writes)
        assertTrue(paths.indexOf("miffs/m/alerts/u") in 0 until paths.indexOf("users/u/memberships/free"))
    }

    @Test
    fun theFinalDeletionOfAMiffDeletesTheAlertOfItsCreator() {
        val writes = MiffWrites.finalDelete("m", "u", "ABCDEFGHJKMN", true, "free", withAlert = true)
        assertTrue(paths(writes).contains("miffs/m/alerts/u"))
        assertEquals(WriteKind.DELETE, writes.single { it.path == "miffs/m/alerts/u" }.kind)
    }

    @Test
    fun theRemovalLotItselfDeletesTheAlertOfTheMember_theCreatorCannotDeleteItAlone() {
        val without = MiffWrites.removeMember("m", "t", "Nom", "AAAAAAAAAAAA", "BBBBBBBBBBBB")
        assertFalse(paths(without).any { it.contains("alerts") })
        assertEquals(7, without.size)
        val with = MiffWrites.removeMember("m", "t", "Nom", "AAAAAAAAAAAA", "BBBBBBBBBBBB", withAlert = true)
        assertEquals(8, with.size)
        assertEquals(WriteKind.DELETE, with.single { it.path == "miffs/m/alerts/t" }.kind)
        // before the three writes of the new code, with the card, the status, the photo and the position
        assertTrue(paths(with).indexOf("miffs/m/alerts/t") < paths(with).indexOf("miffs/m"))
    }
}
