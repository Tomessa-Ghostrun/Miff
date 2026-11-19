package com.tomessa.miff.ui.notification

import com.tomessa.miff.data.notification.NotificationAuthorization
import com.tomessa.miff.data.notification.NotificationCenter
import com.tomessa.miff.data.notification.NotificationCenterStore
import com.tomessa.miff.data.notification.NotificationPreferencesStore
import com.tomessa.miff.data.notification.NotificationSettings
import com.tomessa.miff.data.permission.DevicePermissions
import com.tomessa.miff.data.permission.NotificationAskStore
import com.tomessa.miff.domain.notification.ActorFace
import com.tomessa.miff.domain.notification.CenterAvatar
import com.tomessa.miff.domain.notification.FaceLookup
import com.tomessa.miff.domain.notification.ItemState
import com.tomessa.miff.domain.notification.NotificationItem
import com.tomessa.miff.domain.notification.NotificationPreferences
import com.tomessa.miff.domain.notification.NotificationType
import com.tomessa.miff.domain.notification.SecondChance
import com.tomessa.miff.domain.permission.PermissionSnapshot
import com.tomessa.miff.domain.profile.MyProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CNC-A: the round picture and the words of each line of the centre, as the screen model builds them from memory (the twelve vectors of the ticket, 13): a person is her photo or her initials,
 * the whole Miff is the logo, my reminders are my photo, somebody who left is the logo and "Un proche"; a line of a Miff that is gone is not drawn; the faces are asked in the background.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationCenterAvatarsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class Phone : DevicePermissions {
        override fun snapshot() =
            PermissionSnapshot(34, fineLocation = true, coarseLocation = true, backgroundLocation = true, notifications = true)
    }

    private class Asks : NotificationAskStore {
        override fun wasShown(uid: String) = false

        override fun markShown(uid: String) {}

        override fun systemAsked() = true

        override fun markSystemAsked() {}

        override fun secondChance(uid: String) = SecondChance.NONE

        override fun setSecondChance(
            uid: String,
            value: SecondChance,
        ) {}
    }

    private class Prefs : NotificationPreferencesStore {
        var saved = NotificationPreferences()

        override fun load() = saved

        override fun save(preferences: NotificationPreferences) {
            saved = preferences
        }

        override fun clear() {
            saved = NotificationPreferences()
        }
    }

    private class Store : NotificationCenterStore {
        var items = emptyList<NotificationItem>()

        override fun load() = items

        override fun save(items: List<NotificationItem>) {
            this.items = items
        }

        override fun clear() {
            items = emptyList()
        }
    }

    private val now = 1_800_000_000_000L
    private val photo = ByteArray(800) { 3 }
    private val center = NotificationCenter(Store(), nowMillis = { now })
    private val settings = NotificationSettings(Prefs())
    private val faces = mutableMapOf<Pair<String, String>, FaceLookup>()
    private val profile = MutableStateFlow<MyProfile?>(MyProfile("Moi", "Même", null, photo))
    private val changed = MutableStateFlow(0)
    private val asked = mutableListOf<Set<Pair<String, String>>>()
    private var journalSyncs = 0
    private var profileLoads = 0
    private var myMiffs = setOf("miffAAAA", "miffBBBB")

    private fun model(profileFlow: MutableStateFlow<MyProfile?> = profile) =
        NotificationCenterViewModel(
            center = center,
            settings = settings,
            authorization = NotificationAuthorization(Phone(), Asks()),
            miffNames = { emptyMap() },
            faces = { miff, uid -> faces[miff to uid] ?: FaceLookup.Pending },
            facesChanged = changed,
            myProfile = profileFlow,
            ensureFaces = { asked += it.toSet() },
            loadMyProfile = { profileLoads++ },
            syncJournal = { journalSyncs++ },
            isMyMiff = { it in myMiffs },
        )

    private fun add(
        id: String,
        type: NotificationType,
        miff: String? = "miffAAAA",
        who: String? = null,
        at: Long = now,
    ) = center.add(NotificationItem(id, type, miff, at, state = ItemState.SILENT, who = who))

    private fun rows(vm: NotificationCenterViewModel) = vm.uiState.value.rows

    private fun row(
        vm: NotificationCenterViewModel,
        id: String,
    ) = rows(vm).first { it.id == id }

    @Test
    fun aPersonWithAPhoto_hasHerPhotoAndHerFirstName_resolvedFromTheSameCard() =
        runTest(dispatcher) {
            faces["miffAAAA" to "actor1"] = FaceLookup.Known(ActorFace("Léa", "LD", photo))
            add("n9|u|actor1|u-1", NotificationType.N9, who = "Ancien")
            val vm = model()
            advanceUntilIdle()
            val r = row(vm, "n9|u|actor1|u-1")
            assertTrue(r.avatar is CenterAvatar.Photo)
            assertEquals("the name of the card wins over the one the push wrote", "Léa", r.who)
            assertEquals("actor1", r.actorUid)
            assertEquals("miffAAAA", r.miffId)
        }

    @Test
    fun aPersonWithoutAPhoto_hasHerInitials() =
        runTest(dispatcher) {
            faces["miffAAAA" to "actor1"] = FaceLookup.Known(ActorFace("Léa", "LD", null))
            add("n9|u|actor1|u-1", NotificationType.N9)
            val vm = model()
            advanceUntilIdle()
            assertEquals("LD", (row(vm, "n9|u|actor1|u-1").avatar as CenterAvatar.Initials).text)
        }

    @Test
    fun aLineBroughtByTheJournal_holdsNoName_butTheScreenFindsIt() =
        runTest(dispatcher) {
            faces["miffAAAA" to "actor1"] = FaceLookup.Known(ActorFace("Léa", "LD", null))
            add("al|u|actor1|start|as-1", NotificationType.N10, who = null)
            val vm = model()
            advanceUntilIdle()
            assertEquals("Léa", row(vm, "al|u|actor1|start|as-1").who)
        }

    @Test
    fun aMemberWhoLeft_isTheLogoAndUnProche_evenForALineThePushWroteTheNameOf() =
        runTest(dispatcher) {
            faces["miffAAAA" to "gone1"] = FaceLookup.Gone
            add("n9|u|gone1|u-1", NotificationType.N9, who = "Léa")
            add("al|u|gone1|end|ae-1", NotificationType.N11, who = "Léa")
            val vm = model()
            advanceUntilIdle()
            listOf("n9|u|gone1|u-1", "al|u|gone1|end|ae-1").forEach {
                assertSame(CenterAvatar.Logo, row(vm, it).avatar)
                assertNull(row(vm, it).who)
            }
        }

    @Test
    fun theWholeMiff_isTheLogo_myOwnReminders_areMyPhoto() =
        runTest(dispatcher) {
            add("n3|miffAAAA|1", NotificationType.N3)
            add("n4|miffAAAA|1", NotificationType.N4)
            listOf(NotificationType.N1, NotificationType.N2, NotificationType.N6, NotificationType.N7).forEach {
                add("${it.code}|miffAAAA|me|1", it)
            }
            val vm = model()
            advanceUntilIdle()
            assertSame(CenterAvatar.Logo, row(vm, "n3|miffAAAA|1").avatar)
            assertSame(CenterAvatar.Logo, row(vm, "n4|miffAAAA|1").avatar)
            listOf("n1", "n2", "n6", "n7").forEach { code ->
                val avatar = row(vm, "$code|miffAAAA|me|1").avatar
                assertTrue("$code is my photo", avatar is CenterAvatar.Photo)
                assertTrue((avatar as CenterAvatar.Photo).jpeg.contentEquals(photo))
            }
        }

    @Test
    fun myReminders_areMyInitials_withoutAPhoto_andNeutralWhileMyProfileIsNotRead_thenMyProfileIsAskedOnce() =
        runTest(dispatcher) {
            add("n1|miffAAAA|me|1", NotificationType.N1)
            val none = MutableStateFlow<MyProfile?>(null)
            val vm = model(none)
            advanceUntilIdle()
            assertSame(CenterAvatar.Placeholder, row(vm, "n1|miffAAAA|me|1").avatar)
            assertEquals(1, profileLoads)
            none.value = MyProfile("Moi", "Même", null, null)
            advanceUntilIdle()
            assertEquals("MM", (row(vm, "n1|miffAAAA|me|1").avatar as CenterAvatar.Initials).text)
        }

    @Test
    fun withNoReminderOfMine_myProfileIsNotAsked() =
        runTest(dispatcher) {
            add("n3|miffAAAA|1", NotificationType.N3)
            model(MutableStateFlow(null))
            advanceUntilIdle()
            assertEquals(0, profileLoads)
        }

    @Test
    fun twoPeopleMakeTwoLines_eachWithItsOwnPicture_noGrouping() =
        runTest(dispatcher) {
            faces["miffAAAA" to "a1"] = FaceLookup.Known(ActorFace("Léa", "LD", photo))
            faces["miffAAAA" to "a2"] = FaceLookup.Known(ActorFace("Max", "MP", null))
            add("n9|u|a1|u-1", NotificationType.N9)
            add("n9|u|a2|u-2", NotificationType.N9)
            val vm = model()
            advanceUntilIdle()
            assertEquals(2, rows(vm).size)
            assertTrue(row(vm, "n9|u|a1|u-1").avatar is CenterAvatar.Photo)
            assertTrue(row(vm, "n9|u|a2|u-2").avatar is CenterAvatar.Initials)
        }

    @Test
    fun aLineOfAMiffThatIsGone_isNotDrawn_butTheReminderOfThatMiffStays() =
        runTest(dispatcher) {
            add("n9|u|a1|u-1", NotificationType.N9, miff = "miffZZZZ")
            add("al|u|a1|start|as-1", NotificationType.N10, miff = "miffZZZZ")
            add("n9|u|a1|u-2", NotificationType.N9, miff = "miffAAAA")
            add("n1|miffZZZZ|me|1", NotificationType.N1, miff = "miffZZZZ")
            val vm = model()
            advanceUntilIdle()
            assertEquals(setOf("n9|u|a1|u-2", "n1|miffZZZZ|me|1"), rows(vm).map { it.id }.toSet())
        }

    @Test
    fun aLineKeyedByTheMiffAndNotByAPerson_isTheLogo() =
        runTest(dispatcher) {
            add("n9|m|miffAAAA|1700", NotificationType.N9, who = "Léa")
            val vm = model()
            advanceUntilIdle()
            val r = row(vm, "n9|m|miffAAAA|1700")
            assertSame(CenterAvatar.Logo, r.avatar)
            assertNull("no author: no face to resolve", r.actorUid)
            assertEquals("the stored name is the only identity there is", "Léa", r.who)
        }

    @Test
    fun theFacesTheLinesNeedAreAskedInTheBackground_andAskedAgainWhenALineArrives() =
        runTest(dispatcher) {
            add("n9|u|a1|u-1", NotificationType.N9)
            add("n9|u|a1|u-2", NotificationType.N9)
            add("n3|miffAAAA|1", NotificationType.N3)
            model()
            advanceUntilIdle()
            assertEquals(listOf(setOf("miffAAAA" to "a1")), asked)
            add("al|u|a2|start|as-1", NotificationType.N10, miff = "miffBBBB")
            advanceUntilIdle()
            assertEquals(setOf("miffAAAA" to "a1", "miffBBBB" to "a2"), asked.last())
        }

    @Test
    fun aFaceLearnedLater_redrawsTheLine() =
        runTest(dispatcher) {
            add("n9|u|a1|u-1", NotificationType.N9, who = "Léa")
            val vm = model()
            advanceUntilIdle()
            assertSame(CenterAvatar.Placeholder, row(vm, "n9|u|a1|u-1").avatar)
            assertEquals("the name of the push stands in while the card is being read", "Léa", row(vm, "n9|u|a1|u-1").who)
            faces["miffAAAA" to "a1"] = FaceLookup.Known(ActorFace("Léana", "LD", null))
            changed.value++
            advanceUntilIdle()
            assertEquals("Léana", row(vm, "n9|u|a1|u-1").who)
        }

    @Test
    fun theJournalIsReadOnceWhenTheScreenOpens_inTheBackground() =
        runTest(dispatcher) {
            model()
            advanceUntilIdle()
            assertEquals(1, journalSyncs)
        }

    @Test
    fun aFailureOfTheJournalOrOfTheFaces_isNeverAnError() =
        runTest(dispatcher) {
            add("n9|u|a1|u-1", NotificationType.N9)
            val vm =
                NotificationCenterViewModel(
                    center = center,
                    settings = settings,
                    authorization = NotificationAuthorization(Phone(), Asks()),
                    miffNames = { emptyMap() },
                    ensureFaces = { throw IllegalStateException("boom") },
                    syncJournal = { throw IllegalStateException("boom") },
                )
            advanceUntilIdle()
            assertEquals(1, rows(vm).size)
        }

    @Test
    fun theRequesterOfAPlaceAndTheMemberOfAVisitLineHaveTheirFace_tooAndTheMiffLogoWhenTheyAreGone() =
        runTest(dispatcher) {
            faces["miffAAAA" to "uid"] = FaceLookup.Known(ActorFace("Léa", "LD", photo))
            add("n8|miffAAAA|uid_1", NotificationType.N8)
            val vm = model()
            advanceUntilIdle()
            assertTrue(rows(vm).single { it.type == NotificationType.N8 }.avatar is CenterAvatar.Photo)
            faces["miffAAAA" to "uid"] = FaceLookup.Gone
            changed.value++
            advanceUntilIdle()
            assertSame(CenterAvatar.Logo, rows(vm).single { it.type == NotificationType.N8 }.avatar)
        }
}
