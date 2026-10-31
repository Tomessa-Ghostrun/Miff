package com.tomessa.miff.ui.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.data.profile.AvatarCapture
import com.tomessa.miff.domain.profile.Initials
import com.tomessa.miff.domain.profile.PhoneNumbers
import com.tomessa.miff.domain.profile.ProfileForm
import com.tomessa.miff.ui.auth.MessageText
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.profile.CountryField
import com.tomessa.miff.ui.profile.CountryPickerSheet
import com.tomessa.miff.ui.profile.LabeledOutlinedField
import com.tomessa.miff.ui.profile.PhotoCropScreen
import com.tomessa.miff.ui.profile.PhotoSourceSheet
import com.tomessa.miff.ui.profile.ProfileAvatar
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeAccent
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14
import java.util.Locale

private val PhotoSize = 104.dp
private val PencilBadgeSize = 36.dp

/**
 * Settings > Mes informations (mockups "reglages -3" and "-14", ticket ONB-3a, criteria A5 to A8): the photo, first name,
 * last name, country and phone number, with the rules of the Onboarding steps. Differences from the mockups, each one
 * justified in the pull request: the phone field is labelled "Téléphone"; the counters show the real length; there is no
 * "3 caractères minimum" (1 to 50, decision Q6); "Enregistrer" is grey until something changed; the mention of who sees the
 * number (decision N1) is added; leaving with unsaved changes asks for a confirmation.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MyInfoScreen(
    state: MyInfoUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onFirstNameChanged: (String) -> Unit,
    onLastNameChanged: (String) -> Unit,
    onCountrySelected: (String) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onPhoneFocusLost: () -> Unit,
    onPhotoChosen: (String?) -> Unit,
    onRemovePhoto: () -> Unit,
    onCameraUnavailable: () -> Unit,
    onCropChanged: (zoom: Float, centerX: Float, centerY: Float) -> Unit,
    onCropConfirm: () -> Unit,
    onCropCancel: () -> Unit,
    onSave: () -> Unit,
) {
    var askDiscard by rememberSaveable { mutableStateOf(false) }
    val requestBack: () -> Unit = {
        when {
            state.isSaving -> Unit
            state.hasEdits -> askDiscard = true
            else -> onBack()
        }
    }
    // The system "back" and the arrow follow the same rule: nothing moves while the save is going on, and unsaved
    // changes are never dropped without asking.
    BackHandler(enabled = state.isSaving || state.hasEdits, onBack = requestBack)
    val cropping = state.crop != null

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = if (cropping) Modifier.clearAndSetSemantics {} else Modifier) {
            SettingsScaffold(title = stringResource(R.string.settings_my_info_title), onBack = requestBack) {
                when (state.load) {
                    MyInfoLoad.LOADING -> {
                        LoadingBlock()
                    }

                    MyInfoLoad.FAILED -> {
                        FailedBlock(onRetry)
                    }

                    MyInfoLoad.READY -> {
                        Form(
                            state,
                            onFirstNameChanged,
                            onLastNameChanged,
                            onCountrySelected,
                            onPhoneChanged,
                            onPhoneFocusLost,
                            onPhotoChosen,
                            onRemovePhoto,
                            onCameraUnavailable,
                            onSave,
                        )
                    }
                }
            }
        }
        state.crop?.let { crop ->
            PhotoCropScreen(crop = crop, onChanged = onCropChanged, onConfirm = onCropConfirm, onCancel = onCropCancel)
        }
    }

    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text(stringResource(R.string.settings_my_info_discard_title), style = textBodyBold16) },
            text = { Text(stringResource(R.string.settings_my_info_discard_body), style = textBodyRegular) },
            // Two buttons of the same weight: leaving is not pushed more than staying.
            confirmButton = {
                TextButton(onClick = {
                    askDiscard = false
                    onBack()
                }) { Text(stringResource(R.string.settings_my_info_discard_confirm), style = textBodyBold16) }
            },
            dismissButton = {
                TextButton(onClick = { askDiscard = false }) {
                    Text(stringResource(R.string.settings_my_info_discard_cancel), style = textBodyBold16)
                }
            },
            containerColor = colorBgSurface,
        )
    }
}

@Composable
private fun LoadingBlock() {
    val loading = stringResource(R.string.auth_loading_a11y)
    Box(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = colorActionPrimary,
            modifier =
                Modifier.semantics {
                    contentDescription = loading
                    liveRegion = LiveRegionMode.Polite
                },
        )
    }
}

@Composable
private fun FailedBlock(onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        MessageText(text = stringResource(R.string.settings_my_info_load_failed), isError = false)
        MiffSecondaryButton(text = stringResource(R.string.auth_retry_action), onClick = onRetry)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Form(
    state: MyInfoUiState,
    onFirstNameChanged: (String) -> Unit,
    onLastNameChanged: (String) -> Unit,
    onCountrySelected: (String) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onPhoneFocusLost: () -> Unit,
    onPhotoChosen: (String?) -> Unit,
    onRemovePhoto: () -> Unit,
    onCameraUnavailable: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val pickLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri -> onPhotoChosen(uri?.toString()) }
    // The address of the camera file survives the destruction of the screen while the camera application is open.
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            if (taken) onPhotoChosen(captureUri) else AvatarCapture.deleteAll(context)
        }
    val locale: Locale = LocalConfiguration.current.locales[0]
    val country = remember(state.regionCode, locale) { PhoneNumbers.country(state.regionCode, locale) }
    val example = remember(state.regionCode) { PhoneNumbers.exampleHint(state.regionCode) }
    val saveKeyboard = KeyboardActions(onDone = { if (state.canSave) onSave() })

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        PhotoEditor(state, onClick = { sheetOpen = true })
        if (state.message == MyInfoMessage.PHOTO_UNUSABLE || state.message == MyInfoMessage.CAMERA_UNAVAILABLE) {
            MessageText(
                text =
                    stringResource(
                        if (state.message == MyInfoMessage.PHOTO_UNUSABLE) {
                            R.string.profile_photo_unusable
                        } else {
                            R.string.profile_photo_camera_unavailable
                        },
                    ),
                isError = state.message == MyInfoMessage.PHOTO_UNUSABLE,
                modifier = Modifier.padding(top = Spacing.s),
            )
        }
    }
    Spacer(Modifier.size(Spacing.l))
    NameField(
        value = state.firstName,
        label = stringResource(R.string.profile_first_name_label),
        error = errorFor(state.firstNameLimitHit, state.firstNameBlank),
        onChange = onFirstNameChanged,
        contentType = ContentType.PersonFirstName,
        imeAction = ImeAction.Next,
        keyboardActions = saveKeyboard,
    )
    Spacer(Modifier.size(Spacing.m))
    NameField(
        value = state.lastName,
        label = stringResource(R.string.profile_last_name_label),
        error = errorFor(state.lastNameLimitHit, state.lastNameBlank),
        onChange = onLastNameChanged,
        contentType = ContentType.PersonLastName,
        imeAction = ImeAction.Next,
        keyboardActions = saveKeyboard,
    )
    Spacer(Modifier.size(Spacing.m))
    CountryField(country = country, onClick = { pickerOpen = true }, enabled = !state.isSaving)
    Spacer(Modifier.size(Spacing.m))
    LabeledOutlinedField(
        value = state.phoneText,
        onValueChange = onPhoneChanged,
        label = stringResource(R.string.settings_my_info_phone_label),
        errorText =
            when {
                state.phoneTypeRefused -> stringResource(R.string.profile_phone_invalid_type)
                state.phoneInvalid -> stringResource(R.string.profile_phone_invalid)
                else -> null
            },
        placeholder = example,
        prefix = country.dialCode,
        enabled = !state.isSaving,
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Phone, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = saveKeyboard,
        fieldContentType = ContentType.PhoneNumberNational,
        onBlur = onPhoneFocusLost,
    )
    Spacer(Modifier.size(Spacing.s))
    Text(text = stringResource(R.string.profile_phone_help), style = textBodyRegular14, color = colorTextDescription)
    Spacer(Modifier.size(Spacing.s))
    // Mandatory (decision N1): who sees the number, said where the number is typed, never hidden.
    Text(text = stringResource(R.string.profile_phone_visibility), style = textBodyRegular14, color = colorTextForm)
    SaveMessage(state.message)
    Spacer(Modifier.size(Spacing.l))
    MiffButton(
        text = stringResource(R.string.settings_my_info_save_action),
        onClick = onSave,
        enabled = state.canSave,
        modifier = Modifier.fillMaxWidth(),
    )

    if (sheetOpen) {
        PhotoSourceSheet(
            hasPhoto = state.avatar != null,
            onDismiss = { sheetOpen = false },
            onTakePhoto = {
                sheetOpen = false
                val uri = AvatarCapture.newCaptureUri(context)
                captureUri = uri.toString()
                try {
                    cameraLauncher.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    onCameraUnavailable()
                }
            },
            // The system Photo Picker: no permission to ask, the app only receives the photo that was chosen.
            onChoose = {
                sheetOpen = false
                pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRemove = {
                sheetOpen = false
                onRemovePhoto()
            },
        )
    }
    if (pickerOpen) {
        CountryPickerSheet(
            selectedRegion = state.regionCode,
            locale = locale,
            onSelected = {
                pickerOpen = false
                onCountrySelected(it)
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun errorFor(
    limitHit: Boolean,
    blank: Boolean,
): String? =
    when {
        limitHit -> stringResource(R.string.profile_name_too_long)
        blank -> stringResource(R.string.settings_my_info_name_required)
        else -> null
    }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NameField(
    value: String,
    label: String,
    error: String?,
    onChange: (String) -> Unit,
    contentType: ContentType,
    imeAction: ImeAction,
    keyboardActions: KeyboardActions,
) {
    LabeledOutlinedField(
        value = value,
        onValueChange = onChange,
        label = label,
        errorText = error,
        // The mockups always show the counter, and it shows the REAL number of characters (the mockup said 8/50 for 5).
        counter = "${value.length}/${ProfileForm.MAX_NAME_LENGTH}",
        counterA11y = stringResource(R.string.profile_name_counter_a11y, value.length, ProfileForm.MAX_NAME_LENGTH),
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text,
                imeAction = imeAction,
            ),
        keyboardActions = keyboardActions,
        fieldContentType = contentType,
    )
}

/** The round photo with the pencil badge of the mockup. One button for the screen reader: "Modifier la photo de profil". */
@Composable
private fun PhotoEditor(
    state: MyInfoUiState,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.settings_my_info_photo_edit_a11y)
    Box(
        modifier =
            Modifier
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        ProfileAvatar(
            jpeg = state.avatar?.jpeg,
            initials = Initials.of(state.firstName, state.lastName),
            size = PhotoSize,
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(PencilBadgeSize)
                    .background(colorBgSurface, CircleShape)
                    .border(StrokeAccent, colorActionPrimary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_edit),
                contentDescription = null,
                tint = colorActionPrimary,
                modifier = Modifier.size(Spacing.m + Spacing.xs),
            )
        }
    }
}

@Composable
private fun SaveMessage(message: MyInfoMessage?) {
    val (text, isError) =
        when (message) {
            MyInfoMessage.SAVED -> R.string.settings_my_info_saved to false
            MyInfoMessage.SYNC_PENDING -> R.string.settings_my_info_sync_pending to false
            MyInfoMessage.NETWORK -> R.string.auth_error_network to true
            MyInfoMessage.SAVE_FAILED -> R.string.profile_save_failed to true
            MyInfoMessage.TRY_LATER -> R.string.profile_save_failed_later to true
            MyInfoMessage.PHOTO_UNUSABLE, MyInfoMessage.CAMERA_UNAVAILABLE, null -> return
        }
    MessageText(text = stringResource(text), isError = isError, modifier = Modifier.padding(top = Spacing.m))
}
