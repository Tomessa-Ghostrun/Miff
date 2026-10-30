package com.tomessa.miff.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.auth.PendingConsentStore
import com.tomessa.miff.data.auth.ProfileWriteResult
import com.tomessa.miff.data.auth.UserProfileRepository
import com.tomessa.miff.data.onboarding.DraftStep
import com.tomessa.miff.data.onboarding.ProfileDraft
import com.tomessa.miff.data.onboarding.ProfileDraftStore
import com.tomessa.miff.data.session.SessionManager
import com.tomessa.miff.domain.profile.AvatarCodec
import com.tomessa.miff.domain.profile.AvatarCompression
import com.tomessa.miff.domain.profile.CropMath
import com.tomessa.miff.domain.profile.DecodedPhoto
import com.tomessa.miff.domain.profile.PhoneNumbers
import com.tomessa.miff.domain.profile.PhoneResult
import com.tomessa.miff.domain.profile.ProfileForm
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * SECURITY, for this file: names, phone number and photo are personal data. They live in the UI state and the local draft
 * only, go to Firestore through [UserProfileRepository] and nowhere else: never logged, never in an analytics event, never
 * in an exception message. No analytics event is sent by these steps (ticket 6.3, "Analyse").
 */

/** FULL = profile then phone (new account). PHONE_ONLY = account without a number: the phone step alone (row 2b). */
enum class OnboardingMode { FULL, PHONE_ONLY }

enum class ProfileMessage {
    /** No network: nothing was written, the fields are kept. */
    NETWORK,

    /** The server did not take it (or something unexpected): the fields are kept, retry. */
    SAVE_FAILED,

    /** The chosen file is not an image we can use. */
    PHOTO_UNUSABLE,

    /** No camera application to take a photo. The gallery is still there. */
    CAMERA_UNAVAILABLE,
}

/** The cropped photo. Compared by identity: the state only changes when the picture does. */
class AvatarImage(
    val jpeg: ByteArray,
)

/** The photo being cropped: kept here (not in the screen) so that a rotation of the phone keeps zoom and position. */
data class CropState(
    val photo: DecodedPhoto,
    val zoom: Float,
    /** Centre of the square, in pixels of the photo. */
    val centerX: Float,
    val centerY: Float,
    val isEncoding: Boolean = false,
)

data class ProfileUiState(
    val mode: OnboardingMode,
    val step: DraftStep,
    val firstName: String = "",
    val lastName: String = "",
    /** The last keystroke would have gone past 50 characters: "50 caractères maximum." */
    val firstNameLimitHit: Boolean = false,
    val lastNameLimitHit: Boolean = false,
    val avatar: AvatarImage? = null,
    val crop: CropState? = null,
    val regionCode: String = PhoneNumbers.FALLBACK_REGION,
    val phoneText: String = "",
    /** Shown after a tap on the arrow with a number that does not look right. Cleared at the next change. */
    val phoneInvalid: Boolean = false,
    val isWriting: Boolean = false,
    val message: ProfileMessage? = null,
) {
    val canContinueFromProfile: Boolean
        get() = ProfileForm.isNameComplete(firstName) && ProfileForm.isNameComplete(lastName)

    /** The arrow of the phone step is grey until one digit was typed (criterion TEL1). */
    val canSubmitPhone: Boolean get() = PhoneNumbers.hasDigit(phoneText) && !isWriting
}

/**
 * The two steps of the profile creation (Onboarding ticket 6.3 and 6.4) and the phone step alone for an older account.
 *
 * - NO write at the profile step: names, photo, country and number are kept in a local draft (never the password);
 * - ONE write, at the end of the phone step, with every key of the profile ([UserProfileRepository.create]); the next
 *   screen only opens once the server has confirmed it; offline, a calm message and the fields stay as they are;
 * - the draft is erased after the confirmation (and at every sign-out, see AppContainer).
 */
