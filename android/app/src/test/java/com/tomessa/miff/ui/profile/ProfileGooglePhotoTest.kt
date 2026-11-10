package com.tomessa.miff.ui.profile

import com.tomessa.miff.data.onboarding.DraftStep
import com.tomessa.miff.data.onboarding.ProfileDraft
import com.tomessa.miff.domain.auth.ConsentVersions
import com.tomessa.miff.domain.auth.PendingConsent
import com.tomessa.miff.domain.profile.GooglePhotoPrefill
import com.tomessa.miff.testsupport.FakeAvatarCodec
import com.tomessa.miff.testsupport.FakeGooglePhotoSource
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.FakeUserProfileRepository
import com.tomessa.miff.testsupport.MemoryProfileDraftStore
import com.tomessa.miff.testsupport.SessionHarness
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
import java.io.IOException

/**
 * The Google photo as the PRE-FILLED profile photo (decision of the PM, 2026-11-08): taken once, never blocking, never
 * over a photo or a choice of the person, never for an account that is not a Google one or that already has a profile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileGooglePhotoTest {
    private val dispatcher = StandardTestDispatcher()
    private val sessionScope = CoroutineScope(dispatcher + SupervisorJob())

    private val drafts = MemoryProfileDraftStore()
    private val repo = FakeUserProfileRepository()
    private val codec = FakeAvatarCodec()
    private val source = FakeGooglePhotoSource()
    private val network = FakeNetwork()
    private val googleAddress = "https://lh3.googleusercontent.com/a/ACg8ocJabc=s96-c"
    private val googleJpeg = ByteArray(8_000) { 4 }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        codec.encoded = googleJpeg
    }

    @After
    fun tearDown() {
        sessionScope.cancel()
        Dispatchers.resetMain()
    }

    private fun TestScope.account(photoUrl: String? = googleAddress): SessionHarness =
        SessionHarness(sessionScope).also {
            it.gateway.setSignedIn("uid-1")
            it.gateway.photoUrl = photoUrl
            it.pending.save(PendingConsent("uid-1", ConsentVersions.TERMS, true))
            it.accounts.setLoaded(profileExists = false)
            it.manager.start()
            advanceUntilIdle()
        }

    private fun SessionHarness.vm(
        mode: OnboardingMode = OnboardingMode.FULL,
        prefill: GooglePhotoPrefill? = GooglePhotoPrefill(source, codec),
    ) = ProfileViewModel(
        mode = mode,
        session = manager,
        pending = pending,
        drafts = drafts,
        profiles = repo,
        avatars = codec,
        network = network,
        deviceRegion = { "FR" },
        googlePhoto = prefill,
        worker = dispatcher,
    )

    @Test
    fun theGooglePhotoPrefillsTheProfilePhoto_withTheHint() =
        runTest(dispatcher) {
            val vm = account().vm()
            advanceUntilIdle()
            val state = vm.uiState.value
            assertArrayEquals(googleJpeg, state.avatar!!.jpeg)
            assertTrue(state.googlePhotoPrefilled)
            assertNull("no message of any kind", state.message)
            assertEquals("downloaded once", 1, source.requested.size)
            assertTrue(source.requested.single().endsWith("=s256-c"))
        }

    @Test
    fun theStepIsUsableAtOnce_beforeThePhotoArrives_andNothingWaitsForIt() =
        runTest(dispatcher) {
            source.gate = CompletableDeferred()
            val vm = account().vm()
            vm.onFirstNameChanged("Léa")
            vm.onLastNameChanged("Martin")
            runCurrent()
            assertTrue("the arrow works while the photo is still on its way", vm.uiState.value.canContinueFromProfile)
            assertNull(vm.uiState.value.avatar)
            source.gate!!.complete(Unit)
            advanceUntilIdle()
            assertNotNull(vm.uiState.value.avatar)
        }

    @Test
    fun theGooglePhotoIsWrittenWithTheProfile_likeAnyPhoto() =
        runTest(dispatcher) {
            val vm = account().vm()
            advanceUntilIdle()
            vm.onFirstNameChanged("Léa")
            vm.onLastNameChanged("Martin")
            vm.onContinueFromProfile()
            vm.onPhoneChanged("6 12 34 56 78")
            vm.onSubmitPhone()
            advanceUntilIdle()
            assertArrayEquals(googleJpeg, repo.created.single().avatarJpeg)
        }

    @Test
    fun thePersonCanRemoveIt_andTheHintGoes() =
        runTest(dispatcher) {
            val vm = account().vm()
            advanceUntilIdle()
            vm.onRemovePhoto()
            assertNull(vm.uiState.value.avatar)
            assertFalse(vm.uiState.value.googlePhotoPrefilled)
            assertNull("the draft remembers the choice", drafts.stored!!.avatar)
        }

    @Test
    fun thePersonCanReplaceIt_andTheHintGoes() =
        runTest(dispatcher) {
            val vm = account().vm()
            advanceUntilIdle()
            codec.encoded = ByteArray(5_000) { 9 }
            vm.onPhotoChosen("content://picked/1")
            advanceUntilIdle()
            assertTrue("the Google photo stays until the new one is validated", vm.uiState.value.googlePhotoPrefilled)
            vm.onCropConfirmed()
            advanceUntilIdle()
            assertEquals(
                5_000,
                vm.uiState.value.avatar!!
                    .jpeg.size,
            )
            assertFalse(vm.uiState.value.googlePhotoPrefilled)
        }

    @Test
    fun aRemovedPhoto_isNotTakenAgainAtTheNextVisit() =
        runTest(dispatcher) {
            val h = account()
            val first = h.vm()
            advanceUntilIdle()
            first.onRemovePhoto()
            source.requested.clear()
            val second = h.vm()
            advanceUntilIdle()
            assertNull(second.uiState.value.avatar)
            assertTrue("a draft exists: the person has been here, the choice is theirs", source.requested.isEmpty())
        }

    @Test
    fun aPhotoAlreadyInTheDraft_isNeverOverwritten() =
        runTest(dispatcher) {
            val mine = ByteArray(1_234) { 8 }
            drafts.save(ProfileDraft("uid-1", DraftStep.PROFILE, "Léa", "Martin", "FR", "", mine))
            val vm = account().vm()
            advanceUntilIdle()
            assertArrayEquals(
                mine,
                vm.uiState.value.avatar!!
                    .jpeg,
            )
            assertFalse(vm.uiState.value.googlePhotoPrefilled)
            assertTrue(source.requested.isEmpty())
        }

    @Test
    fun aPhotoChosenWhileTheGoogleOneIsOnItsWay_isNotOverwritten() =
        runTest(dispatcher) {
            source.gate = CompletableDeferred()
            val vm = account().vm()
            runCurrent()
            val mine = ByteArray(2_500) { 6 }
            codec.encoded = mine
            vm.onPhotoChosen("content://picked/1")
            runCurrent()
            vm.onCropConfirmed()
            runCurrent()
            codec.encoded = googleJpeg
            source.gate!!.complete(Unit)
            advanceUntilIdle()
            assertArrayEquals(
                mine,
                vm.uiState.value.avatar!!
                    .jpeg,
            )
            assertFalse(vm.uiState.value.googlePhotoPrefilled)
        }

    @Test
    fun aCropInProgress_isNotDisturbed() =
        runTest(dispatcher) {
            source.gate = CompletableDeferred()
            val vm = account().vm()
            runCurrent()
            vm.onPhotoChosen("content://picked/1")
            runCurrent()
            assertNotNull(vm.uiState.value.crop)
            source.gate!!.complete(Unit)
            advanceUntilIdle()
            assertNull(vm.uiState.value.avatar)
            assertNotNull(vm.uiState.value.crop)
        }

    @Test
    fun ifThePersonMovedOnToThePhoneStep_theLatePhotoIsDropped() =
        runTest(dispatcher) {
            source.gate = CompletableDeferred()
            val vm = account().vm()
            vm.onFirstNameChanged("Léa")
            vm.onLastNameChanged("Martin")
            vm.onContinueFromProfile()
            runCurrent()
            source.gate!!.complete(Unit)
            advanceUntilIdle()
            assertNull("a photo they never saw is not written for them", vm.uiState.value.avatar)
        }

    @Test
    fun aNetworkFailure_neverBlocksNorSaysAnything() =
        runTest(dispatcher) {
            source.failure = IOException("offline")
            val vm = account().vm()
            advanceUntilIdle()
            val state = vm.uiState.value
            assertNull(state.avatar)
            assertFalse(state.googlePhotoPrefilled)
            assertNull(state.message)
            vm.onFirstNameChanged("Léa")
            vm.onLastNameChanged("Martin")
            assertTrue("the sign-up goes on without a photo", vm.uiState.value.canContinueFromProfile)
        }

    @Test
    fun aPictureThatCannotBeUsed_isSilentToo() =
        runTest(dispatcher) {
            codec.decoded = null
            val vm = account().vm()
            advanceUntilIdle()
            assertNull(vm.uiState.value.avatar)
            assertNull(vm.uiState.value.message)
        }

    @Test
    fun aNonGoogleAddress_isNeverDownloaded() =
        runTest(dispatcher) {
            val vm = account("https://example.com/me.jpg").vm()
            advanceUntilIdle()
            assertNull(vm.uiState.value.avatar)
            assertTrue(source.requested.isEmpty())
        }

    @Test
    fun theGenericGooglePicture_isNotUsed() =
        runTest(dispatcher) {
            val vm = account("https://lh3.googleusercontent.com/a/default-user=s96-c").vm()
            advanceUntilIdle()
            assertNull(vm.uiState.value.avatar)
            assertTrue(source.requested.isEmpty())
        }

    @Test
    fun anEmailAccount_hasNoPhotoAddress_andNothingChanges() =
        runTest(dispatcher) {
            val vm = account(photoUrl = null).vm()
            advanceUntilIdle()
            assertNull(vm.uiState.value.avatar)
            assertFalse(vm.uiState.value.googlePhotoPrefilled)
            assertTrue(source.requested.isEmpty())
            assertTrue(codec.decodedBytes.isEmpty())
        }

    @Test
    fun theAccountThatAlreadyHasAPhoneOnlyStep_isNeverTouched() =
        runTest(dispatcher) {
            val vm = account().vm(mode = OnboardingMode.PHONE_ONLY)
            advanceUntilIdle()
            assertNull(vm.uiState.value.avatar)
            assertTrue(source.requested.isEmpty())
        }

    @Test
    fun withoutThePrefillTool_nothingHappens() =
        runTest(dispatcher) {
            val vm = account().vm(prefill = null)
            advanceUntilIdle()
            assertNull(vm.uiState.value.avatar)
            assertTrue(source.requested.isEmpty())
        }
}
