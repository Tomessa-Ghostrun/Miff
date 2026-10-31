package com.tomessa.miff.domain.miff

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lots of ticket Miff 5.4, 5.7 and 5.13, against the contract of backend/firestore.rules and data-model.md section 8:
 * the exact set of keys of each document (the rules refuse a missing key and an unknown one), the dates of the server, the
 * explicit consent written with the entry, no coordinate anywhere (criteria C4, K2, K5, K6, V1).
 */
class MiffWritesTest {
    private val uid = "uid-1"
    private val miffId = "AbCdEfGhIjKlMnOpQrSt"
    private val code = "XTZRSP367988"
    private val profile = ProfileCopy("Léa", "Martin", "+33612345678", ByteArray(100) { 1 })
    private val serverTime = FieldValue.serverTimestamp()

    private fun List<DocWrite>.at(path: String) = single { it.path == path }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.sharing() = this["sharing"] as Map<String, Any?>

    // --- Create ---------------------------------------------------------------------------------------------------

    @Test
    fun create_isFiveWrites_inOneLot_withTheRightPaths() {
        val writes = MiffWrites.create(uid, miffId, code, "  Chez nous ", profile, "free", LocationAccess.UNKNOWN)
        assertEquals(
            setOf(
                "miffs/$miffId",
                "joinCodes/$code",
                "miffs/$miffId/members/$uid",
                "users/$uid/memberships/free",
                "miffs/$miffId/status/$uid",
            ),
            writes.map { it.path }.toSet(),
        )
        assertEquals(5, writes.size)
        assertTrue(writes.all { it.kind == WriteKind.SET })
    }

    @Test
    fun create_miffAndCode_haveExactlyTheKeysOfTheRules_andTheTrimmedName() {
        val writes = MiffWrites.create(uid, miffId, code, "  Chez nous ", profile, "free", LocationAccess.UNKNOWN)
        val miff = writes.at("miffs/$miffId").data
        assertEquals(setOf("ownerUid", "name", "joinCode", "createdAt"), miff.keys)
        assertEquals("Chez nous", miff["name"])
        assertEquals(uid, miff["ownerUid"])
        assertEquals(code, miff["joinCode"])
        assertEquals(serverTime, miff["createdAt"])
        val joinCode = writes.at("joinCodes/$code").data
        assertEquals(setOf("miffId", "miffName", "createdAt"), joinCode.keys)
        assertEquals("Chez nous", joinCode["miffName"])
        assertEquals(miffId, joinCode["miffId"])
    }

    @Test
    fun create_ownerCard_hasAllKeys_noJoinCode_andTheExplicitConsentOfTheEntry() {
        val card =
            MiffWrites
                .create(uid, miffId, code, "Chez nous", profile, "free", LocationAccess.UNKNOWN)
                .at("miffs/$miffId/members/$uid")
                .data
        assertEquals(setOf("role", "firstName", "lastName", "avatar", "phone", "joinedAt", "sharing"), card.keys)
        assertEquals("owner", card["role"])
        assertEquals("+33612345678", card["phone"])
        assertEquals(serverTime, card["joinedAt"])
        assertTrue(card["avatar"] is Blob)
        val sharing = card.sharing()
        assertEquals(setOf("enabled", "consentedAt", "consentVersion", "revokedAt"), sharing.keys)
        assertEquals(true, sharing["enabled"])
        assertEquals(serverTime, sharing["consentedAt"])
        assertEquals(MiffSharingConsent.VERSION, sharing["consentVersion"])
        assertNull(sharing["revokedAt"])
    }

    @Test
    fun create_cardWithoutPhoto_hasNoAvatarKey_notANullOne() {
        val noPhoto = ProfileCopy("Léa", "Martin", "+33612345678", null)
        val card =
            MiffWrites
                .create(uid, miffId, code, "Chez nous", noPhoto, "free", LocationAccess.UNKNOWN)
                .at("miffs/$miffId/members/$uid")
                .data
        assertFalse(card.containsKey("avatar"))
    }

    @Test
    fun create_indexKey_isFreeForAFreeAccount_andTheMiffIdForAPremiumOne() {
        val free = MiffWrites.create(uid, miffId, code, "Chez nous", profile, "free", LocationAccess.UNKNOWN)
        assertEquals(setOf("miffId", "joinedAt"), free.at("users/$uid/memberships/free").data.keys)
        assertEquals(miffId, free.at("users/$uid/memberships/free").data["miffId"])
        val premium = MiffWrites.create(uid, miffId, code, "Chez nous", profile, miffId, LocationAccess.UNKNOWN)
        assertNotNull(premium.at("users/$uid/memberships/$miffId"))
    }

