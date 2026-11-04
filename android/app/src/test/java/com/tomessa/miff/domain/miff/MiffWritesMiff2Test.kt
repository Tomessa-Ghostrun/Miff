package com.tomessa.miff.domain.miff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lots of MIFF-2 as plain data (ticket R2, N1, Q1, S2): the paths, the order and the number of writes are the contract with
 * backend/firestore.rules (tests/rules/18-miff2-retirer-quitter-supprimer.test.js proves the rules accept exactly these).
 */
class MiffWritesMiff2Test {
    private val miffId = "AbCdEfGhIjKlMnOpQrSt"
    private val oldCode = "XTZRSP367988"
    private val newCode = "BCDFGHK23456"

    @Test
    fun removeMember_isExactlySixWrites_theThreeOfThePersonThenTheThreeOfTheNewCode_andNothingElse() {
        val lot = MiffWrites.removeMember(miffId, "uid-2", "Chez nous", oldCode, newCode)
        assertEquals(
            listOf(
                "miffs/$miffId/members/uid-2" to WriteKind.DELETE,
                "miffs/$miffId/status/uid-2" to WriteKind.DELETE,
                "miffs/$miffId/photos/uid-2" to WriteKind.DELETE,
                "miffs/$miffId" to WriteKind.UPDATE,
                "joinCodes/$newCode" to WriteKind.SET,
                "joinCodes/$oldCode" to WriteKind.DELETE,
            ),
            lot.map { it.path to it.kind },
        )
        // Nothing is added to this lot: with six writes the rules read up to 18 of the 20 documents allowed (criterion R2).
        assertEquals(6, lot.size)
        assertFalse("the index of the person is never touched by the creator", lot.any { it.path.startsWith("users/") })
    }

    @Test
    fun removeMember_alwaysChangesTheCode_andTheNewCodeDocumentCarriesTheCurrentName() {
        val lot = MiffWrites.removeMember(miffId, "uid-2", "Chez nous", oldCode, newCode)
        assertEquals(mapOf<String, Any?>("joinCode" to newCode), lot[3].data)
        val doc = lot[4].data
        assertEquals(miffId, doc["miffId"])
        assertEquals("Chez nous", doc["miffName"])
        assertEquals(setOf("miffId", "miffName", "createdAt"), doc.keys)
        // A removal that keeps the code is refused before any lot is built (criterion R4).
        runCatching { MiffWrites.removeMember(miffId, "uid-2", "Chez nous", oldCode, oldCode) }
            .let { assertTrue("the same code is refused", it.isFailure) }
        runCatching { MiffWrites.removeMember(miffId, "uid-2", "Chez nous", oldCode, "abc") }
            .let { assertTrue("a code of the wrong shape is refused", it.isFailure) }
    }

    @Test
    fun removeMember_whenTheOldCodeDocumentIsAlreadyGone_leavesItsDeletionOut() {
        val lot = MiffWrites.removeMember(miffId, "uid-2", "Chez nous", oldCode, newCode, oldCodeExists = false)
        assertEquals(5, lot.size)
        assertFalse(lot.any { it.path == "joinCodes/$oldCode" })
    }

    @Test
    fun renewCode_isThreeWrites_inTheOrderOfTheRules() {
        val lot = MiffWrites.renewCode(miffId, "Chez nous", oldCode, newCode)
        assertEquals(
            listOf(
                "miffs/$miffId" to WriteKind.UPDATE,
                "joinCodes/$newCode" to WriteKind.SET,
                "joinCodes/$oldCode" to WriteKind.DELETE,
            ),
            lot.map { it.path to it.kind },
        )
    }

    @Test
    fun leave_isFourDeletions_andTheIndexIsLeftOutOnlyWhenItWasNotFound() {
        val lot = MiffWrites.leave(miffId, "uid-2", "free")
        assertEquals(
            listOf(
                "miffs/$miffId/members/uid-2",
                "miffs/$miffId/status/uid-2",
                "miffs/$miffId/photos/uid-2",
                "users/uid-2/memberships/free",
            ),
            lot.map { it.path },
        )
        assertTrue(lot.all { it.kind == WriteKind.DELETE })
        assertEquals(3, MiffWrites.leave(miffId, "uid-2", null).size)
        // A premium account keys its index by the id of the Miff.
        assertEquals("users/uid-2/memberships/$miffId", MiffWrites.leave(miffId, "uid-2", miffId).last().path)
    }

    @Test
    fun deletePlaces_takesFiveAtMost() {
        assertEquals(5, MiffWrites.deletePlaces(miffId, listOf("p1", "p2", "p3", "p4", "p5")).size)
        assertTrue(runCatching { MiffWrites.deletePlaces(miffId, listOf("a", "b", "c", "d", "e", "f")) }.isFailure)
        assertTrue(runCatching { MiffWrites.deletePlaces(miffId, emptyList()) }.isFailure)
        assertTrue(
            MiffWrites
                .deletePlaces(miffId, listOf("p1"))
                .single()
                .path
                .endsWith("/places/p1"),
        )
    }

    @Test
    fun finalDelete_isTheMiff_itsTombstone_theCurrentCode_andTheCreatorsOwnTraces() {
        val lot = MiffWrites.finalDelete(miffId, "uid-1", oldCode, codeExists = true, membershipKey = "free")
        assertEquals(
            listOf(
                "miffs/$miffId" to WriteKind.DELETE,
                "miffsDeleted/$miffId" to WriteKind.SET,
                "joinCodes/$oldCode" to WriteKind.DELETE,
                "miffs/$miffId/status/uid-1" to WriteKind.DELETE,
                "miffs/$miffId/photos/uid-1" to WriteKind.DELETE,
                "miffs/$miffId/members/uid-1" to WriteKind.DELETE,
                "users/uid-1/memberships/free" to WriteKind.DELETE,
            ),
            lot.map { it.path to it.kind },
        )
        assertEquals("only the date, nothing about anybody", setOf("deletedAt"), lot[1].data.keys)
        assertFalse(
            "no removal of another member goes in the lot that deletes the Miff",
            lot.any {
                it.path.contains("members/") &&
                    !it.path.endsWith("uid-1")
            },
        )
    }

    @Test
    fun finalDelete_leavesOutWhatDoesNotExist() {
        val noCode = MiffWrites.finalDelete(miffId, "uid-1", oldCode, codeExists = false, membershipKey = "free")
        assertFalse(noCode.any { it.path.startsWith("joinCodes/") })
        val noIndex = MiffWrites.finalDelete(miffId, "uid-1", oldCode, codeExists = true, membershipKey = null)
        assertFalse(noIndex.any { it.path.startsWith("users/") })
        assertEquals(5, MiffWrites.finalDelete(miffId, "uid-1", oldCode, codeExists = false, membershipKey = null).size)
    }

    @Test
    fun theLotsNeverShowTheirContentInALog() {
        val lot = MiffWrites.removeMember(miffId, "uid-2", "Chez nous", oldCode, newCode)
        assertTrue(lot.none { it.toString().contains(newCode) || it.toString().contains("Chez nous") })
        assertNotEquals(oldCode, newCode)
    }
}
