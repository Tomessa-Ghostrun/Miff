package com.tomessa.miff.domain.notification

import com.tomessa.miff.domain.permission.PermissionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure rules of NOTIF-P0 that the screens and the planner share: the switches, the state of the permission, the quiet night, the types. */
class NotificationModelsTest {
    private fun snap(
        sdk: Int,
        notifications: Boolean,
    ) = PermissionSnapshot(
        sdk = sdk,
        fineLocation = true,
        coarseLocation = true,
        backgroundLocation = true,
        notifications = notifications,
    )

    // --- Types ---------------------------------------------------------------------------------------------------------------------

    @Test
    fun thereAreSevenTypes_n5IsAbandonedAndAbsent_n7IsTheEndOfMyTrip_n9IsTheNewsPushedByTheServer() {
        assertEquals(listOf("n1", "n2", "n3", "n4", "n6", "n7", "n9"), NotificationType.entries.map { it.code })
        assertNull(NotificationType.fromCode("n5"))
        assertNull(NotificationType.fromCode(null))
        assertEquals(NotificationType.N6, NotificationType.fromCode("n6"))
        assertEquals(NotificationType.N7, NotificationType.fromCode("n7"))
    }

    @Test
    fun theTapLeadsToTheUpdateForTheRemindersToMe_andToTheFeedForTheRest() {
        assertEquals(NotificationDestination.UPDATE, NotificationType.N1.destination)
        assertEquals(NotificationDestination.UPDATE, NotificationType.N2.destination)
        assertEquals(NotificationDestination.FEED, NotificationType.N3.destination)
        assertEquals(NotificationDestination.FEED, NotificationType.N4.destination)
        // N6: the Feed, not the Update (an update does not bring a person back to green in a danger zone).
        assertEquals(NotificationDestination.FEED, NotificationType.N6.destination)
    }

    @Test
    fun theNightIsQuietForN1N3N6N9_andTheRedOnesKeepTheirNormalLevel_decisionQ30() {
        assertEquals(
            setOf(NotificationType.N1, NotificationType.N3, NotificationType.N6, NotificationType.N9),
            NotificationType.entries.filter { it.quietAtNight }.toSet(),
        )
    }

    // --- Preferences ----------------------------------------------------------------------------------------------------------------

    @Test
    fun everySwitchIsOnByDefault() {
        val p = NotificationPreferences()
        assertTrue(p.enabled && p.nightCalm)
        NotificationType.entries.forEach { assertTrue("$it", p.allows(it)) }
    }

    @Test
    fun theGeneralSwitchOffStopsEveryType_butKeepsTheirValues() {
        val p = NotificationPreferences().with(NotificationType.N3, false).copy(enabled = false)
        NotificationType.entries.forEach { assertFalse("$it", p.allows(it)) }
        // The values of the types are kept (only greyed): turning the general switch back on restores the choice.
        assertTrue(p.switchOf(NotificationType.N1))
        assertFalse(p.switchOf(NotificationType.N3))
        val back = p.copy(enabled = true)
        assertTrue(back.allows(NotificationType.N1))
        assertFalse(back.allows(NotificationType.N3))
    }

    @Test
    fun aTypeSwitchOnlyChangesItsOwnType() {
        NotificationType.entries.forEach { off ->
            val p = NotificationPreferences().with(off, false)
            NotificationType.entries.forEach { other -> assertEquals("$off/$other", other != off, p.allows(other)) }
        }
    }

    // --- State of the permission ----------------------------------------------------------------------------------------------------

    @Test
    fun allowedWhenThePhoneAllows_whateverTheMemory() {
        assertEquals(
            NotificationPermissionState.ALLOWED,
            NotificationPermissionState.resolve(snap(34, true), systemAsked = false),
        )
        assertEquals(
            NotificationPermissionState.ALLOWED,
            NotificationPermissionState.resolve(snap(34, true), systemAsked = true),
        )
        assertEquals(
            NotificationPermissionState.ALLOWED,
            NotificationPermissionState.resolve(snap(30, true), systemAsked = false),
        )
    }

    @Test
    fun android13Plus_neverAsked_isNotAsked_andAfterTheWindowAndARefusalIsOff() {
        assertEquals(
            NotificationPermissionState.NOT_ASKED,
            NotificationPermissionState.resolve(snap(33, false), systemAsked = false),
        )
        assertEquals(
            NotificationPermissionState.OFF,
            NotificationPermissionState.resolve(snap(33, false), systemAsked = true),
        )
        assertEquals(
            NotificationPermissionState.NOT_ASKED,
            NotificationPermissionState.resolve(snap(36, false), systemAsked = false),
        )
    }

    @Test
    fun android12AndBefore_thereIsNoWindow_soWhatIsNotAllowedWasTurnedOffByThePerson() {
        assertEquals(
            NotificationPermissionState.OFF,
            NotificationPermissionState.resolve(snap(32, false), systemAsked = false),
        )
        assertEquals(
            NotificationPermissionState.OFF,
            NotificationPermissionState.resolve(snap(26, false), systemAsked = false),
        )
    }

    @Test
    fun theActionOfTheButtonFollowsTheState() {
        assertEquals(AuthorizationAction.NONE, AuthorizationAction.of(NotificationPermissionState.ALLOWED))
        assertEquals(AuthorizationAction.ASK_SYSTEM, AuthorizationAction.of(NotificationPermissionState.NOT_ASKED))
        assertEquals(AuthorizationAction.OPEN_PHONE_SETTINGS, AuthorizationAction.of(NotificationPermissionState.OFF))
    }

    // --- Quiet night ----------------------------------------------------------------------------------------------------------------

    @Test
    fun theNightIs22hTo7h_atTheBounds() {
        assertFalse(NightCalm.isNight(21))
        assertTrue(NightCalm.isNight(22))
        assertTrue(NightCalm.isNight(23))
        assertTrue(NightCalm.isNight(0))
        assertTrue(NightCalm.isNight(6))
        assertFalse(NightCalm.isNight(7))
        assertFalse(NightCalm.isNight(12))
    }

    @Test
    fun atNight_n1n3n6n9AreQuiet_n2n4AreNot_andTheSwitchOffMakesNothingQuiet() {
        val on = NotificationPreferences()
        val quiet = NotificationType.entries.filter { NightCalm.isQuiet(it, on, localHour = 2) }.toSet()
        assertEquals(setOf(NotificationType.N1, NotificationType.N3, NotificationType.N6, NotificationType.N9), quiet)
        NotificationType.entries.forEach { assertFalse("$it by day", NightCalm.isQuiet(it, on, localHour = 14)) }
        val off = on.copy(nightCalm = false)
        NotificationType.entries.forEach {
            assertFalse(
                "$it with the switch off",
                NightCalm.isQuiet(it, off, localHour = 2),
            )
        }
    }
}
