package com.tomessa.miff.data.notification

import com.tomessa.miff.domain.feed.PlaceKind
import com.tomessa.miff.domain.notification.ItemState
import com.tomessa.miff.domain.notification.LastVisit
import com.tomessa.miff.domain.notification.NotificationItem
import com.tomessa.miff.domain.notification.NotificationType
import com.tomessa.miff.domain.notification.VisitKind
import com.tomessa.miff.domain.notification.VisitMember
import com.tomessa.miff.domain.notification.VisitState
import com.tomessa.miff.domain.tracking.PresenceValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Depuis ta dernière visite", the part that lives on the phone (ticket notifications-p1-derniere-visite.md): WHEN the comparison is made (a cold start, or back after 30 minutes
 * or more), that what was seen follows the screen and survives a forced close (C1-T20), that the group of the centre is replaced, and what is erased and when (C1-8).
 */
class LastVisitTrackerTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private var now = 1_800_000_000_000L

    private class MemoryVisitStore(
        var memory: LastVisitMemory = LastVisitMemory(),
    ) : LastVisitStore {
        var saves = 0
        var clears = 0

        override fun load() = memory

        override fun save(memory: LastVisitMemory) {
            this.memory = memory
            saves++
        }

        override fun clear() {
            memory = LastVisitMemory()
            clears++
        }
    }

    private class MemoryCenterStore : NotificationCenterStore {
        var items: List<NotificationItem> = emptyList()

        override fun load() = items

        override fun save(items: List<NotificationItem>) {
            this.items = items
        }

        override fun clear() {
            items = emptyList()
        }
    }

    private val visitStore = MemoryVisitStore()
    private val centerStore = MemoryCenterStore()
    private val trayCancelled = mutableListOf<String>()
    private var sessionOk = true

    private fun center() = NotificationCenter(centerStore, nowMillis = { now }, onRead = { trayCancelled += it })

    private fun tracker(
        center: NotificationCenter = center(),
        store: LastVisitStore = visitStore,
    ) = LastVisitTracker(store, center, sessionValid = { sessionOk }, nowMillis = { now })

    private fun member(
        uid: String,
        presence: PresenceValue = PresenceValue.IN_TRUSTED,
        presenceAt: Long,
        place: PlaceKind? = PlaceKind.SCHOOL,
        lastUpdateAt: Long = presenceAt,
    ) = VisitMember(uid, false, presence, place, presenceAt, lastUpdateAt, silent = false)

    private fun picture(
        vararg members: VisitMember,
        miffId: String = "miff-1",
    ) = VisitState(miffId, members.toList())

    private fun lines(center: NotificationCenter) = center.items.value.filter { it.type == NotificationType.C1 }

    /** Thomas is at school at the last visit; he has left by the next one. */
    private val atSchool get() = picture(member("t", presenceAt = now - 6 * hour))
    private val leftSchool get() =
        picture(
            member(
                "t",
                PresenceValue.OUT,
                presenceAt = now - hour,
                lastUpdateAt =
                    now - 6 * hour,
            ),
        )

    @Test
    fun theFirstVisitOnThePhone_comparesNothing_butKeepsWhatItSaw() {
        val c = center()
        val t = tracker(c)
        t.onFreshPicture("me", atSchool)
        assertTrue(lines(c).isEmpty())
        assertEquals(1, visitStore.memory.states.size)
        assertEquals("miff-1", visitStore.memory.lastMiffId)
    }

    @Test
    fun aColdStart_isAnOpening_theNextFreshPictureIsCompared_andTheLineIsUnreadAndSilent() {
        val seen = atSchool
        visitStore.memory = LastVisitMemory(mapOf("miff-1" to seen), "miff-1")
        val c = center()
        tracker(c).onFreshPicture("me", leftSchool)
        val line = lines(c).single()
        assertTrue("unread, so the dot of the bell shows (decision Q2)", line.unread)
        assertEquals("silent: no notification of the system, no sound (C1-1)", ItemState.SILENT, line.state)
        assertEquals(VisitKind.DEPARTURE, LastVisit.parse(line)!!.kind)
        assertEquals(1, c.unreadCount())
        // Nothing goes to the tray: a line of this group never had a notification.
        assertTrue(trayCancelled.isEmpty())
    }

    @Test
    fun theComparisonIsMadeOnce_thenTheStoredPictureFollowsTheScreen() {
        visitStore.memory = LastVisitMemory(mapOf("miff-1" to atSchool), "miff-1")
        val c = center()
        val t = tracker(c)
        t.onFreshPicture("me", leftSchool)
        assertEquals(1, lines(c).size)
        // Same visit, the picture moves on (he arrives home): not an opening, nothing is told, what was seen is kept.
        val home = picture(member("t", presenceAt = now - 10 * minute, place = PlaceKind.HOME))
        t.onFreshPicture("me", home)
        assertEquals("the group of this opening stays as it is", 1, lines(c).size)
        assertEquals(home, visitStore.memory.states.getValue("miff-1"))
    }

    @Test
    fun anAbsenceUnder30Minutes_isNotAnOpening_andTheStateKeepsFollowingTheScreen() {
        val c = center()
        val t = tracker(c)
        t.onFreshPicture("me", atSchool)
        t.onBackground()
        now += 29 * minute
        t.onForeground()
        t.onFreshPicture("me", leftSchool)
        assertTrue(lines(c).isEmpty())
        assertEquals(leftSchool, visitStore.memory.states.getValue("miff-1"))
    }

    @Test
    fun anAbsenceOf30MinutesOrMore_isAnOpening() {
        val c = center()
        val t = tracker(c)
        t.onFreshPicture("me", atSchool)
        t.onBackground()
        now += 30 * minute
        t.onForeground()
        t.onFreshPicture("me", leftSchool)
        assertEquals(VisitKind.DEPARTURE, LastVisit.parse(lines(c).single())!!.kind)
    }

    @Test
    fun aForcedClose_keepsWhatWasOnTheScreen_c1T20() {
        // The Feed showed "at school"; the system kills the process; the next process starts cold on the same files.
        val first = tracker(center())
        first.onFreshPicture("me", atSchool)
        val c2 = center()
        tracker(c2, MemoryVisitStore(visitStore.memory)).onFreshPicture("me", leftSchool)
        assertEquals(VisitKind.DEPARTURE, LastVisit.parse(lines(c2).single())!!.kind)
    }

    @Test
    fun theGroupIsReplacedAtTheNextComparison_orRemovedWhenThereIsNothingNew() {
        visitStore.memory = LastVisitMemory(mapOf("miff-1" to atSchool), "miff-1")
        val c = center()
        val t = tracker(c)
        t.onFreshPicture("me", leftSchool)
        val first = lines(c).single().id
        // Next opening: the person gave news since; the old line is gone, the new one is the only one.
        t.onBackground()
        now += hour
        t.onForeground()
        val news = picture(member("t", PresenceValue.OUT, presenceAt = now - 3 * hour, lastUpdateAt = now - 5 * minute))
        t.onFreshPicture("me", news)
        val second = lines(c).single()
        assertEquals(VisitKind.UPDATE, LastVisit.parse(second)!!.kind)
        assertFalse(second.id == first)
        // Next opening again, nothing new: the group disappears (never a line that is days old).
        t.onBackground()
        now += hour
        t.onForeground()
        t.onFreshPicture("me", news)
        assertTrue(lines(c).isEmpty())
    }

    @Test
    fun neverMoreThanThreeLines_andTheOtherLinesOfTheCentreAreNotTouched() {
        val c = center()
        c.add(NotificationItem("n1|miff-1|u|1", NotificationType.N1, "miff-1", now - hour))
        val seen = picture(*listOf("a", "b", "c", "d").map { member(it, presenceAt = now - 9 * hour) }.toTypedArray())
        val current =
            picture(
                *listOf("a" to 4, "b" to 3, "c" to 2, "d" to 1)
                    .map { (uid, ago) ->
                        member(
                            uid,
                            PresenceValue.OUT,
                            presenceAt = now - ago * hour,
                            lastUpdateAt =
                                now - 9 * hour,
                        )
                    }.toTypedArray(),
            )
        visitStore.memory = LastVisitMemory(mapOf("miff-1" to seen), "miff-1")
        tracker(c).onFreshPicture("me", current)
        assertEquals(listOf("d", "c", "b"), lines(c).map { LastVisit.parse(it)!!.uid }.let { it })
        assertEquals(1, c.items.value.count { it.type == NotificationType.N1 })
    }

    @Test
    fun theFirstVisitAfterAChangeOfActiveMiff_comparesNothing_butEachMiffKeepsItsPicture_c1T17() {
        val other = picture(member("t", presenceAt = now - 30 * hour), miffId = "miff-2")
        visitStore.memory = LastVisitMemory(mapOf("miff-2" to other), "miff-2")
        val c = center()
        val t = tracker(c)
        // The Feed now shows miff-1, whose picture is days old: nothing is compared.
        t.onFreshPicture("me", atSchool)
        assertTrue(lines(c).isEmpty())
        assertEquals(setOf("miff-1", "miff-2"), visitStore.memory.states.keys)
        assertEquals(other, visitStore.memory.states.getValue("miff-2"))
        assertEquals("miff-1", visitStore.memory.lastMiffId)
    }

    @Test
    fun aMemberWhoLeftTheMiff_loosesHisLine() {
        visitStore.memory = LastVisitMemory(mapOf("miff-1" to atSchool), "miff-1")
        val c = center()
        val t = tracker(c)
        t.onFreshPicture("me", leftSchool)
        assertEquals(1, lines(c).size)
        // The Feed now says Thomas is no longer in the Miff.
        t.onFreshPicture("me", picture(member("a", presenceAt = now - hour)))
        assertTrue(lines(c).isEmpty())
        assertEquals(
            listOf("a"),
            visitStore.memory.states
                .getValue("miff-1")
                .members
                .map { it.uid },
        )
    }

    @Test
    fun aMiffThatIsGone_losesItsPictureAndItsLines_andTheOthersStay() {
        visitStore.memory =
            LastVisitMemory(
                mapOf(
                    "miff-1" to atSchool,
                    "miff-2" to picture(member("t", presenceAt = now), miffId = "miff-2"),
                ),
                "miff-1",
            )
        val c = center()
        val t = tracker(c)
        t.onFreshPicture("me", leftSchool)
        t.onMiffGone("miff-1")
        assertTrue(lines(c).isEmpty())
        assertEquals(setOf("miff-2"), visitStore.memory.states.keys)
        assertNull(visitStore.memory.lastMiffId)
        // The list of the Miffs of the person no longer holds miff-2: forgotten as well.
        t.onMiffsKnown(emptySet())
        assertTrue(visitStore.memory.states.isEmpty())
    }

    @Test
    fun theSignOut_forgetsEverything_andTheNextSessionStartsAsAFirstVisit_c1_8() {
        visitStore.memory = LastVisitMemory(mapOf("miff-1" to atSchool), "miff-1")
        val c = center()
        val t = tracker(c)
        t.purge()
        c.purge()
        assertEquals(1, visitStore.clears)
        assertTrue(visitStore.memory.states.isEmpty())
        t.onFreshPicture("me", leftSchool)
        assertTrue("a first visit compares nothing", lines(c).isEmpty())
    }

    @Test
    fun aCallForASessionThatIsNoLongerValid_writesNothing() {
        sessionOk = false
        val c = center()
        tracker(c).onFreshPicture("gone", leftSchool)
        assertEquals(0, visitStore.saves)
        assertTrue(lines(c).isEmpty())
    }

    @Test
    fun theSamePictureAgain_writesNothingMore() {
        val t = tracker()
        t.onFreshPicture("me", atSchool)
        t.onFreshPicture("me", atSchool)
        t.onFreshPicture("me", atSchool)
        assertEquals("the minute that passes costs no write", 1, visitStore.saves)
    }
}