    @Test
    fun create_initialStatus_isUnknown_withTheRealPermission_andNoCoordinate() {
        val status =
            MiffWrites
                .create(uid, miffId, code, "Chez nous", profile, "free", LocationAccess.FOREGROUND)
                .at("miffs/$miffId/status/$uid")
                .data
        assertEquals(
            setOf(
                "presence",
                "placeId",
                "presenceAt",
                "lastUpdateAt",
                "updateText",
                "hasPhoto",
                "batteryPct",
                "locationAccess",
                "escalationDueAt",
                "updatedAt",
            ),
            status.keys,
        )
        assertEquals("unknown", status["presence"])
        assertEquals("foreground", status["locationAccess"])
        assertEquals(false, status["hasPhoto"])
        assertNull(status["placeId"])
        assertNull(status["batteryPct"])
        assertEquals(serverTime, status["updatedAt"])
        assertEquals(serverTime, status["presenceAt"])
        assertEquals(serverTime, status["lastUpdateAt"])
    }

    @Test
    fun noLotEverWritesACoordinateAnAddressOrAPlaceName() {
        val forbidden =
            setOf("lat", "lng", "lon", "latitude", "longitude", "address", "coords", "coordinates", "position")
        val all =
            MiffWrites.create(uid, miffId, code, "Chez nous", profile, "free", LocationAccess.UNKNOWN) +
                MiffWrites.join(uid, miffId, code, profile, "free", LocationAccess.UNKNOWN) +
                MiffWrites.rename(miffId, code, "Nouveau") +
                MiffWrites.stopSharing(miffId, uid) +
                MiffWrites.resumeSharing(miffId, uid, LocationAccess.UNKNOWN) +
                MiffWrites.leave(miffId, uid, "free")
        all.forEach { write ->
            write.data.keys.forEach { key -> assertFalse("$key", key.lowercase() in forbidden) }
        }
    }

    @Test
    fun create_refusesABadNameOrCode_beforeAnythingIsWritten() {
        runCatching { MiffWrites.create(uid, miffId, code, "a", profile, "free", LocationAccess.UNKNOWN) }
            .also { assertTrue(it.exceptionOrNull() is IllegalArgumentException) }
        runCatching {
            MiffWrites.create(
                uid,
                miffId,
                "XTZRSP36798I",
                "Chez nous",
                profile,
                "free",
                LocationAccess.UNKNOWN,
            )
        }.also { assertTrue(it.exceptionOrNull() is IllegalArgumentException) }
    }

    // --- Join -----------------------------------------------------------------------------------------------------

    @Test
    fun join_isThreeWrites_cardWithTheCode_index_andInitialStatus() {
        val writes = MiffWrites.join(uid, miffId, code, profile, "free", LocationAccess.DENIED)
        assertEquals(
            setOf("miffs/$miffId/members/$uid", "users/$uid/memberships/free", "miffs/$miffId/status/$uid"),
            writes.map { it.path }.toSet(),
        )
        assertEquals(3, writes.size)
        val card = writes.at("miffs/$miffId/members/$uid").data
        assertEquals(
            setOf("role", "firstName", "lastName", "avatar", "phone", "joinedAt", "joinCode", "sharing"),
            card.keys,
        )
        assertEquals("member", card["role"])
        assertEquals(code, card["joinCode"])
        assertEquals(true, card.sharing()["enabled"])
        assertEquals(MiffSharingConsent.VERSION, card.sharing()["consentVersion"])
        assertEquals("denied", writes.at("miffs/$miffId/status/$uid").data["locationAccess"])
    }

    @Test
    fun consentVersion_fitsTheRules_oneToTwentyCharacters() {
        assertTrue(MiffSharingConsent.VERSION.length in 1..20)
    }

    // --- Rename, sharing ------------------------------------------------------------------------------------------

    @Test
    fun rename_updatesTheMiffAndTheNameKeptWithTheCode_inTheSameLot() {
        val writes = MiffWrites.rename(miffId, code, " Nouvelle  Miff ")
        assertEquals(2, writes.size)
        assertEquals(mapOf<String, Any?>("name" to "Nouvelle  Miff"), writes.at("miffs/$miffId").data)
        assertEquals(mapOf<String, Any?>("miffName" to "Nouvelle  Miff"), writes.at("joinCodes/$code").data)
        assertTrue(writes.all { it.kind == WriteKind.UPDATE })
    }

