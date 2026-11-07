package com.tomessa.miff.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.profile.CurrentProfile
import com.tomessa.miff.data.profile.MyProfileLoad
import com.tomessa.miff.data.profile.MyProfileRepository
import com.tomessa.miff.data.profile.ProfileSaveResult
import com.tomessa.miff.data.session.SessionManager
import com.tomessa.miff.domain.profile.AvatarCodec
import com.tomessa.miff.domain.profile.AvatarCompression
import com.tomessa.miff.domain.profile.CropMath
import com.tomessa.miff.domain.profile.MyProfile
import com.tomessa.miff.domain.profile.PhoneNumbers
import com.tomessa.miff.domain.profile.PhoneResult
import com.tomessa.miff.domain.profile.ProfileForm
import com.tomessa.miff.domain.profile.contentEqualsNullable
import com.tomessa.miff.ui.profile.AvatarImage
import com.tomessa.miff.ui.profile.CropState
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
 * SECURITY, for this file: names, phone number and photo are personal data. They live in the UI state, go to Firestore
 * through [MyProfileRepository] and nowhere else: never logged, never in an analytics event, never in an exception
 * message. No analytics event is sent by this screen.
 */

enum class MyInfoLoad { LOADING, READY, FAILED }

enum class MyInfoMessage {
    /** The save is confirmed by the server. */
    SAVED,

    /** The profile is saved but the copy seen by the Miffs could not be reached yet: saving again finishes it. */
    SYNC_PENDING,

    /** No network: nothing was written, the fields are kept. */
    NETWORK,

    /** The server did not take it: the fields are kept, retry. */
    SAVE_FAILED,

    /** Several refusals in a row: kind advice to come back later. */
    TRY_LATER,

    /** The chosen file is not an image we can use. */
    PHOTO_UNUSABLE,

    /** No camera application to take a photo. The gallery is still there. */
    CAMERA_UNAVAILABLE,
}

data class MyInfoUiState(
    val load: MyInfoLoad = MyInfoLoad.LOADING,
    val firstName: String = "",
    val lastName: String = "",
    val firstNameLimitHit: Boolean = false,
    val lastNameLimitHit: Boolean = false,
    /** The person emptied the field: "il faut au moins un caractère" is shown (never before they touched it). */
    val firstNameBlank: Boolean = false,
    val lastNameBlank: Boolean = false,
    val avatar: AvatarImage? = null,
    val crop: CropState? = null,
    val regionCode: String = PhoneNumbers.FALLBACK_REGION,
    val phoneText: String = "",
    /** Shown once the person left the field (or tapped "Enregistrer") with a number that does not look right. */
    val phoneInvalid: Boolean = false,
    /** A valid number of a kind that is not accepted (premium rate, toll free...): a more precise sentence. */
    val phoneTypeRefused: Boolean = false,
    val isSaving: Boolean = false,
    val message: MyInfoMessage? = null,
    /** Something differs from what the server holds (or a copy is still to be written). */
    val hasChanges: Boolean = false,
    /** Something the person typed or chose is not saved yet: leaving the screen asks for a confirmation. */
    val hasEdits: Boolean = false,
    val namesComplete: Boolean = false,
) {
    /** "Enregistrer" is grey until something changed AND every value is usable (ticket, criterion A5). */
    val canSave: Boolean
        get() =
            load == MyInfoLoad.READY && hasChanges && namesComplete && PhoneNumbers.hasDigit(phoneText) &&
                !phoneInvalid && !isSaving
}

/**
 * Settings > Mes informations (ticket ONB-3a, criteria A5 to A8): photo, first name, last name, country and phone number,
 * with the rules of the Onboarding steps (names 1 to 50 characters, the number is mandatory and stored in E.164).
 *
 * The save is ONE confirmed write of `users/{uid}` and of the copy of each Miff (see [MyProfileRepository]); offline, a calm
 * message and nothing is queued. If only the copies of the Miffs are still to be written, the same button finishes the job.
 */
