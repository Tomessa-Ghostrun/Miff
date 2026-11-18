package com.tomessa.miff.domain.update

import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.tracking.Fix
import com.tomessa.miff.testsupport.DEVICE_A
import com.tomessa.miff.testsupport.T0
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shape of the document of a photo that carries a DESCRIPTION (ticket description-update, criteria P1 to P4, P10, R1 to R3, contract of `validPhoto`):
 * seven keys at most, the six of the photo always there, `description` ONLY when the cleaned text is not empty, and then a text of 1 to 140 UTF-16 units.
 * The shape is compared with the rule as the ticket writes it (`hasOnly` of seven keys, `hasAll` of six, `isStr(description, 1, 140)`), so that a drift of the client is seen here.
 */
class UpdateDescriptionWritesTest {
    private val miffId = "AbCdEfGhIjKlMnOpQrSt"
    private val uid = "uid-1"
    private val photo = UpdatePhoto(ByteArray(90_000) { it.toByte() }, 461, 1024, T0)
    private val place = PhotoPlace.from(Fix(48.856614, 2.352222, T0 - 60_000L), T0)!!

    private val required = setOf("jpeg", "createdAt", "expiresAt", "lat", "lng", "measuredAt")
    private val allowed = required + "description"

    private fun write(description: String?): DocWrite = UpdateWrites.photo(miffId, uid, photo, place, T0, description)

    /** The rule of the ticket, applied to what the client builds. */
    private fun acceptedByTheRule(data: Map<String, Any?>): Boolean {
        if (!allowed.containsAll(data.keys)) return false
        if (!data.keys.containsAll(required)) return false
        if ("description" in data) {
            val text = data["description"] as? String ?: return false
            if (text.length !in 1..140) return false
        }
        return true
    }

    @Test
    fun withoutADescription_theDocumentIsExactlyTheOldOne_noKeyAtAll() {
        listOf(null, "", "   ", "\n\t").forEach {
            val data = write(it).data
            assertEquals("for «${it ?: "null"}»", required, data.keys)
            assertFalse("description" in data)
            assertTrue(acceptedByTheRule(data))
        }
    }

    @Test
    fun theDefaultCallOfTheOldClients_hasNoDescription() {
        val data = UpdateWrites.photo(miffId, uid, photo, place, T0).data
        assertEquals(required, data.keys)
    }

    @Test
    fun withADescription_theKeyIsThere_asACleanedText_neverNull_neverEmpty() {
        val data = write("  Bien   arrivé \n ").data
        assertEquals(allowed, data.keys)
        assertEquals("Bien arrivé", data["description"])
        assertTrue(acceptedByTheRule(data))
        assertFalse(data.values.any { it == null })
    }

    @Test
    fun theLimitIs140Units_anythingLongerNeverReachesTheRules() {
        assertEquals("a".repeat(140), write("a".repeat(140)).data["description"])
        val longer = write("a".repeat(500)).data
        assertTrue(acceptedByTheRule(longer))
        assertEquals(140, (longer["description"] as String).length)
    }

    @Test
    fun theSixKeysOfThePhoto_areNeverTouchedByTheDescription() {
        val with = write("Coucou").data
        val without = write(null).data
        required.forEach {
            assertTrue("$it is still there", it in with)
        }
        assertEquals(without["lat"], with["lat"])
        assertEquals(without["lng"], with["lng"])
        assertEquals(without["measuredAt"], with["measuredAt"])
        assertEquals(without["expiresAt"], with["expiresAt"])
    }

    @Test
    fun theLotWritesTheSameTextInEveryMiff_andTheStatusNeverCarriesIt() {
        val miffs = listOf("MiffAAAAAAAAAAAAAAAA", "MiffBBBBBBBBBBBBBBBB")
        val lot =
            miffs.flatMap {
                UpdateWrites.publish(it, uid, photo, place, T0, 80, DEVICE_A, description = "Même texte")
            }
        val photos = lot.filter { it.path.contains("/photos/") }
        assertEquals(2, photos.size)
        photos.forEach { assertEquals("Même texte", it.data["description"]) }
        // 2 writes per Miff, no more: the description rides in the document of the photo.
        assertEquals(4, lot.size)
        val statuses = lot.filter { it.path.contains("/status/") }
        statuses.forEach { status ->
            assertFalse("the status holds no description", status.data.keys.any { it.contains("escription") })
            assertEquals("updateText stays reserved: null", null, status.data["updateText"])
        }
    }

    @Test
    fun aNewPhotoWithoutADescription_isASetOfAWholeDocument_soItNeverInheritsTheOldOne() {
        val first = write("Ancien texte")
        val second = write(null)
        assertEquals(com.tomessa.miff.domain.miff.WriteKind.SET, first.kind)
        assertEquals(com.tomessa.miff.domain.miff.WriteKind.SET, second.kind)
        assertFalse("description" in second.data)
    }

    @Test
    fun theWriteShowsNothingOfTheText() {
        assertFalse(write("secret words").toString().contains("secret"))
    }
}
