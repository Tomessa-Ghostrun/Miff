package com.tomessa.miff.ui.profile

import com.tomessa.miff.data.auth.AccountLoad
import com.tomessa.miff.data.auth.ProfileSeed
import com.tomessa.miff.data.auth.ProfileWriteResult
import com.tomessa.miff.data.onboarding.DraftStep
import com.tomessa.miff.data.onboarding.ProfileDraft
import com.tomessa.miff.data.onboarding.asTeardown
import com.tomessa.miff.data.session.SessionState
import com.tomessa.miff.domain.auth.AppDestination
import com.tomessa.miff.domain.auth.ConsentVersions
import com.tomessa.miff.domain.auth.PendingConsent
import com.tomessa.miff.testsupport.FakeAvatarCodec
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.FakePhoto
import com.tomessa.miff.testsupport.FakeUserProfileRepository
import com.tomessa.miff.testsupport.MemoryProfileDraftStore
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The two profile steps and the phone step alone (Onboarding ticket 6.2 to 6.4; criteria PR2, PR3, PR7, PR8, TEL1 to TEL5,
 * TEL8, TEL9, X2): NO write at the profile step, ONE write with every key at the end of the phone step, confirmed by the
 * server before the next screen, draft kept until then and erased after.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val sessionScope = CoroutineScope(dispatcher + SupervisorJob())

    private val drafts = MemoryProfileDraftStore()
    private val repo = FakeUserProfileRepository()
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

    /** A signed-in person with no profile whose terms were accepted at the sign-up (destination PROFILE). */
    private fun TestScope.newAccount(
        analytics: Boolean = true,
        displayName: String? = null,
    ): SessionHarness =
        SessionHarness(sessionScope).also {
            it.gateway.setSignedIn("uid-1")
            it.gateway.displayName = displayName
            it.pending.save(PendingConsent("uid-1", ConsentVersions.TERMS, analytics))
            it.accounts.setLoaded(profileExists = false)
            it.manager.start()
            advanceUntilIdle()
        }

    private fun SessionHarness.destination() = (manager.state.value as? SessionState.Ready)?.destination

    private fun SessionHarness.vm(
        mode: OnboardingMode = OnboardingMode.FULL,
        region: String? = "FR",
    ) = ProfileViewModel(
        mode = mode,
        session = manager,
        pending = pending,
        drafts = drafts,
        profiles = repo,
        avatars = codec,
        network = network,
        deviceRegion = { region },
        worker = dispatcher,
    )

    private fun ProfileViewModel.fillProfile(
        first: String = "Léa",
        last: String = "Martin",
    ) {
        onFirstNameChanged(first)
        onLastNameChanged(last)
    }

    // --- Profile step: nothing is written, the arrow follows the names (PR2, PR7) -------------------------------------

    @Test
    fun theProfileStepStartsEmpty_withTheArrowGrey_andTheCountryOfTheDevice() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm(region = "BE")
            val s = vm.uiState.value
            assertEquals(DraftStep.PROFILE, s.step)
            assertEquals("", s.firstName)
            assertNull(s.avatar)
            assertFalse(s.canContinueFromProfile)
            assertEquals("BE", s.regionCode)
        }

    @Test
    fun withoutARegion_theCountryIsFrance() =
        runTest(dispatcher) {
            val h = newAccount()
            assertEquals(
                "FR",
                h
                    .vm(region = null)
                    .uiState.value.regionCode,
            )
            assertEquals(
                "FR",
                h
                    .vm(region = "XX")
                    .uiState.value.regionCode,
            )
        }

    @Test
    fun theArrowIsActiveOnceBothNamesHaveACharacterAfterTrimming() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.onFirstNameChanged("  ")
            vm.onLastNameChanged("Martin")
            assertFalse(vm.uiState.value.canContinueFromProfile)
            vm.onFirstNameChanged(" L ")
            assertTrue(vm.uiState.value.canContinueFromProfile)
            vm.onLastNameChanged("   ")
            assertFalse(vm.uiState.value.canContinueFromProfile)
        }

    @Test
    fun aNameLongerThan50IsCut_withTheMessage_andTheMessageGoesAwayAtTheNextKeystroke() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.onFirstNameChanged("a".repeat(70))
            assertEquals(50, vm.uiState.value.firstName.length)
            assertTrue(vm.uiState.value.firstNameLimitHit)
            vm.onFirstNameChanged("a".repeat(49))
            assertFalse(vm.uiState.value.firstNameLimitHit)
            assertFalse("the other field is not concerned", vm.uiState.value.lastNameLimitHit)
        }

    @Test
    fun nothingIsWrittenAtTheProfileStep_PR7() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.fillProfile()
            vm.onContinueFromProfile()
            advanceUntilIdle()
            assertEquals(DraftStep.PHONE, vm.uiState.value.step)
            assertEquals("no write at the profile step", 0, repo.writes)
            assertEquals(AppDestination.PROFILE, h.destination())
        }

    @Test
    fun theArrowDoesNothingWhileTheNamesAreIncomplete() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.onFirstNameChanged("Léa")
            vm.onContinueFromProfile()
            assertEquals(DraftStep.PROFILE, vm.uiState.value.step)
        }

    @Test
    fun noAnalyticsEventIsSentByTheSteps() =
        runTest(dispatcher) {
            val h = newAccount()
            val before = h.sink.events.size
            val vm = h.vm()
            vm.fillProfile()
            vm.onContinueFromProfile()
            vm.onPhoneChanged("06 12 34 56 78")
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertEquals(before, h.sink.events.size)
        }

    // --- Pre-fill from the provider (PR8) -----------------------------------------------------------------------------

    @Test
    fun theNameFromGoogleIsASuggestion_cutTo50_andEditable() =
        runTest(dispatcher) {
            val h = newAccount(displayName = "Marie " + "d".repeat(80))
            val vm = h.vm()
            assertEquals("Marie", vm.uiState.value.firstName)
            assertEquals(50, vm.uiState.value.lastName.length)
            assertNull("no photo is ever imported", vm.uiState.value.avatar)
            vm.onFirstNameChanged("Marion")
            assertEquals("Marion", vm.uiState.value.firstName)
        }

    @Test
    fun whatThePersonTypedBeatsTheSuggestion() =
        runTest(dispatcher) {
            val h = newAccount(displayName = "Marie Dupont")
            drafts.save(ProfileDraft("uid-1", DraftStep.PROFILE, "Zoé", "Roy", null, "", null))
            val vm = h.vm()
            assertEquals("Zoé", vm.uiState.value.firstName)
            assertEquals("Roy", vm.uiState.value.lastName)
        }

    // --- Draft (TEL9, RT2) --------------------------------------------------------------------------------------------

    @Test
    fun everyChangeIsKeptInTheDraft_andReloadedAfterTheAppWasClosed() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.fillProfile("Léa", "Martin")
            vm.onContinueFromProfile()
            vm.onCountrySelected("BE")
            vm.onPhoneChanged("0470 12 34 56")
            val saved = drafts.stored!!
            assertEquals("uid-1", saved.uid)
            assertEquals(DraftStep.PHONE, saved.step)
            assertEquals("Léa", saved.firstName)
            assertEquals("BE", saved.regionCode)
            assertEquals("0470 12 34 56", saved.phoneText)

            // The app is closed and opened again: a new view model reads the draft.
            val again = h.vm(region = "FR").uiState.value
            assertEquals(DraftStep.PHONE, again.step)
            assertEquals("Léa", again.firstName)
            assertEquals("Martin", again.lastName)
            assertEquals("BE", again.regionCode)
            assertEquals("0470 12 34 56", again.phoneText)
        }

    @Test
    fun theDraftNeverHoldsAPasswordOrAnAddress() {
        val fields = ProfileDraft::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(fields.none { "password" in it || "secret" in it || "mail" in it || "token" in it })
    }

    @Test
    fun aDraftOfAnotherAccountIsErasedAndNeverUsed() =
        runTest(dispatcher) {
            val h = newAccount()
            drafts.save(ProfileDraft("uid-OTHER", DraftStep.PHONE, "Autre", "Personne", "BE", "0470", ByteArray(5)))
            val s = h.vm().uiState.value
            assertEquals(DraftStep.PROFILE, s.step)
            assertEquals("", s.firstName)
            assertNull(s.avatar)
            assertNull("erased", drafts.stored)
        }

    @Test
    fun theArrowBackOfThePhoneStepKeepsEverything() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.fillProfile()
            vm.onContinueFromProfile()
            vm.onPhoneChanged("06 12 34 56 78")
            vm.onBackToProfile()
            assertEquals(DraftStep.PROFILE, vm.uiState.value.step)
            assertEquals("Léa", vm.uiState.value.firstName)
            assertEquals("06 12 34 56 78", vm.uiState.value.phoneText)
            assertEquals(DraftStep.PROFILE, drafts.stored!!.step)
        }

    // --- Phone step (TEL1 to TEL5) -------------------------------------------------------------------------------------

    @Test
    fun theArrowOfThePhoneStepIsGreyUntilOneDigit_TEL1() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.fillProfile()
            vm.onContinueFromProfile()
            assertFalse(vm.uiState.value.canSubmitPhone)
            vm.onPhoneChanged("abc")
            assertFalse(vm.uiState.value.canSubmitPhone)
            vm.onPhoneChanged("0")
            assertTrue(vm.uiState.value.canSubmitPhone)
        }

    @Test
    fun anInvalidNumberShowsTheCalmMessage_andWritesNothing_TEL3() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.fillProfile()
            vm.onContinueFromProfile()
            vm.onPhoneChanged("06 12 34")
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.phoneInvalid)
            assertNull("no error banner for a number that does not look right", vm.uiState.value.message)
            assertEquals(0, repo.writes)
            vm.onPhoneChanged("06 12 34 5")
            assertFalse("the message goes away when the person types again", vm.uiState.value.phoneInvalid)
        }

    @Test
    fun aPlusNumberSelectsItsCountry_TEL2() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm(region = "FR")
            vm.fillProfile()
            vm.onContinueFromProfile()
            vm.onPhoneChanged("+44 7400 123456")
            assertEquals("GB", vm.uiState.value.regionCode)
            assertEquals("7400123456", vm.uiState.value.phoneText)
            assertEquals("GB", drafts.stored!!.regionCode)
        }

    @Test
    fun chooseACountry_andAnUnknownOneIsIgnored() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.onCountrySelected("CG")
            assertEquals("CG", vm.uiState.value.regionCode)
            vm.onCountrySelected("ZZ")
            vm.onCountrySelected("")
            assertEquals("CG", vm.uiState.value.regionCode)
        }

    @Test
    fun aVeryLongPasteIsCut() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.onPhoneChanged("1".repeat(500))
            assertEquals(ProfileViewModel.MAX_PHONE_INPUT, vm.uiState.value.phoneText.length)
        }

    // --- The ONE write (PR7, TEL4, TEL5) --------------------------------------------------------------------------------

    private fun ProfileViewModel.reachPhoneStep() {
        fillProfile("  Léa ", "Martin  Dupont ")
        onContinueFromProfile()
        onPhoneChanged("(06) 12-34-56-78")
    }

    @Test
    fun theWriteIsOne_withEveryValue_inE164_andTheConsentOfTheSignUp() =
        runTest(dispatcher) {
            val h = newAccount(analytics = true)
            val vm = h.vm()
            vm.reachPhoneStep()
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertEquals(1, repo.writes)
            val written = repo.created.single()
            assertEquals("uid-1", written.uid)
            assertEquals("Léa", written.firstName)
            assertEquals("Martin Dupont", written.lastName)
            assertEquals("+33612345678", written.phone)
            assertNull(written.avatarJpeg)
            assertEquals(ConsentVersions.TERMS, written.consent.termsVersion)
            assertTrue(written.consent.analyticsAccepted)
        }

    @Test
    fun theAvatarGoesInTheSameWrite() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.onPhotoChosen("content://picked/1")
            advanceUntilIdle()
            vm.onCropConfirmed()
            advanceUntilIdle()
            val avatar =
                vm.uiState.value.avatar!!
                    .jpeg
            vm.reachPhoneStep()
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertArrayEquals(avatar, repo.created.single().avatarJpeg)
        }

    @Test
    fun theNextScreenOnlyOpensAfterTheServerConfirmed_PR7() =
        runTest(dispatcher) {
            val h = newAccount()
            var destinationWhenTheWriteIsInProgress: AppDestination? = null
            repo.onWrite = { destinationWhenTheWriteIsInProgress = h.destination() }
            val vm = h.vm()
            vm.reachPhoneStep()
            vm.onSubmitPhone()
            assertTrue("a write is in progress", vm.uiState.value.isWriting)
            advanceUntilIdle()
            assertEquals(
                "still on the profile steps during the write",
                AppDestination.PROFILE,
                destinationWhenTheWriteIsInProgress,
            )
            assertEquals(AppDestination.MIFF_CHOICE, h.destination())
        }

    @Test
    fun afterTheConfirmation_theDraftAndThePendingConsentAreErased_TEL9() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.reachPhoneStep()
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertNull(drafts.stored)
            assertNull(h.pending.stored)
            // And nothing typed stays in memory.
            assertEquals("", vm.uiState.value.firstName)
            assertEquals("", vm.uiState.value.phoneText)
            assertNull(vm.uiState.value.avatar)
        }

    @Test
    fun noDraftIsWrittenAfterTheConfirmation() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.reachPhoneStep()
            vm.onSubmitPhone()
            advanceUntilIdle()
            vm.onFirstNameChanged("Late")
            assertNull(drafts.stored)
        }

    @Test
    fun aFirstTryThatLandedLate_isNotAnErrorOnTheSecondTry() =
        runTest(dispatcher) {
            val h = newAccount()
            // First try: no answer in time. The server keeps it anyway. Second try: refused (the profile exists already).
            repo.results = ArrayDeque(listOf(ProfileWriteResult.OFFLINE, ProfileWriteResult.REFUSED))
            val vm = h.vm()
            vm.reachPhoneStep()
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertEquals(ProfileMessage.NETWORK, vm.uiState.value.message)
            h.accounts.setLoaded(profileExists = true, hasPhone = true) // what the server holds now
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertEquals(
                "the person is not stopped on a profile that is saved",
                AppDestination.MIFF_CHOICE,
                h.destination(),
            )
            assertNull(drafts.stored)
        }

    @Test
    fun aRealRefusal_isChecked_andStaysARefusal_whenNothingIsSaved() =
        runTest(dispatcher) {
            val h = newAccount()
            repo.results = ArrayDeque(listOf(ProfileWriteResult.REFUSED))
            val vm = h.vm()
            vm.reachPhoneStep()
            val reads = h.accounts.loadCalls
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertEquals("one read to check", reads + 1, h.accounts.loadCalls)
            assertEquals(ProfileMessage.SAVE_FAILED, vm.uiState.value.message)
            assertEquals(AppDestination.PROFILE, h.destination())
        }

    @Test
    fun noReadIsMadeWhenTheNetworkIsTheProblem() =
        runTest(dispatcher) {
            val h = newAccount()
            repo.results = ArrayDeque(listOf(ProfileWriteResult.OFFLINE))
            val vm = h.vm()
            vm.reachPhoneStep()
            val reads = h.accounts.loadCalls
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertEquals(reads, h.accounts.loadCalls)
        }

    @Test
    fun aDoubleTapWritesOnlyOnce() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.reachPhoneStep()
            vm.onSubmitPhone()
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertEquals(1, repo.writes)
        }

    @Test
    fun offline_aCalmMessage_nothingIsWritten_theFieldsAndTheDraftStay_X2() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.reachPhoneStep()
            network.online = false
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertEquals(ProfileMessage.NETWORK, vm.uiState.value.message)
            assertFalse(vm.uiState.value.isWriting)
            assertEquals(0, repo.writes)
            assertEquals("  Léa ", vm.uiState.value.firstName) // kept as typed
            assertEquals("(06) 12-34-56-78", vm.uiState.value.phoneText)
            assertNotNull(drafts.stored)
            assertEquals(AppDestination.PROFILE, h.destination())

            // Network back: the same screen goes through.
            network.online = true
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertEquals(1, repo.created.size)
            assertEquals(AppDestination.MIFF_CHOICE, h.destination())
        }

    @Test
    fun aServerThatCouldNotBeReached_isTheSameAsOffline_andNothingIsErased() =
        runTest(dispatcher) {
            val h = newAccount()
            repo.results = ArrayDeque(listOf(ProfileWriteResult.OFFLINE, ProfileWriteResult.DONE))
            val vm = h.vm()
            vm.reachPhoneStep()
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertEquals(ProfileMessage.NETWORK, vm.uiState.value.message)
            assertFalse(vm.uiState.value.isWriting)
            assertNotNull(drafts.stored)
            assertNotNull("the acceptance of the terms is kept for the retry", h.pending.stored)
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertEquals(2, repo.created.size)
            assertEquals(AppDestination.MIFF_CHOICE, h.destination())
        }

    @Test
    fun aRefusal_isNotPresentedAsAMissingNetwork() =
        runTest(dispatcher) {
            val h = newAccount()
            repo.results = ArrayDeque(listOf(ProfileWriteResult.REFUSED))
            val vm = h.vm()
            vm.reachPhoneStep()
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertEquals(ProfileMessage.SAVE_FAILED, vm.uiState.value.message)
            assertFalse(vm.uiState.value.isWriting)
            assertEquals(AppDestination.PROFILE, h.destination())
        }

    @Test
    fun withoutTheAcceptanceOfTheTermsOnTheDevice_nothingIsWritten_andTheAccountIsRoutedAgain() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.reachPhoneStep()
            h.pending.clear()
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertEquals(0, repo.writes)
            assertEquals(AppDestination.CONSENT_PANEL, h.destination())
        }

    @Test
    fun theWriteIsFollowedToItsEnd_evenIfTheScreenIsGone() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.reachPhoneStep()
            vm.onSubmitPhone()
            // The view model is abandoned right away (rotation, app in the background): the write still ends cleanly.
            advanceUntilIdle()
            assertEquals(AppDestination.MIFF_CHOICE, h.destination())
            assertNull(drafts.stored)
        }

    // --- Photo (PR3, PR4) -----------------------------------------------------------------------------------------------

    @Test
    fun theSourceIsHandedToTheCodec_andTheCropOpensCentred() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.onPhotoChosen("content://media/picker/1")
            advanceUntilIdle()
            assertEquals(listOf("content://media/picker/1"), codec.decodedSources)
            val crop = vm.uiState.value.crop!!
            assertEquals(1f, crop.zoom, 0f)
            assertEquals(1000f, crop.centerX, 0f)
            assertEquals(500f, crop.centerY, 0f)
            assertNull("nothing is kept before the person validates", vm.uiState.value.avatar)
        }

    @Test
    fun closingThePickerDoesNothing() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.onPhotoChosen(null)
            advanceUntilIdle()
            assertNull(vm.uiState.value.crop)
            assertNull(vm.uiState.value.message)
            assertTrue(codec.decodedSources.isEmpty())
        }

    @Test
    fun theCropKeepsItsStateAndStaysInsideThePhoto() =
        runTest(dispatcher) {
            val h = newAccount()
            codec.decoded = FakePhoto(2000, 1000)
            val vm = h.vm()
            vm.onPhotoChosen("content://x")
            advanceUntilIdle()
            vm.onCropChanged(2f, 99999f, -99999f)
            val crop = vm.uiState.value.crop!!
            assertEquals(2f, crop.zoom, 0f)
            assertEquals(1750f, crop.centerX, 0f)
            assertEquals(250f, crop.centerY, 0f)
            vm.onCropChanged(50f, 0f, 0f)
            assertEquals(
                4f,
                vm.uiState.value.crop!!
                    .zoom,
                0f,
            )
        }

    @Test
    fun validatingTheCrop_keepsTheSquare_andTheAvatar_andTheDraft() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.onPhotoChosen("content://x")
            advanceUntilIdle()
            vm.onCropChanged(2f, 1000f, 500f)
            vm.onCropConfirmed()
            advanceUntilIdle()
            assertEquals(1, codec.crops.size)
            assertEquals(500, codec.crops.single().size)
            assertNull(vm.uiState.value.crop)
            assertNotNull(vm.uiState.value.avatar)
            assertTrue(drafts.stored!!.avatar!!.size in 1..20_000)
        }

    @Test
    fun cancellingTheCrop_keepsNothing() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.onPhotoChosen("content://x")
            advanceUntilIdle()
            vm.onCropCancelled()
            assertNull(vm.uiState.value.crop)
            assertNull(vm.uiState.value.avatar)
            assertEquals(0, codec.crops.size)
        }

    @Test
    fun aFileThatIsNotAnImage_givesACalmMessage_andNoCrop() =
        runTest(dispatcher) {
            val h = newAccount()
            codec.decoded = null
            val vm = h.vm()
            vm.onPhotoChosen("content://x")
            advanceUntilIdle()
            assertEquals(ProfileMessage.PHOTO_UNUSABLE, vm.uiState.value.message)
            assertNull(vm.uiState.value.crop)
            vm.onMessageDismissed()
            assertNull(vm.uiState.value.message)
        }

    @Test
    fun aPhotoThatCannotFitIn20000Bytes_isNeverKept() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            for (encoded in listOf<ByteArray?>(null, ByteArray(20_001))) {
                codec.encoded = encoded
                vm.onPhotoChosen("content://x")
                advanceUntilIdle()
                vm.onCropConfirmed()
                advanceUntilIdle()
                assertNull(vm.uiState.value.avatar)
                assertEquals(ProfileMessage.PHOTO_UNUSABLE, vm.uiState.value.message)
            }
        }

    @Test
    fun theAvatarCanBeRemoved_andTheProfileStillGoesThrough_PR3() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.onPhotoChosen("content://x")
            advanceUntilIdle()
            vm.onCropConfirmed()
            advanceUntilIdle()
            assertNotNull(vm.uiState.value.avatar)
            vm.onRemovePhoto()
            assertNull(vm.uiState.value.avatar)
            assertNull(drafts.stored!!.avatar)
            vm.reachPhoneStep()
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertNull(repo.created.single().avatarJpeg)
        }

    @Test
    fun theAvatarSurvivesTheClosingOfTheApp() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.onPhotoChosen("content://x")
            advanceUntilIdle()
            vm.onCropConfirmed()
            advanceUntilIdle()
            val kept =
                vm.uiState.value.avatar!!
                    .jpeg
            assertArrayEquals(
                kept,
                h
                    .vm()
                    .uiState.value.avatar!!
                    .jpeg,
            )
        }

    @Test
    fun noCameraApplication_isACalmMessage_andTheGalleryStaysAvailable() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.onCameraUnavailable()
            assertEquals(ProfileMessage.CAMERA_UNAVAILABLE, vm.uiState.value.message)
        }

    // --- Phone step alone: account without a number (TEL8, ordre 2b) -------------------------------------------------

    private fun TestScope.accountWithoutPhone(
        hasTermsVersion: Boolean = true,
        hasMembership: Boolean = false,
    ): SessionHarness =
        SessionHarness(sessionScope).also {
            it.gateway.setSignedIn("uid-1")
            it.accounts.setLoaded(
                profileExists = true,
                hasMembership = hasMembership,
                analyticsConsent = true,
                hasPhone = false,
                hasTermsVersion = hasTermsVersion,
            )
            it.manager.start()
            advanceUntilIdle()
        }

    @Test
    fun anAccountWithoutANumber_opensThePhoneStepAlone_TEL8() =
        runTest(dispatcher) {
            val h = accountWithoutPhone()
            assertEquals(AppDestination.PHONE_ONLY, h.destination())
            val vm = h.vm(OnboardingMode.PHONE_ONLY)
            assertEquals(DraftStep.PHONE, vm.uiState.value.step)
            assertEquals(OnboardingMode.PHONE_ONLY, vm.uiState.value.mode)
        }

    @Test
    fun thePhoneAloneUpdatesTheProfile_withTheNumberOnly_thenGoesOn() =
        runTest(dispatcher) {
            val h = accountWithoutPhone()
            h.accounts.setLoaded(profileExists = true, hasPhone = true) // what the server will say afterwards
            val vm = h.vm(OnboardingMode.PHONE_ONLY)
            vm.onPhoneChanged("06 12 34 56 78")
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertEquals(listOf("uid-1" to "+33612345678"), repo.phonesAdded)
            assertEquals("no creation, no deletion", 0, repo.created.size + repo.deleted.size)
            assertEquals(AppDestination.MIFF_CHOICE, h.destination())
            assertNull(drafts.stored)
        }

    @Test
    fun anOlderAccountWithAMiff_goesStraightToTheFeed_afterItsNumber() =
        runTest(dispatcher) {
            val h = accountWithoutPhone(hasMembership = true)
            h.accounts.setLoaded(profileExists = true, hasMembership = true, hasPhone = true)
            val vm = h.vm(OnboardingMode.PHONE_ONLY)
            vm.onPhoneChanged("0612345678")
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertEquals(AppDestination.FEED, h.destination())
        }

    @Test
    fun ifTheReadAfterTheWriteFails_theAppStillMovesOn_withoutARetryScreen() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.reachPhoneStep()
            h.accounts.result = AccountLoad.Unavailable
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertEquals(AppDestination.MIFF_CHOICE, h.destination())
        }

    @Test
    fun aProfileOlderThanTheTerms_isDeletedAndCreatedAgain_neverUpdated() =
        runTest(dispatcher) {
            val h = accountWithoutPhone(hasTermsVersion = false)
            val vm = h.vm(OnboardingMode.PHONE_ONLY)
            vm.onPhoneChanged("06 12 34 56 78")
            h.accounts.setLoaded(profileExists = false) // once deleted, the profile is gone
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertEquals(listOf("uid-1"), repo.deleted)
            assertTrue("never an update the rules would refuse", repo.phonesAdded.isEmpty())
            // The acceptance of the terms is asked again (consent panel), and what was typed is kept.
            assertEquals(AppDestination.CONSENT_PANEL, h.destination())
            assertEquals("06 12 34 56 78", drafts.stored!!.phoneText)
            assertEquals(DraftStep.PROFILE, drafts.stored!!.step)
        }

    @Test
    fun aRefusedUpdate_neverTriggersTheDeletionOfAnUpToDateProfile() =
        runTest(dispatcher) {
            // Rules not deployed yet, or App Check: the update is refused, but the profile DOES have its terms.
            val h = accountWithoutPhone(hasTermsVersion = true)
            repo.results = ArrayDeque(listOf(ProfileWriteResult.REFUSED))
            val vm = h.vm(OnboardingMode.PHONE_ONLY)
            vm.onPhoneChanged("06 12 34 56 78")
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertTrue(repo.deleted.isEmpty())
            assertEquals(ProfileMessage.SAVE_FAILED, vm.uiState.value.message)
            assertEquals(AppDestination.PHONE_ONLY, h.destination())
        }

    @Test
    fun theOldProfileIsReadBeforeItIsDeleted_andItsNamesAndPhotoPrefillTheSteps() =
        runTest(dispatcher) {
            val h = accountWithoutPhone(hasTermsVersion = false)
            repo.seed = ProfileSeed("Léa", "Martin", ByteArray(7) { 9 })
            val vm = h.vm(OnboardingMode.PHONE_ONLY)
            vm.onPhoneChanged("06 12 34 56 78")
            h.accounts.setLoaded(profileExists = false)
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertEquals("read first, then delete", listOf("read", "delete"), repo.calls)
            val draft = drafts.stored!!
            assertEquals("Léa", draft.firstName)
            assertEquals("Martin", draft.lastName)
            assertArrayEquals(ByteArray(7) { 9 }, draft.avatar)
            // The two steps that follow start pre-filled: nothing to type again.
            val next = h.vm(OnboardingMode.FULL).uiState.value
            assertEquals("Léa", next.firstName)
            assertEquals("Martin", next.lastName)
            assertNotNull(next.avatar)
        }

    @Test
    fun ifTheOldProfileCannotBeRead_nothingIsDeleted() =
        runTest(dispatcher) {
            val h = accountWithoutPhone(hasTermsVersion = false)
            repo.seed = null
            val vm = h.vm(OnboardingMode.PHONE_ONLY)
            vm.onPhoneChanged("06 12 34 56 78")
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertTrue(repo.deleted.isEmpty())
            assertEquals(ProfileMessage.SAVE_FAILED, vm.uiState.value.message)
            assertEquals(AppDestination.PHONE_ONLY, h.destination())
        }

    @Test
    fun anOldProfileThatBelongsToAMiff_isNeverDeleted_neutralMessageInstead() =
        runTest(dispatcher) {
            val h = accountWithoutPhone(hasTermsVersion = false, hasMembership = true)
            val vm = h.vm(OnboardingMode.PHONE_ONLY)
            vm.onPhoneChanged("06 12 34 56 78")
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertEquals("nothing touched: no read, no delete, no update", emptyList<String>(), repo.calls)
            assertEquals(ProfileMessage.ACCOUNT_UNAVAILABLE, vm.uiState.value.message)
            assertFalse(vm.uiState.value.isWriting)
            assertEquals(AppDestination.PHONE_ONLY, h.destination())
        }

    // --- Numbers that are valid but not for calling a person (review I2) ------------------------------------------------

    @Test
    fun aPremiumRateOrTollFreeNumber_isRefusedKindly_andNothingIsWritten() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.fillProfile()
            vm.onContinueFromProfile()
            vm.onCountrySelected("US")
            vm.onPhoneChanged("800 555 0123") // toll free
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.phoneInvalid)
            assertTrue(vm.uiState.value.phoneTypeRefused)
            assertEquals(0, repo.writes)
            vm.onPhoneChanged("202 555 0123") // a normal number goes through again
            assertFalse(vm.uiState.value.phoneTypeRefused)
            assertFalse(vm.uiState.value.phoneInvalid)
        }

    @Test
    fun aMalformedNumber_isNotTheTypeMessage() =
        runTest(dispatcher) {
            val h = newAccount()
            val vm = h.vm()
            vm.fillProfile()
            vm.onContinueFromProfile()
            vm.onPhoneChanged("06 12")
            vm.onSubmitPhone()
            assertTrue(vm.uiState.value.phoneInvalid)
            assertFalse(vm.uiState.value.phoneTypeRefused)
        }

    // --- Several refusals in a row (review m5) ---------------------------------------------------------------------------------

    @Test
    fun afterThreeRefusalsInARow_theMessageAdvisesToComeBackLater_andNothingIsLost() =
        runTest(dispatcher) {
            val h = newAccount()
            repo.results = ArrayDeque(listOf(ProfileWriteResult.REFUSED))
            val vm = h.vm()
            vm.reachPhoneStep()
            val messages = mutableListOf<ProfileMessage?>()
            repeat(3) {
                vm.onSubmitPhone()
                advanceUntilIdle()
                messages += vm.uiState.value.message
            }
            assertEquals(
                listOf(ProfileMessage.SAVE_FAILED, ProfileMessage.SAVE_FAILED, ProfileMessage.TRY_LATER),
                messages,
            )
            assertNotNull("everything typed is kept", drafts.stored)
            assertEquals(
                "Léa",
                vm.uiState.value.firstName
                    .trim(),
            )
        }

    @Test
    fun aMissingNetwork_doesNotCountAsARefusal() =
        runTest(dispatcher) {
            val h = newAccount()
            repo.results = ArrayDeque(listOf(ProfileWriteResult.OFFLINE))
            val vm = h.vm()
            vm.reachPhoneStep()
            repeat(5) {
                vm.onSubmitPhone()
                advanceUntilIdle()
            }
            assertEquals(ProfileMessage.NETWORK, vm.uiState.value.message)
        }

    // --- One person after another ----------------------------------------------------------------------------------

    @Test
    fun aSignOutErasesTheDraft_andStartsAnotherGeneration() =
        runTest(dispatcher) {
            // The container registers the draft store as a teardown of the session (AppContainer).
            val h =
                SessionHarness(sessionScope, listOf(drafts.asTeardown())).also {
                    it.gateway.setSignedIn("uid-1")
                    it.pending.save(PendingConsent("uid-1", ConsentVersions.TERMS, true))
                    it.accounts.setLoaded(profileExists = false)
                    it.manager.start()
                    advanceUntilIdle()
                }
            val vm = h.vm()
            vm.fillProfile()
            assertNotNull(drafts.stored)
            assertEquals(0, h.manager.generation)
            h.manager.signOut()
            advanceUntilIdle()
            assertNull("the draft goes with the session", drafts.stored)
            assertEquals("the next person gets a fresh view model", 1, h.manager.generation)
        }
}