class ProfileViewModel(
    private val mode: OnboardingMode,
    private val session: SessionManager,
    private val pending: PendingConsentStore,
    private val drafts: ProfileDraftStore,
    private val profiles: UserProfileRepository,
    private val avatars: AvatarCodec,
    private val network: NetworkMonitor,
    deviceRegion: () -> String?,
    private val worker: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val uid: String? = session.currentUid

    /** Once the profile is confirmed or the draft erased, nothing is written to the draft any more. */
    private var draftClosed = false

    private val mutableState: MutableStateFlow<ProfileUiState>
    val uiState: StateFlow<ProfileUiState>

    init {
        val draft = uid?.let { drafts.read(it) }
        // Names: the draft first (what the person typed), else the name given by the provider (a suggestion, editable).
        val (suggestedFirst, suggestedLast) = ProfileForm.splitDisplayName(session.suggestedDisplayName())
        val firstName = draft?.firstName?.takeIf { it.isNotBlank() } ?: suggestedFirst
        val lastName = draft?.lastName?.takeIf { it.isNotBlank() } ?: suggestedLast
        val region =
            draft?.regionCode?.takeIf { PhoneNumbers.isSupportedRegion(it) }
                ?: PhoneNumbers.defaultRegion(deviceRegion())
        mutableState =
            MutableStateFlow(
                ProfileUiState(
                    mode = mode,
                    step = if (mode == OnboardingMode.PHONE_ONLY) DraftStep.PHONE else draft?.step ?: DraftStep.PROFILE,
                    firstName = if (mode == OnboardingMode.FULL) firstName else "",
                    lastName = if (mode == OnboardingMode.FULL) lastName else "",
                    avatar = if (mode == OnboardingMode.FULL) draft?.avatar?.let(::AvatarImage) else null,
                    regionCode = region,
                    phoneText = draft?.phoneText.orEmpty(),
                ),
            )
        uiState = mutableState.asStateFlow()
        // Loads the numbering plan of the country off the main thread, so that the phone step opens without a hitch.
        viewModelScope.launch(worker) { PhoneNumbers.exampleHint(region) }
    }

    // --- Profile step ------------------------------------------------------------------------------------------

    fun onFirstNameChanged(raw: String) {
        val limited = ProfileForm.limitInput(raw)
        mutableState.update { it.copy(firstName = limited.text, firstNameLimitHit = limited.wasCut) }
        saveDraft()
    }

    fun onLastNameChanged(raw: String) {
        val limited = ProfileForm.limitInput(raw)
        mutableState.update { it.copy(lastName = limited.text, lastNameLimitHit = limited.wasCut) }
        saveDraft()
    }

    /** The arrow of the profile step: nothing is written, only the next step. */
    fun onContinueFromProfile() {
        val state = mutableState.value
        if (mode != OnboardingMode.FULL || state.step != DraftStep.PROFILE || !state.canContinueFromProfile) return
        mutableState.update { it.copy(step = DraftStep.PHONE, message = null) }
        saveDraft()
    }

    /** Arrow back of the phone step (and system back): the profile step, everything typed is kept. */
    fun onBackToProfile() {
        if (mode != OnboardingMode.FULL || mutableState.value.isWriting ||
            mutableState.value.step == DraftStep.PROFILE
        ) {
            return
        }
        mutableState.update { it.copy(step = DraftStep.PROFILE, message = null, phoneInvalid = false) }
        saveDraft()
    }

    // --- Photo -------------------------------------------------------------------------------------------------

    /** A photo was chosen (gallery or camera). [source] is the address of the file; null = the person closed the picker. */
    fun onPhotoChosen(source: String?) {
        if (source == null) return
        mutableState.update { it.copy(message = null) }
        viewModelScope.launch {
            val photo = withContext(worker) { avatars.decode(source) }
            if (photo == null) {
                mutableState.update { it.copy(message = ProfileMessage.PHOTO_UNUSABLE) }
            } else {
                mutableState.update {
                    it.copy(crop = CropState(photo, CropMath.MIN_ZOOM, photo.width / 2f, photo.height / 2f))
                }
            }
        }
    }

    /** Pinch and drag in the crop screen, or the zoom slider. The square is kept inside the photo. */
    fun onCropChanged(
        zoom: Float,
        centerX: Float,
        centerY: Float,
    ) {
        mutableState.update { state ->
            val crop = state.crop ?: return@update state
            val z = CropMath.clampZoom(zoom)
            val (x, y) = CropMath.clampCenter(crop.photo.width, crop.photo.height, z, centerX, centerY)
            state.copy(crop = crop.copy(zoom = z, centerX = x, centerY = y))
        }
    }

    fun onCropCancelled() {
        mutableState.update { if (it.crop?.isEncoding == true) it else it.copy(crop = null) }
    }

    /** "Valider" in the crop screen: cuts the square, re-encodes it (no metadata, 20 000 bytes at most), keeps it. */
    fun onCropConfirmed() {
        val crop = mutableState.value.crop ?: return
        if (crop.isEncoding) return
        mutableState.update { it.copy(crop = crop.copy(isEncoding = true)) }
        viewModelScope.launch {
            val square = CropMath.toCrop(crop.photo.width, crop.photo.height, crop.zoom, crop.centerX, crop.centerY)
            val bytes = withContext(worker) { avatars.encode(crop.photo, square) }
            mutableState.update {
                // The codec already keeps to 20 000 bytes; the rules would refuse a bigger one, so it is checked here too.
                if (bytes == null || bytes.size > AvatarCompression.MAX_BYTES) {
                    it.copy(crop = null, message = ProfileMessage.PHOTO_UNUSABLE)
                } else {
                    it.copy(crop = null, avatar = AvatarImage(bytes), message = null)
                }
            }
            saveDraft()
        }
    }

    fun onRemovePhoto() {
        mutableState.update { it.copy(avatar = null) }
        saveDraft()
    }

    fun onCameraUnavailable() {
        mutableState.update { it.copy(message = ProfileMessage.CAMERA_UNAVAILABLE) }
    }

    fun onMessageDismissed() {
        mutableState.update { it.copy(message = null) }
    }

    // --- Phone step --------------------------------------------------------------------------------------------

    fun onCountrySelected(region: String) {
        if (!PhoneNumbers.isSupportedRegion(region)) return
        mutableState.update { it.copy(regionCode = region.uppercase(), phoneInvalid = false, message = null) }
        saveDraft()
    }

    /** Typing or pasting. A "+" number of a known country selects that country and keeps the national part (TEL2). */
    fun onPhoneChanged(raw: String) {
        val text = raw.take(MAX_PHONE_INPUT)
        val detected = PhoneNumbers.detectInternational(text)
        mutableState.update {
            it.copy(
                phoneText = detected?.national ?: text,
                regionCode = detected?.region ?: it.regionCode,
                phoneInvalid = false,
                message = null,
            )
        }
        saveDraft()
    }

    /** The arrow of the phone step: checks the form, then ONE write confirmed by the server. */
    fun onSubmitPhone() {
        val state = mutableState.value
        if (state.step != DraftStep.PHONE || state.isWriting || !PhoneNumbers.hasDigit(state.phoneText)) return
        val phone = PhoneNumbers.normalize(state.regionCode, state.phoneText) as? PhoneResult.Valid
        if (phone == null) {
            // Nothing leaves the phone: a calm message under the field.
            mutableState.update { it.copy(phoneInvalid = true, message = null) }
            return
        }
        val me = uid
        if (me == null || session.currentUid != me) return
        if (!network.isOnline()) {
            mutableState.update { it.copy(message = ProfileMessage.NETWORK) }
            return
        }
        mutableState.update { it.copy(isWriting = true, message = null, phoneInvalid = false) }
        viewModelScope.launch {
            // Whatever happens to the screen, the write is followed to its end: a result nobody reads would leave the
            // draft and the screen out of step with what the server holds.
            withContext(NonCancellable) { write(me, phone.e164, state) }
        }
    }

    private suspend fun write(
        me: String,
        phone: String,
        state: ProfileUiState,
    ) {
        if (mode == OnboardingMode.PHONE_ONLY) {
            writePhoneOnly(me, phone)
            return
        }
        val consent = pending.read(me)
        if (consent == null || !state.canContinueFromProfile) {
            // The acceptance of the terms is not on this device any more: back to the start of the account's routing,
            // which shows the consent panel. Nothing was written.
            mutableState.update { it.copy(isWriting = false) }
            session.retry()
            return
        }
        val result =
            profiles.create(
                uid = me,
                firstName = ProfileForm.normalizeName(state.firstName),
                lastName = ProfileForm.normalizeName(state.lastName),
                avatarJpeg = state.avatar?.jpeg,
                phone = phone,
                consent = consent,
            )
        if (result.isSaved()) {
            eraseDraft()
            session.onProfileSaved(consent.analyticsAccepted)
            wipe()
        } else {
            reportFailure(result)
        }
    }

    /** Older account: the profile exists. Its phone number is added; or, if it predates AUTH-B, it is created again. */
    private suspend fun writePhoneOnly(
        me: String,
        phone: String,
    ) {
        if (session.accountSnapshot?.hasTermsVersion == false) {
            // The rules refuse any update of a profile without `termsVersion`: it is deleted, and the account goes through
            // the consent panel and the two steps again. What was typed here is kept in the draft.
            val deleted = profiles.delete(me)
            if (deleted == ProfileWriteResult.DONE) {
                mutableState.update { it.copy(isWriting = false) }
                saveDraftAt(DraftStep.PROFILE)
                session.retry()
            } else {
                reportFailure(deleted)
            }
            return
        }
        val result = profiles.addPhone(me, phone)
        if (result.isSaved()) {
            eraseDraft()
            // Existing accounts keep their own analytics choice: the account is read again by the session.
            session.onProfileSaved(analyticsConsentEnabled = session.accountSnapshot?.analyticsConsentEnabled ?: false)
            wipe()
        } else {
            reportFailure(result)
        }
    }

    /**
     * The server said yes, or said no although the profile IS there: an earlier try that ended after our time limit can
     * land late, and the second try is then refused (the profile exists and its creation date cannot change). A "no" is
     * checked against what the server really holds, so the person is not stopped on a profile that is already saved.
     * Never for a network failure (nothing could be asked).
     */
    private suspend fun ProfileWriteResult.isSaved(): Boolean =
        this == ProfileWriteResult.DONE ||
            ((this == ProfileWriteResult.REFUSED || this == ProfileWriteResult.FAILED) && session.profileAlreadySaved())

    /** Once the profile is saved, nothing typed stays in the memory of this view model (it can outlive the screen). */
    private fun wipe() {
        mutableState.value =
            ProfileUiState(mode = mode, step = mutableState.value.step, regionCode = mutableState.value.regionCode)
    }

    private fun reportFailure(result: ProfileWriteResult) {
        val message = if (result == ProfileWriteResult.OFFLINE) ProfileMessage.NETWORK else ProfileMessage.SAVE_FAILED
        mutableState.update { it.copy(isWriting = false, message = message) }
    }

    // --- Draft -------------------------------------------------------------------------------------------------

    private fun saveDraft() = saveDraftAt(mutableState.value.step)

    private fun saveDraftAt(step: DraftStep) {
        val me = uid ?: return
        if (draftClosed) return
        val state = mutableState.value
        drafts.save(
            ProfileDraft(
                uid = me,
                step = step,
                firstName = state.firstName,
                lastName = state.lastName,
                regionCode = state.regionCode,
                phoneText = state.phoneText,
                avatar = state.avatar?.jpeg,
            ),
        )
    }

    private fun eraseDraft() {
        draftClosed = true
        drafts.clear()
    }

    companion object {
        /** More than a phone number can hold: pasted text is cut here (the number is then simply invalid). */
        const val MAX_PHONE_INPUT = 40
    }
}