    @Test
    fun stopSharing_marksTheCard_andDeletesTheStatusAndThePhoto_inTheSameLot() {
        val writes = MiffWrites.stopSharing(miffId, uid)
        assertEquals(3, writes.size)
        val card = writes.at("miffs/$miffId/members/$uid")
        assertEquals(WriteKind.UPDATE, card.kind)
        // Only two sub-fields change: the date and version of the first consent are kept as they were.
        assertEquals(setOf("sharing.enabled", "sharing.revokedAt"), card.data.keys)
        assertEquals(false, card.data["sharing.enabled"])
        assertEquals(serverTime, card.data["sharing.revokedAt"])
        assertEquals(WriteKind.DELETE, writes.at("miffs/$miffId/status/$uid").kind)
        assertEquals(WriteKind.DELETE, writes.at("miffs/$miffId/photos/$uid").kind)
    }

    @Test
    fun resumeSharing_writesANewConsent_andTheInitialStatus() {
        val writes = MiffWrites.resumeSharing(miffId, uid, LocationAccess.BACKGROUND)
        assertEquals(2, writes.size)
        val card = writes.at("miffs/$miffId/members/$uid")
        assertEquals(WriteKind.UPDATE, card.kind)
        assertEquals(true, card.data["sharing.enabled"])
        assertEquals(serverTime, card.data["sharing.consentedAt"])
        assertEquals(MiffSharingConsent.VERSION, card.data["sharing.consentVersion"])
        assertNull(card.data["sharing.revokedAt"])
        assertTrue(card.data.containsKey("sharing.revokedAt"))
        val status = writes.at("miffs/$miffId/status/$uid")
        assertEquals(WriteKind.SET, status.kind)
        assertEquals("unknown", status.data["presence"])
        assertEquals("background", status.data["locationAccess"])
    }

    // --- Clean-up -------------------------------------------------------------------------------------------------

    @Test
    fun leave_isFourDeletions_andDropIndexIsOne() {
        val leave = MiffWrites.leave(miffId, uid, "free")
        assertEquals(4, leave.size)
        assertTrue(leave.all { it.kind == WriteKind.DELETE })
        assertEquals(
            setOf(
                "miffs/$miffId/members/$uid",
                "miffs/$miffId/status/$uid",
                "miffs/$miffId/photos/$uid",
                "users/$uid/memberships/free",
            ),
            leave.map { it.path }.toSet(),
        )
        val drop = MiffWrites.dropIndex(uid, "free")
        assertEquals(listOf("users/$uid/memberships/free"), drop.map { it.path })
        assertEquals(WriteKind.DELETE, drop.single().kind)
    }

    // --- Profile copy and failures --------------------------------------------------------------------------------

    @Test
    fun profileCopy_needsNamesAndAValidPhone() {
        val ok = mapOf<String, Any?>("firstName" to "Léa", "lastName" to "Martin", "phone" to "+33612345678")
        assertNotNull(ProfileCopy.from(ok))
        assertNull(ProfileCopy.from(null))
        assertNull(ProfileCopy.from(ok - "phone"))
        assertNull(ProfileCopy.from(ok + ("phone" to "0612345678")))
        assertNull(ProfileCopy.from(ok - "firstName"))
        // An empty photo is no photo; one over 20 000 bytes is dropped (the rules would refuse the card).
        assertNull(ProfileCopy.from(ok + ("avatar" to ByteArray(0)))!!.avatarJpeg)
        assertNull(ProfileCopy.from(ok + ("avatar" to ByteArray(20_001)))!!.avatarJpeg)
        assertNotNull(ProfileCopy.from(ok + ("avatar" to ByteArray(20_000)))!!.avatarJpeg)
    }

    @Test
    fun profileCopy_toString_showsNothing() {
        assertFalse(profile.toString().contains("Léa"))
        assertFalse(profile.toString().contains("3361"))
    }

    @Test
    fun errors_areReducedToACalmCategory_andANetworkCutIsNeverALossOfAccess() {
        fun error(code: FirebaseFirestoreException.Code) = FirebaseFirestoreException("x", code)
        assertEquals(MiffFailure.OFFLINE, interpretMiffError(error(FirebaseFirestoreException.Code.UNAVAILABLE)))
        assertEquals(MiffFailure.OFFLINE, interpretMiffError(error(FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)))
        // "Quota" is said as "no connection" (criterion H4).
        assertEquals(MiffFailure.OFFLINE, interpretMiffError(error(FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED)))
        assertEquals(MiffFailure.REFUSED, interpretMiffError(error(FirebaseFirestoreException.Code.PERMISSION_DENIED)))
        assertEquals(MiffFailure.UNKNOWN, interpretMiffError(IllegalStateException("x")))
        assertTrue(isAccessLostError(error(FirebaseFirestoreException.Code.PERMISSION_DENIED)))
        assertFalse(isAccessLostError(error(FirebaseFirestoreException.Code.UNAVAILABLE)))
        assertFalse(isAccessLostError(error(FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)))
        assertFalse(isAccessLostError(IllegalStateException("x")))
    }
}
