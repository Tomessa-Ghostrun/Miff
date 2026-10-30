package com.tomessa.miff.ui.settings

import com.tomessa.miff.data.profile.CurrentProfile
import com.tomessa.miff.data.profile.MyProfileLoad
import com.tomessa.miff.data.profile.ProfileSaveResult
import com.tomessa.miff.domain.profile.MyProfile
import com.tomessa.miff.testsupport.FakeAvatarCodec
import com.tomessa.miff.testsupport.FakeMyProfileRepository
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.SessionHarness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Settings > Mes informations (ticket ONB-3a, criteria A5 to A8): the button follows what changed, the names follow the
 * rules of the Onboarding (1 to 50 characters), the number is mandatory and stored in E.164, the save is ONE confirmed
 * write and nothing is queued offline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MyInfoViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val sessionScope = CoroutineScope(dispatcher + SupervisorJob())

    private val repo = FakeMyProfileRepository()
    private val codec = FakeAvatarCodec()
    private val network = FakeNetwork()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        sessionScope.cancel()
        Dispatchers.resetMain()
    }

    private class Env(
        val harness: SessionHarness,
        val current: CurrentProfile,
    )

    private fun TestScope.env(): Env {
        val harness =
            SessionHarness(sessionScope).also {
                it.gateway.setSignedIn("uid-1")
                it.accounts.setLoaded(profileExists = true, hasMembership = true)
                it.manager.start()
                advanceUntilIdle()
            }
        return Env(harness, CurrentProfile(repo) { harness.gateway.currentUid })
    }

    private fun Env.vm(region: String? = "FR") =
        MyInfoViewModel(
            session = harness.manager,
            repository = repo,
            current = current,
            avatars = codec,
            network = network,
            deviceRegion = { region },
            worker = dispatcher,
        )

    private fun TestScope.openedVm(): Pair<Env, MyInfoViewModel> {
        val e = env()
        val vm = e.vm()
        advanceUntilIdle()
        return e to vm
    }

    // --- Reading --------------------------------------------------------------------------------------------------------

    @Test
    fun theFormShowsWhatTheServerHolds_theNumberSplitIntoCountryAndNationalPart_andNothingToSave() =
        runTest(dispatcher) {
            val (_, vm) = openedVm()
            val s = vm.uiState.value
            assertEquals(MyInfoLoad.READY, s.load)
            assertEquals("Léa", s.firstName)
            assertEquals("Martin", s.lastName)
            assertEquals("FR", s.regionCode)
            assertEquals("612345678", s.phoneText)
            assertFalse("grey until something changes", s.hasChanges)
            assertFalse(s.canSave)
            assertFalse(s.hasEdits)
        }

    @Test
    fun aNumberOfAnotherCountryComesBackWithItsCountry() =
        runTest(dispatcher) {
            repo.stored = MyProfile("Léa", "Martin", "+447400123456", null)
            val (_, vm) = openedVm()
            assertEquals("GB", vm.uiState.value.regionCode)
            assertEquals("7400123456", vm.uiState.value.phoneText)
        }

    @Test
    fun theProfileAlreadyKnownByTheTabIsNotReadAgain() =
        runTest(dispatcher) {
            val e = env()
            e.current.onSaved("uid-1", MyProfile("Zoé", "Roy", "+33612345678", null))
            val vm = e.vm()
            assertEquals("Zoé", vm.uiState.value.firstName)
            assertEquals(0, repo.reads)
        }

    @Test
    fun aProfileThatCannotBeReadOffersToTryAgain() =
        runTest(dispatcher) {
            repo.readResult = MyProfileLoad.Unavailable
            val (_, vm) = openedVm()
            assertEquals(MyInfoLoad.FAILED, vm.uiState.value.load)
            repo.readResult = null
            vm.retry()
            advanceUntilIdle()
            assertEquals(MyInfoLoad.READY, vm.uiState.value.load)
        }

    // --- The button follows what changed (criterion A5) ----------------------------------------------------------------------

    @Test
    fun changingAName_enablesSave_andPuttingItBackDisablesItAgain() =
        runTest(dispatcher) {
            val (_, vm) = openedVm()
            vm.onFirstNameChanged("Léane")
            assertTrue(vm.uiState.value.canSave)
            assertTrue(vm.uiState.value.hasEdits)
            vm.onFirstNameChanged("Léa")
            assertFalse(vm.uiState.value.canSave)
            vm.onFirstNameChanged("  Léa  ")
            assertFalse("spaces around do not count as a change", vm.uiState.value.canSave)
        }

    @Test
    fun aNameMustKeepOneCharacter_noMinimumOfThree() =
        runTest(dispatcher) {
            val (_, vm) = openedVm()
            vm.onFirstNameChanged("A")
            assertTrue("one character is enough (decision Q6)", vm.uiState.value.canSave)
            vm.onFirstNameChanged("   ")
            val s = vm.uiState.value
            assertFalse(s.canSave)
            assertTrue("the person is told, kindly, once they emptied the field", s.firstNameBlank)
            assertFalse(s.lastNameBlank)
        }

    @Test
    fun aNameIsCutAtFiftyCharacters_andTheLimitIsSaid() =
        runTest(dispatcher) {
            val (_, vm) = openedVm()
            vm.onLastNameChanged("x".repeat(60))
            val s = vm.uiState.value
            assertEquals(50, s.lastName.length)
            assertTrue(s.lastNameLimitHit)
            vm.onLastNameChanged("x".repeat(40))
            assertFalse(vm.uiState.value.lastNameLimitHit)
        }

    @Test
    fun theNumberCannotBeEmptied() =
        runTest(dispatcher) {
            val (_, vm) = openedVm()
            vm.onPhoneChanged("")
            assertFalse(vm.uiState.value.canSave)
            vm.onPhoneChanged("ab")
            assertFalse("no digit, no save", vm.uiState.value.canSave)
        }

    @Test
    fun aNumberThatDoesNotLookRight_isPointedOutWhenTheFieldIsLeft_andGreysTheButton() =
        runTest(dispatcher) {
            val (_, vm) = openedVm()
            vm.onPhoneChanged("123")
            assertTrue("nothing is said while the person is still typing", vm.uiState.value.canSave)
            assertFalse(vm.uiState.value.phoneInvalid)
            vm.onPhoneFocusLost()
            assertTrue(vm.uiState.value.phoneInvalid)
            assertFalse(vm.uiState.value.canSave)
            vm.onPhoneChanged("0699999999")
            assertFalse("typing again clears the message", vm.uiState.value.phoneInvalid)
            assertTrue(vm.uiState.value.canSave)
        }

    @Test
    fun leavingTheFieldUntouchedSaysNothing() =
        runTest(dispatcher) {
            val (_, vm) = openedVm()
            vm.onPhoneFocusLost()
            assertFalse(vm.uiState.value.phoneInvalid)
        }

    @Test
    fun aNumberOfAKindThatIsNotAcceptedHasItsOwnSentence() =
        runTest(dispatcher) {
            val (_, vm) = openedVm()
            vm.onCountrySelected("US")
            vm.onPhoneChanged("800 555 0123") // toll free
            vm.onPhoneFocusLost()
            assertTrue(vm.uiState.value.phoneInvalid)
            assertTrue(vm.uiState.value.phoneTypeRefused)
        }

    @Test
    fun anInternationalNumberPastedSelectsItsCountry() =
        runTest(dispatcher) {
            val (_, vm) = openedVm()
            vm.onPhoneChanged("+44 7400 123456")
            assertEquals("GB", vm.uiState.value.regionCode)
            assertEquals("7400123456", vm.uiState.value.phoneText)
        }

    @Test
    fun anUnknownCountryIsIgnored() =
        runTest(dispatcher) {
            val (_, vm) = openedVm()
            vm.onCountrySelected("ZZ")
            assertEquals("FR", vm.uiState.value.regionCode)
            vm.onCountrySelected("be")
            assertEquals("BE", vm.uiState.value.regionCode)
            assertTrue(vm.uiState.value.hasChanges)
        }

    // --- Saving (criteria A7, X2) ---------------------------------------------------------------------------------------------

    @Test
    fun savingSendsTheNormalizedNamesAndTheE164Number_andTheHeaderIsUpdated() =
        runTest(dispatcher) {
            val (e, vm) = openedVm()
            vm.onFirstNameChanged("  Léane   Marie ")
            vm.onCountrySelected("BE")
            vm.onPhoneChanged("0470 12 34 56")
            vm.onSave()
            assertTrue(vm.uiState.value.isSaving)
            advanceUntilIdle()
            val sent = repo.saved.single()
            assertEquals("Léane Marie", sent.firstName)
            assertEquals("Martin", sent.lastName)
            assertEquals("+32470123456", sent.phone)
            val s = vm.uiState.value
            assertFalse(s.isSaving)
            assertEquals(MyInfoMessage.SAVED, s.message)
            assertFalse("nothing left to save", s.canSave)
            assertFalse(s.hasEdits)
            assertEquals("Léane Marie", s.firstName)
            assertEquals("BE", s.regionCode)
            assertEquals(
                "Léane Marie Martin",
                e.current.profile.value
                    ?.displayName,
            )
        }

    @Test
    fun aDoubleTapSavesOnce() =
        runTest(dispatcher) {
            val (_, vm) = openedVm()
            vm.onFirstNameChanged("Léane")
            vm.onSave()
            vm.onSave()
            advanceUntilIdle()
            assertEquals(1, repo.saved.size)
        }

    @Test
    fun withoutNetwork_aCalmMessage_nothingIsSent_nothingIsQueued_theFieldsStay() =
        runTest(dispatcher) {
            val (_, vm) = openedVm()
            vm.onFirstNameChanged("Léane")
            network.online = false
            vm.onSave()
            advanceUntilIdle()
            assertTrue(repo.saved.isEmpty())
            val s = vm.uiState.value
            assertEquals(MyInfoMessage.NETWORK, s.message)
            assertEquals("Léane", s.firstName)
            assertTrue(s.canSave)
        }

    @Test
    fun aNumberThatIsInvalidIsNeverSent() =
        runTest(dispatcher) {
            val (_, vm) = openedVm()
            vm.onPhoneChanged("123")
            vm.onSave()
            advanceUntilIdle()
            assertTrue(repo.saved.isEmpty())
            assertTrue(vm.uiState.value.phoneInvalid)
        }

    @Test
    fun aRefusalKeepsEverything_andAfterThreeInARowAdvisesToComeBackLater() =
        runTest(dispatcher) {
            val (_, vm) = openedVm()
            repo.results = ArrayDeque(listOf(ProfileSaveResult.REFUSED))
            vm.onFirstNameChanged("Léane")
            repeat(2) {
                vm.onSave()
                advanceUntilIdle()
                assertEquals(MyInfoMessage.SAVE_FAILED, vm.uiState.value.message)
                assertEquals("Léane", vm.uiState.value.firstName)
            }
            vm.onSave()
            advanceUntilIdle()
            assertEquals(MyInfoMessage.TRY_LATER, vm.uiState.value.message)
            assertTrue(vm.uiState.value.canSave)
        }

    @Test
    fun aServerThatAnswersOffline_isSaidAsNoConnection() =
        runTest(dispatcher) {
            val (_, vm) = openedVm()
            repo.results = ArrayDeque(listOf(ProfileSaveResult.OFFLINE))
            vm.onFirstNameChanged("Léane")
            vm.onSave()
            advanceUntilIdle()
            assertEquals(MyInfoMessage.NETWORK, vm.uiState.value.message)
            assertEquals("nothing was written", "Léa", repo.stored?.firstName)
        }

    @Test
    fun whenOnlyTheCopiesOfTheMiffsAreStillToWrite_theSameButtonFinishesTheJob() =
        runTest(dispatcher) {
            val (_, vm) = openedVm()
            repo.results = ArrayDeque(listOf(ProfileSaveResult.SAVED_PARTIAL, ProfileSaveResult.SAVED))
            vm.onFirstNameChanged("Léane")
            vm.onSave()
            advanceUntilIdle()
            var s = vm.uiState.value
            assertEquals(MyInfoMessage.SYNC_PENDING, s.message)
            assertFalse("what was typed is saved: leaving asks nothing", s.hasEdits)
            assertTrue("but the button stays usable to finish", s.canSave)
            vm.onSave()
            advanceUntilIdle()
            s = vm.uiState.value
            assertEquals(MyInfoMessage.SAVED, s.message)
            assertFalse(s.canSave)
            assertEquals(2, repo.saved.size)
        }

    @Test
    fun editingClearsTheConfirmation() =
        runTest(dispatcher) {
            val (_, vm) = openedVm()
            vm.onFirstNameChanged("Léane")
            vm.onSave()
            advanceUntilIdle()
            assertEquals(MyInfoMessage.SAVED, vm.uiState.value.message)
            vm.onLastNameChanged("Martin-Roy")
            assertNull(vm.uiState.value.message)
        }

    // --- Photo (criterion A8) -------------------------------------------------------------------------------------------------

    @Test
    fun aChosenPhotoIsCroppedThenKept_andSavedWithTheProfile() =
        runTest(dispatcher) {
            val (_, vm) = openedVm()
            vm.onPhotoChosen("content://photo/1")
            advanceUntilIdle()
            assertNotNull(vm.uiState.value.crop)
            vm.onCropChanged(2f, 100f, 100f)
            vm.onCropConfirmed()
            advanceUntilIdle()
            val s = vm.uiState.value
            assertNull(s.crop)
            assertNotNull(s.avatar)
            assertTrue(s.canSave)
            vm.onSave()
            advanceUntilIdle()
            assertEquals(
                9_000,
                repo.saved
                    .single()
                    .avatarJpeg
                    ?.size,
            )
        }

    @Test
    fun closingThePickerChangesNothing_andCancellingTheCropKeepsTheOldPhoto() =
        runTest(dispatcher) {
            repo.stored = MyProfile("Léa", "Martin", "+33612345678", ByteArray(100) { 7 })
            val (_, vm) = openedVm()
            vm.onPhotoChosen(null)
            assertNull(vm.uiState.value.crop)
            vm.onPhotoChosen("content://photo/1")
            advanceUntilIdle()
            vm.onCropCancelled()
            assertNull(vm.uiState.value.crop)
            assertFalse(vm.uiState.value.hasChanges)
            assertEquals(
                100,
                vm.uiState.value.avatar
                    ?.jpeg
                    ?.size,
            )
        }

    @Test
    fun aPhotoCanBeRemoved_andThatIsAChange() =
        runTest(dispatcher) {
            repo.stored = MyProfile("Léa", "Martin", "+33612345678", ByteArray(100) { 7 })
            val (_, vm) = openedVm()
            vm.onRemovePhoto()
            assertNull(vm.uiState.value.avatar)
            assertTrue(vm.uiState.value.canSave)
            vm.onSave()
            advanceUntilIdle()
            assertNull(repo.saved.single().avatarJpeg)
        }

    @Test
    fun anImageThatCannotBeUsedIsSaidKindly_andNothingChanges() =
        runTest(dispatcher) {
            val (_, vm) = openedVm()
            codec.decoded = null
            vm.onPhotoChosen("content://photo/bad")
            advanceUntilIdle()
            assertEquals(MyInfoMessage.PHOTO_UNUSABLE, vm.uiState.value.message)
            assertFalse(vm.uiState.value.hasChanges)

            codec.decoded =
                com.tomessa.miff.testsupport
                    .FakePhoto()
            codec.encoded = ByteArray(20_001)
            vm.onPhotoChosen("content://photo/big")
            advanceUntilIdle()
            vm.onCropConfirmed()
            advanceUntilIdle()
            assertEquals(MyInfoMessage.PHOTO_UNUSABLE, vm.uiState.value.message)
            assertNull(vm.uiState.value.avatar)
        }

    @Test
    fun noCameraApplication_theGalleryIsStillThere() =
        runTest(dispatcher) {
            val (_, vm) = openedVm()
            vm.onCameraUnavailable()
            assertEquals(MyInfoMessage.CAMERA_UNAVAILABLE, vm.uiState.value.message)
            vm.onMessageDismissed()
            assertNull(vm.uiState.value.message)
        }
}
