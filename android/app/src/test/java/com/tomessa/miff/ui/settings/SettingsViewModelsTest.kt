package com.tomessa.miff.ui.settings

import com.tomessa.miff.data.profile.CurrentProfile
import com.tomessa.miff.data.profile.MyProfileLoad
import com.tomessa.miff.data.profile.MyProfileRepository
import com.tomessa.miff.data.profile.ProfileSaveResult
import com.tomessa.miff.domain.profile.MyProfile
import com.tomessa.miff.domain.settings.AppLanguage
import com.tomessa.miff.testsupport.FakeLanguageStore
import com.tomessa.miff.testsupport.FakeMyProfileRepository
import com.tomessa.miff.ui.profile.ProfileTabStatus
import com.tomessa.miff.ui.profile.ProfileTabViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Profile tab, language and account screens (ticket ONB-3a, criteria A3, A9, B1, B2). */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = FakeMyProfileRepository()
    private var signedInAs: String? = "uid-1"
    private val current = CurrentProfile(repo) { signedInAs }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- The profile kept in memory for the session ---------------------------------------------------------------------------

    @Test
    fun theProfileIsReadOnce_andKeptInMemoryForTheSession() =
        runTest(dispatcher) {
            assertNull(current.profile.value)
            assertTrue(current.refresh())
            assertEquals("Léa Martin", current.profile.value?.displayName)
        }

    @Test
    fun aProfileThatCannotBeReadLeavesNothing() =
        runTest(dispatcher) {
            repo.readResult = MyProfileLoad.Unavailable
            assertFalse(current.refresh())
            assertNull(current.profile.value)
        }

    @Test
    fun nobodySignedInReadsNothing() =
        runTest(dispatcher) {
            signedInAs = null
            assertFalse(current.refresh())
            assertEquals(0, repo.reads)
        }

    @Test
    fun theEndOfASessionForgetsTheProfile() =
        runTest(dispatcher) {
            current.refresh()
            current.stop()
            assertNull("sign-out erases the name and the photo from memory", current.profile.value)
        }

    @Test
    fun anAnswerAwaitedWhileTheSessionEndedBelongsToThePreviousPerson_andIsDropped() =
        runTest(dispatcher) {
            lateinit var holder: CurrentProfile
            val racing =
                object : MyProfileRepository {
                    override suspend fun read(uid: String): MyProfileLoad {
                        holder.stop() // the sign-out happens while the server is being asked
                        return MyProfileLoad.Loaded(MyProfile("Léa", "Martin", "+33612345678", null))
                    }

                    override suspend fun save(
                        uid: String,
                        profile: MyProfile,
                    ) = ProfileSaveResult.SAVED
                }
            holder = CurrentProfile(racing) { "uid-1" }
            assertFalse(holder.refresh())
            assertNull(holder.profile.value)
        }

    @Test
    fun aSaveConfirmedForAnotherAccountIsIgnored() =
        runTest(dispatcher) {
            current.onSaved("someone-else", MyProfile("Zoé", "Roy", "+33612345678", null))
            assertNull(current.profile.value)
            current.onSaved("uid-1", MyProfile("Zoé", "Roy", "+33612345678", null))
            assertEquals("Zoé Roy", current.profile.value?.displayName)
        }

    // --- Profile tab header (criterion A3) ----------------------------------------------------------------------------------------

    @Test
    fun theTabShowsTheNameOnceTheProfileIsRead() =
        runTest(dispatcher) {
            val vm = ProfileTabViewModel(current)
            assertEquals(ProfileTabStatus.LOADING, vm.uiState.value.status)
            advanceUntilIdle()
            val s = vm.uiState.value
            assertEquals(ProfileTabStatus.READY, s.status)
            assertEquals("Léa Martin", s.displayName)
            assertEquals("LM", s.initials)
        }

    @Test
    fun theTabWithAProfileAlreadyKnownShowsItAtOnce_withoutReadingAgain() =
        runTest(dispatcher) {
            current.onSaved("uid-1", MyProfile("Zoé", "Roy", "+33612345678", null))
            val vm = ProfileTabViewModel(current)
            assertEquals(ProfileTabStatus.READY, vm.uiState.value.status)
            advanceUntilIdle()
            assertEquals(0, repo.reads)
        }

    @Test
    fun theTabFollowsTheProfileWhenItIsSavedInMyInformation() =
        runTest(dispatcher) {
            val vm = ProfileTabViewModel(current)
            advanceUntilIdle()
            current.onSaved("uid-1", MyProfile("Léane", "Martin", "+33612345678", null))
            advanceUntilIdle()
            assertEquals("Léane Martin", vm.uiState.value.displayName)
        }

    @Test
    fun anUnreadableProfileIsSaidCalmly_andRetryWorks() =
        runTest(dispatcher) {
            repo.readResult = MyProfileLoad.Unavailable
            val vm = ProfileTabViewModel(current)
            advanceUntilIdle()
            assertEquals(ProfileTabStatus.UNAVAILABLE, vm.uiState.value.status)
            repo.readResult = null
            vm.retry()
            advanceUntilIdle()
            assertEquals(ProfileTabStatus.READY, vm.uiState.value.status)
        }

    // --- Language (criterion A9) -----------------------------------------------------------------------------------------------------

    @Test
    fun aTapAppliesTheLanguageAtOnce_andReadsTheCurrentOneAgain() {
        val store = FakeLanguageStore(AppLanguage.SYSTEM)
        val vm = LanguageViewModel(store)
        assertEquals("the language may have been changed in the settings of the phone", 1, store.refreshes)
        vm.onSelect(AppLanguage.FRENCH)
        assertEquals(listOf(AppLanguage.FRENCH), store.selections)
        assertEquals(AppLanguage.FRENCH, vm.selected.value)
        vm.onSelect(AppLanguage.FRENCH)
        assertEquals("choosing the language already in force does nothing", 1, store.selections.size)
        vm.onSelect(AppLanguage.SYSTEM)
        assertEquals(AppLanguage.SYSTEM, vm.selected.value)
    }

    // --- Account (criteria B1, B2) -----------------------------------------------------------------------------------------------------

    @Test
    fun signingOutAsksFirst_andCancellingChangesNothing() =
        runTest(dispatcher) {
            var signedOut = 0
            val vm = AccountViewModel(email = "lea@example.org") { signedOut++ }
            assertEquals("lea@example.org", vm.uiState.value.email)
            vm.onSignOutRequested()
            assertTrue(vm.uiState.value.confirmingSignOut)
            vm.onSignOutCancelled()
            advanceUntilIdle()
            assertFalse(vm.uiState.value.confirmingSignOut)
            assertEquals(0, signedOut)
        }

    @Test
    fun confirmingSignsOutOnce_evenOnADoubleTap() =
        runTest(dispatcher) {
            var signedOut = 0
            val vm = AccountViewModel(email = null) { signedOut++ }
            vm.onSignOutRequested()
            vm.onSignOutConfirmed()
            vm.onSignOutConfirmed()
            advanceUntilIdle()
            assertEquals(1, signedOut)
            assertTrue(vm.uiState.value.isSigningOut)
            assertFalse(vm.uiState.value.confirmingSignOut)
            vm.onSignOutRequested()
            assertFalse("no second dialog while the sign-out is going on", vm.uiState.value.confirmingSignOut)
        }
}
