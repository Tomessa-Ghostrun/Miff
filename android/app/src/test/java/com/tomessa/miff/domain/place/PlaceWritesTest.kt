package com.tomessa.miff.domain.place

import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.WriteKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lots of the places are the CONTRACT with `backend/firestore.rules` of PR #54 (branch backend/regles-lieux, not deployed yet):
 * `validPlaceShape` (all 11 keys, no other), `validPlaceCreate` (createdBy = uid, createdAt = updatedAt = server time),
 * `validPlaceUpdate` (only name, type, category, address, lat, lng, radiusM, priority, updatedAt), N <= 5 per lot, 20 reads per lot.
 */
class PlaceWritesTest {
    private val uid = "u1"
    private val placeId = "AbCdEfGhIjKlMnOpQrSt"

    // The keys of validPlaceShape, copied from the rules of PR #54.
    private val shapeKeys =
        setOf(
            "name",
            "type",
            "category",
            "address",
            "lat",
            "lng",
            "radiusM",
            "priority",
            "createdBy",
            "createdAt",
            "updatedAt",
        )
    private val updatableKeys =
        setOf("name", "type", "category", "address", "lat", "lng", "radiusM", "priority", "updatedAt")

    private fun draft(
        miffIds: List<String> = listOf("m1"),
        type: PlaceType = PlaceType.TRUSTED,
        name: String = "  Maison du père de Nathan ",
    ) = PlaceDraft(
        name = name,
        miffIds = miffIds,
        address = "17 Avenue d'Italie, 75013 Paris, France",
        lat = 48.8292675,
        lng = 2.3567938,
        radiusM = 150,
        type = type,
        category = PlaceCategory.RELATIVE,
    )

    @Test
    fun create_writesEveryKeyAndNoOther_inEachMiff_withTheSameId() {
        val writes = PlaceWrites.create(uid, placeId, draft(listOf("m1", "m2", "m3")))
        assertEquals(3, writes.size)
        assertEquals(
            listOf("miffs/m1/places/$placeId", "miffs/m2/places/$placeId", "miffs/m3/places/$placeId"),
            writes.map { it.path },
        )
        writes.forEach { write ->
            assertEquals(WriteKind.SET, write.kind)
            assertEquals(shapeKeys, write.data.keys)
        }
    }

    @Test
    fun create_valuesFollowTheRules() {
        val data = PlaceWrites.create(uid, placeId, draft()).single().data
        assertEquals("Maison du père de Nathan", data["name"])
        assertEquals("trusted", data["type"])
        assertEquals("relative", data["category"])
        assertEquals("17 Avenue d'Italie, 75013 Paris, France", data["address"])
        assertEquals(48.82927, data["lat"])
        assertEquals(2.35679, data["lng"])
        assertEquals(150, data["radiusM"])
        assertNull(data["priority"])
        assertEquals(uid, data["createdBy"])
        // The dates are the server's (a FieldValue), never a date of the phone.
        assertNotNull(data["createdAt"])
        assertNotNull(data["updatedAt"])
        assertFalse(data["createdAt"] is Number || data["createdAt"] is String)
    }

    @Test
    fun create_aDangerZone_writesTheTechnicalValueRisk() {
        assertEquals("risk", PlaceWrites.create(uid, placeId, draft(type = PlaceType.RISK)).single().data["type"])
    }

    @Test
    fun update_touchesOnlyTheFieldsTheRulesAllow() {
        val fields = PlaceWrites.updateFields(draft())
        assertEquals(updatableKeys - "priority", fields.keys)
        assertFalse(fields.containsKey("createdBy"))
        assertFalse(fields.containsKey("createdAt"))
    }

    @Test
    fun change_keptAreUpdated_addedAreCreated_removedAreDeleted_inOneLot() {
        val lots =
            PlaceWrites.change(
                uid = uid,
                placeId = placeId,
                currentMiffIds = listOf("m1", "m2"),
                draft = draft(listOf("m2", "m3")),
                typeChangedToRisk = false,
                addedNeedPremium = true,
            )
        assertEquals(1, lots.size)
        val byPath = lots.single().associateBy { it.path }
        assertEquals(WriteKind.UPDATE, byPath.getValue("miffs/m2/places/$placeId").kind)
        assertEquals(WriteKind.SET, byPath.getValue("miffs/m3/places/$placeId").kind)
        assertEquals(shapeKeys, byPath.getValue("miffs/m3/places/$placeId").data.keys)
        assertEquals(WriteKind.DELETE, byPath.getValue("miffs/m1/places/$placeId").kind)
        assertEquals(3, byPath.size)
    }

    @Test
    fun change_aLotThatWouldReadMoreThanTwentyDocuments_isCutInTwo_changesFirst() {
        // 5 Miffs added (3 reads each: owner check 2 + Miff+ 1) and 5 removed (2 each) = 25 > 20.
        val lots =
            PlaceWrites.change(
                uid = uid,
                placeId = placeId,
                currentMiffIds = listOf("a", "b", "c", "d", "e"),
                draft = draft(listOf("f", "g", "h", "i", "j")),
                typeChangedToRisk = false,
                addedNeedPremium = true,
            )
        assertEquals(2, lots.size)
        assertTrue(lots[0].all { it.kind == WriteKind.SET })
        assertTrue(lots[1].all { it.kind == WriteKind.DELETE })
        assertEquals(5, lots[0].size)
        assertEquals(5, lots[1].size)
    }

    @Test
    fun change_withinTheBudget_staysOneAtomicLot() {
        val lots =
            PlaceWrites.change(
                uid = uid,
                placeId = placeId,
                currentMiffIds = listOf("a", "b", "c", "d", "e"),
                draft = draft(listOf("a", "b", "c", "d", "e"), type = PlaceType.RISK),
                typeChangedToRisk = true,
                addedNeedPremium = false,
            )
        // 5 updates that make the place a danger zone: (2 + 1) x 5 = 15 reads.
        assertEquals(1, lots.size)
        assertEquals(5, lots.single().size)
    }

    @Test
    fun delete_isOneLot_inEveryMiff() {
        val writes = PlaceWrites.delete(placeId, listOf("m1", "m2"))
        assertEquals(2, writes.size)
        assertTrue(writes.all { it.kind == WriteKind.DELETE && it.data.isEmpty() })
    }

    @Test
    fun budget_fiveMiffsAtMiffPlus_isFifteenOfTwenty() {
        assertEquals(15, PlaceWrites.createReads(PlaceLimits.MAX_MIFFS, needsPremium = true))
        assertEquals(10, PlaceWrites.createReads(PlaceLimits.MAX_MIFFS, needsPremium = false))
        assertTrue(PlaceWrites.createReads(PlaceLimits.MAX_MIFFS, needsPremium = true) <= PlaceLimits.RULE_READ_BUDGET)
    }

    @Test
    fun aWriteNeverShowsTheDataInItsToString() {
        val write: DocWrite = PlaceWrites.create(uid, placeId, draft()).single()
        assertFalse(write.toString().contains("Maison"))
        assertFalse(write.toString().contains("Avenue"))
    }
}