class MyInfoViewModel(
    private val session: SessionManager,
    private val repository: MyProfileRepository,
    private val current: CurrentProfile,
    private val avatars: AvatarCodec,
    private val network: NetworkMonitor,
    private val deviceRegion: () -> String?,
    private val worker: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val uid: String? = session.currentUid

    /** What the server holds, the reference of "something changed". Null until the profile is read. */
    private var original: MyProfile? = null
    private var originalRegion: String = PhoneNumbers.FALLBACK_REGION
    private var originalNational: String = ""

    private var firstNameTouched = false
    private var lastNameTouched = false

    /** The profile is saved but some copies are not: the button stays usable although nothing changed. */
    private var syncPending = false

    /** Consecutive answers "no" from the server (not counting a missing network); reset by a success. */
    private var serverRefusals = 0

    private val mutableState = MutableStateFlow(MyInfoUiState())
    val uiState: StateFlow<MyInfoUiState> = mutableState.asStateFlow()

    init {
        val known = current.profile.value
        if (known != null) apply(known) else load()
    }

    /** "Réessayer" after a failed read. */
    fun retry() {
        if (mutableState.value.load == MyInfoLoad.LOADING) return
        load()
    }

    private fun load() {
        val me = uid
        if (me == null) {
            mutableState.update { it.copy(load = MyInfoLoad.FAILED) }
            return
        }
        mutableState.update { it.copy(load = MyInfoLoad.LOADING) }
        viewModelScope.launch {
            when (val result = repository.read(me)) {
                is MyProfileLoad.Loaded -> {
                    if (session.currentUid == me) apply(result.profile)
                }

                MyProfileLoad.Unavailable -> {
                    mutableState.update { it.copy(load = MyInfoLoad.FAILED) }
                }
            }
        }
    }

    private fun apply(profile: MyProfile) {
        original = profile
        val region = PhoneNumbers.defaultRegion(deviceRegion())
        val detected = profile.phone?.let { PhoneNumbers.detectInternational(it) }
        originalRegion = detected?.region ?: region
        originalNational = detected?.national.orEmpty()
        mutableState.value =
            derive(
                MyInfoUiState(
                    load = MyInfoLoad.READY,
                    firstName = profile.firstName,
                    lastName = profile.lastName,
                    avatar = profile.avatarJpeg?.let(::AvatarImage),
                    regionCode = originalRegion,
                    phoneText = originalNational,
                ),
            )
    }

    // --- Fields ------------------------------------------------------------------------------------------------

    fun onFirstNameChanged(raw: String) {
        firstNameTouched = true
        val limited = ProfileForm.limitInput(raw)
        change { it.copy(firstName = limited.text, firstNameLimitHit = limited.wasCut, message = null) }
    }

    fun onLastNameChanged(raw: String) {
        lastNameTouched = true
        val limited = ProfileForm.limitInput(raw)
        change { it.copy(lastName = limited.text, lastNameLimitHit = limited.wasCut, message = null) }
    }

    fun onCountrySelected(region: String) {
        if (!PhoneNumbers.isSupportedRegion(region)) return
        change {
            it.copy(
                regionCode = region.uppercase(),
                phoneInvalid = false,
                phoneTypeRefused = false,
                message = null,
            )
        }
    }

    /** Typing or pasting. A "+" number of a known country selects that country and keeps the national part. */
    fun onPhoneChanged(raw: String) {
        val text = raw.take(MAX_PHONE_INPUT)
        val detected = PhoneNumbers.detectInternational(text)
        change {
            it.copy(
                phoneText = detected?.national ?: text,
                regionCode = detected?.region ?: it.regionCode,
                phoneInvalid = false,
                phoneTypeRefused = false,
                message = null,
            )
        }
    }

    /** The person left the phone field: a number that does not look right is pointed out, calmly, now. */
    fun onPhoneFocusLost() {
        val state = mutableState.value
        if (state.load != MyInfoLoad.READY || state.isSaving || !PhoneNumbers.hasDigit(state.phoneText)) return
        // Untouched number: nothing to point out.
        if (state.regionCode == originalRegion && state.phoneText == originalNational) return
        checkPhone(state)
    }

    // --- Photo -------------------------------------------------------------------------------------------------

    /** A photo was chosen (gallery or camera). [source] is the address of the file; null = the person closed the picker. */
    fun onPhotoChosen(source: String?) {
        if (source == null) return
        change { it.copy(message = null) }
        viewModelScope.launch {
            val photo = withContext(worker) { avatars.decode(source) }
            if (photo == null) {
                change { it.copy(message = MyInfoMessage.PHOTO_UNUSABLE) }
            } else {
                change { it.copy(crop = CropState(photo, CropMath.MIN_ZOOM, photo.width / 2f, photo.height / 2f)) }
            }
        }
    }

    fun onCropChanged(
        zoom: Float,
        centerX: Float,
        centerY: Float,
    ) {
        change { state ->
            val crop = state.crop ?: return@change state
            val z = CropMath.clampZoom(zoom)
            val (x, y) = CropMath.clampCenter(crop.photo.width, crop.photo.height, z, centerX, centerY)
            state.copy(crop = crop.copy(zoom = z, centerX = x, centerY = y))
        }
    }

    fun onCropCancelled() {
        change { if (it.crop?.isEncoding == true) it else it.copy(crop = null) }
    }

    /** "Utiliser cette photo": cuts the square, re-encodes it (no metadata, 20 000 bytes at most). */
    fun onCropConfirmed() {
        val crop = mutableState.value.crop ?: return
        if (crop.isEncoding) return
        change { it.copy(crop = crop.copy(isEncoding = true)) }
        viewModelScope.launch {
            val square = CropMath.toCrop(crop.photo.width, crop.photo.height, crop.zoom, crop.centerX, crop.centerY)
            val bytes = withContext(worker) { avatars.encode(crop.photo, square) }
            change {
                if (bytes == null || bytes.size > AvatarCompression.MAX_BYTES) {
                    it.copy(crop = null, message = MyInfoMessage.PHOTO_UNUSABLE)
                } else {
                    it.copy(crop = null, avatar = AvatarImage(bytes), message = null)
                }
            }
        }
    }

    fun onRemovePhoto() {
        change { it.copy(avatar = null, message = null) }
    }

    fun onCameraUnavailable() {
        change { it.copy(message = MyInfoMessage.CAMERA_UNAVAILABLE) }
    }

    fun onMessageDismissed() {
        change { it.copy(message = null) }
    }

    // --- Save --------------------------------------------------------------------------------------------------

    /** "Enregistrer": checks the number, then ONE confirmed write (profile and copies). Nothing is queued offline. */
    fun onSave() {
        val state = mutableState.value
        if (!state.canSave) return
        val phone = checkPhone(state) ?: return
        val me = uid
        if (me == null || session.currentUid != me) return
        if (!network.isOnline()) {
            change { it.copy(message = MyInfoMessage.NETWORK) }
            return
        }
        val profile =
            MyProfile(
                firstName = ProfileForm.normalizeName(state.firstName),
                lastName = ProfileForm.normalizeName(state.lastName),
                phone = phone,
                avatarJpeg = state.avatar?.jpeg,
            )
        change { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            // Whatever happens to the screen, the write is followed to its end: a result nobody reads would leave the
            // state of the app out of step with what the server holds.
            withContext(NonCancellable) { finish(me, profile, repository.save(me, profile)) }
        }
    }

    private fun finish(
        me: String,
        profile: MyProfile,
        result: ProfileSaveResult,
    ) {
        when (result) {
            ProfileSaveResult.SAVED, ProfileSaveResult.SAVED_PARTIAL -> {
                serverRefusals = 0
                syncPending = result == ProfileSaveResult.SAVED_PARTIAL
                current.onSaved(me, profile)
                original = profile
                val detected = profile.phone?.let { PhoneNumbers.detectInternational(it) }
                originalRegion = detected?.region ?: originalRegion
                originalNational = detected?.national ?: originalNational
                mutableState.value =
                    derive(
                        mutableState.value.copy(
                            isSaving = false,
                            firstName = profile.firstName,
                            lastName = profile.lastName,
                            firstNameLimitHit = false,
                            lastNameLimitHit = false,
                            regionCode = originalRegion,
                            phoneText = originalNational,
                            message = if (syncPending) MyInfoMessage.SYNC_PENDING else MyInfoMessage.SAVED,
                        ),
                    )
                firstNameTouched = false
                lastNameTouched = false
            }

            ProfileSaveResult.OFFLINE -> {
                mutableState.update { it.copy(isSaving = false, message = MyInfoMessage.NETWORK) }
            }

            ProfileSaveResult.REFUSED, ProfileSaveResult.FAILED -> {
                serverRefusals++
                val message =
                    if (serverRefusals >=
                        REFUSALS_BEFORE_ADVICE
                    ) {
                        MyInfoMessage.TRY_LATER
                    } else {
                        MyInfoMessage.SAVE_FAILED
                    }
                mutableState.update { it.copy(isSaving = false, message = message) }
            }
        }
    }

    /** E.164 of what is typed, or null (and the calm message is shown). */
    private fun checkPhone(state: MyInfoUiState): String? {
        val checked = PhoneNumbers.normalize(state.regionCode, state.phoneText)
        val valid = checked as? PhoneResult.Valid
        if (valid == null) {
            val typeRefused = checked == PhoneResult.UnsupportedType
            change { it.copy(phoneInvalid = true, phoneTypeRefused = typeRefused, message = null) }
            return null
        }
        return valid.e164
    }

    // --- State ---------------------------------------------------------------------------------------------------

    private fun change(transform: (MyInfoUiState) -> MyInfoUiState) {
        mutableState.update { derive(transform(it)) }
    }

    /** The values worked out from the others: names complete, blank-field messages, "something changed". */
    private fun derive(state: MyInfoUiState): MyInfoUiState {
        val reference = original ?: return state
        val first = ProfileForm.normalizeName(state.firstName)
        val last = ProfileForm.normalizeName(state.lastName)
        val changed =
            first != reference.firstName ||
                last != reference.lastName ||
                !state.avatar?.jpeg.contentEqualsNullable(reference.avatarJpeg) ||
                state.regionCode != originalRegion ||
                state.phoneText != originalNational
        return state.copy(
            namesComplete = ProfileForm.isNameComplete(state.firstName) && ProfileForm.isNameComplete(state.lastName),
            firstNameBlank = firstNameTouched && first.isEmpty(),
            lastNameBlank = lastNameTouched && last.isEmpty(),
            hasChanges = changed || syncPending,
            hasEdits = changed,
        )
    }

    companion object {
        /** After this many refusals in a row, the message advises to come back later instead of "try again". */
        const val REFUSALS_BEFORE_ADVICE = 3

        /** More than a phone number can hold: pasted text is cut here (the number is then simply invalid). */
        const val MAX_PHONE_INPUT = 40
    }
}
