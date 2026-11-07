package com.tomessa.miff.domain.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class DeviceIdsTest {
    @Test
    fun aGeneratedIdentifier_hasTheShapeTheRulesRequire_andIsLongEnough() {
        repeat(200) {
            val id = DeviceIds.generate()
            assertTrue(id, DeviceIds.isValid(id))
            assertTrue("22 characters or more (128 bits at least), got ${id.length}", id.length >= 22)
            assertTrue(id.length <= 64)
        }
    }

    @Test
    fun twoIdentifiers_arenotTheSame_theyAreDrawnAtRandom() {
        val ids = (1..500).map { DeviceIds.generate() }.toSet()
        assertEquals(500, ids.size)
    }

    @Test
    fun theIdentifierComesFromTheRandomSourceItIsGiven_andFromNothingElse() {
        // Same seed, same bytes: the identifier depends on the random source only (no clock, no hardware identifier, no platform).
        val a = DeviceIds.generate(SecureRandomWithSeed(7))
        val b = DeviceIds.generate(SecureRandomWithSeed(7))
        val c = DeviceIds.generate(SecureRandomWithSeed(8))
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun theShapeIsTheOneOfTheRules() {
        assertTrue(DeviceIds.isValid("a".repeat(16)))
        assertTrue(DeviceIds.isValid("A_b-9".repeat(12)))
        assertFalse(DeviceIds.isValid("a".repeat(15)))
        assertFalse(DeviceIds.isValid("a".repeat(65)))
        assertFalse(DeviceIds.isValid("abcdefghijklmnop/"))
        assertFalse(DeviceIds.isValid("abcdefghijklmnop "))
        assertFalse(DeviceIds.isValid("abcdefghijklmnop\n"))
        assertFalse(DeviceIds.isValid(null))
    }

    @Test
    fun theSessionRead_hidesTheIdentifier_inItsText() {
        assertFalse(RemoteSession.Present("device-secret-0123456789").toString().contains("secret"))
    }

    // --- The rule of the launch / return of the network / refused write ----------------------------------------------

    private val mine = "device-A-0123456789abcdef"
    private val theirs = "device-B-0123456789abcdef"

    @Test
    fun judge_ourSession_isMine() {
        assertEquals(SessionVerdict.MINE, SessionJudge.judge(mine, RemoteSession.Present(mine)))
    }

    @Test
    fun judge_anotherPhonesSession_isReplaced() {
        assertEquals(SessionVerdict.REPLACED, SessionJudge.judge(mine, RemoteSession.Present(theirs)))
    }

    @Test
    fun judge_aSessionAndNoLocalIdentifier_isReplaced_neverTrusted() {
        assertEquals(SessionVerdict.REPLACED, SessionJudge.judge(null, RemoteSession.Present(theirs)))
        assertEquals(SessionVerdict.REPLACED, SessionJudge.judge(null, RemoteSession.Present(mine)))
    }

    @Test
    fun judge_noSessionAndNoLocalIdentifier_isAnOldInstallation_whichWritesItsOwn() {
        assertEquals(SessionVerdict.WRITE_NEW, SessionJudge.judge(null, RemoteSession.Missing))
    }

    @Test
    fun judge_aLocalIdentifierButNoSessionAnyMore_isSessionGone_notReplaced() {
        // The session vanished (account being deleted from another phone): signed out, nothing written back (no loop), but nobody TOOK the place.
        assertEquals(SessionVerdict.SESSION_GONE, SessionJudge.judge(mine, RemoteSession.Missing))
    }

    @Test
    fun judge_onlyAMissingSessionWithAnIdentifier_isGone_everythingElseKeepsItsVerdict() {
        assertEquals(SessionVerdict.REPLACED, SessionJudge.judge(mine, RemoteSession.Present(theirs)))
        assertEquals(SessionVerdict.REPLACED, SessionJudge.judge(null, RemoteSession.Present(theirs)))
        assertEquals(SessionVerdict.REPLACED, SessionJudge.judge(null, RemoteSession.Present(mine)))
        assertEquals(SessionVerdict.WRITE_NEW, SessionJudge.judge(null, RemoteSession.Missing))
        assertEquals(SessionVerdict.MINE, SessionJudge.judge(mine, RemoteSession.Present(mine)))
    }

    private class SecureRandomWithSeed(
        seed: Long,
    ) : SecureRandom() {
        private val inner = java.util.Random(seed)

        override fun nextBytes(bytes: ByteArray) = inner.nextBytes(bytes)
    }
}
