package com.tomessa.miff.ui.profile

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.data.onboarding.DraftStep
import com.tomessa.miff.data.profile.AvatarCapture
import com.tomessa.miff.domain.profile.PhoneNumbers
import com.tomessa.miff.domain.profile.ProfileForm
import com.tomessa.miff.ui.auth.MessageText
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textTitleBold20
import java.util.Locale

private val PhotoCircleSize = 144.dp

/** The activity behind the themed contexts of Compose (null in a preview). */
internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Profile step (mockup "Welcoming screen -1", ticket 6.3): the round photo (facultative), first name, last name, and the
 * round arrow. NO back arrow and no way out here (decisions Q14 and Authentification Q11): the system "back" puts the app
 * in the background, it never signs out nor goes back to the sign-up. Nothing is written by this step.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ProfileStepScreen(
    state: ProfileUiState,
    onFirstNameChanged: (String) -> Unit,
    onLastNameChanged: (String) -> Unit,
    onContinue: () -> Unit,
    onPhotoChosen: (String?) -> Unit,
    onRemovePhoto: () -> Unit,
    onCameraUnavailable: () -> Unit,
) {
    val context = LocalContext.current
    // The back gesture must not leave the app on a screen that has no previous one: the app goes to the background.
    BackHandler { context.findActivity()?.moveTaskToBack(true) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
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
    val keyboardActions = KeyboardActions(onDone = { if (state.canContinueFromProfile) onContinue() })

    ProfileScaffold(
        paneTitle = stringResource(R.string.profile_title),
        title = stringResource(R.string.profile_title),
        onBack = null,
        nextButton = {
            RoundNextButton(
                enabled = state.canContinueFromProfile,
                isLoading = false,
                onClick = onContinue,
            )
        },
    ) {
        PhotoButton(state, onClick = { sheetOpen = true })
        if (state.message == ProfileMessage.PHOTO_UNUSABLE || state.message == ProfileMessage.CAMERA_UNAVAILABLE) {
            MessageText(
                text =
                    stringResource(
                        if (state.message == ProfileMessage.PHOTO_UNUSABLE) {
                            R.string.profile_photo_unusable
                        } else {
                            R.string.profile_photo_camera_unavailable
                        },
                    ),
                isError = state.message == ProfileMessage.PHOTO_UNUSABLE,
                modifier = Modifier.padding(top = Spacing.s),
            )
        }
        Spacer(Modifier.size(Spacing.l))
        NameField(
            value = state.firstName,
            label = stringResource(R.string.profile_first_name_label),
            limitHit = state.firstNameLimitHit,
            onChange = onFirstNameChanged,
            contentType = ContentType.PersonFirstName,
            imeAction = ImeAction.Next,
            keyboardActions = keyboardActions,
        )
        Spacer(Modifier.size(Spacing.m))
        NameField(
            value = state.lastName,
            label = stringResource(R.string.profile_last_name_label),
            limitHit = state.lastNameLimitHit,
            onChange = onLastNameChanged,
            contentType = ContentType.PersonLastName,
            imeAction = ImeAction.Done,
            keyboardActions = keyboardActions,
        )
    }

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
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NameField(
    value: String,
    label: String,
    limitHit: Boolean,
    onChange: (String) -> Unit,
    contentType: ContentType,
    imeAction: ImeAction,
    keyboardActions: KeyboardActions,
) {
    LabeledOutlinedField(
        value = value,
        onValueChange = onChange,
        label = label,
        errorText = if (limitHit) stringResource(R.string.profile_name_too_long) else null,
        counter = if (ProfileForm.showCounter(value.length)) "${value.length}/${ProfileForm.MAX_NAME_LENGTH}" else null,
        counterA11y =
            if (ProfileForm.showCounter(value.length)) {
                stringResource(R.string.profile_name_counter_a11y, value.length, ProfileForm.MAX_NAME_LENGTH)
            } else {
                null
            },
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

/**
 * The round photo of the mockup (white disc, lime camera) with, under it, what it is for: "Ajouter une photo (facultatif)"
 * and "Visible par les membres de tes Miffs" (criterion PR6). Disc and captions are ONE button for the screen reader.
 */
@Composable
private fun PhotoButton(
    state: ProfileUiState,
    onClick: () -> Unit,
) {
    val avatarBitmap =
        remember(state.avatar) {
            state.avatar?.jpeg?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Spacing.m))
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {}
                .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(PhotoCircleSize)
                    .miffShadow(ShadowDefault, CircleShape)
                    .clip(CircleShape)
                    .background(colorBgSurface),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap,
                    contentDescription = stringResource(R.string.profile_photo_image_a11y),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_avatar_camera),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
        Spacer(Modifier.size(Spacing.s))
        Text(
            text =
                stringResource(
                    if (state.avatar ==
                        null
                    ) {
                        R.string.profile_photo_add
                    } else {
                        R.string.profile_photo_change
                    },
                ),
            style = textBodyBold16,
            color = colorTextForm,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.profile_photo_visibility),
            style = textBodyRegular14,
            color = colorTextDescription,
            textAlign = TextAlign.Center,
        )
    }
}

/** "Prendre une photo" / "Choisir dans la galerie" / "Supprimer la photo" (not drawn in the mockups: Material bottom sheet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoSourceSheet(
    hasPhoto: Boolean,
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colorBgSurface,
    ) {
        Column(
            modifier = Modifier.navigationBarsPadding().padding(horizontal = Spacing.l, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.profile_photo_sheet_title),
                style = textTitleBold20,
                color = colorTextForm,
                modifier = Modifier.padding(bottom = Spacing.s).semantics { heading() },
            )
            SheetRow(stringResource(R.string.profile_photo_take), onTakePhoto)
            SheetRow(stringResource(R.string.profile_photo_choose), onChoose)
            if (hasPhoto) SheetRow(stringResource(R.string.profile_photo_remove), onRemove)
            Spacer(Modifier.size(Spacing.m))
        }
    }
}

@Composable
private fun SheetRow(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = textBodyRegular,
        color = colorTextForm,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget + Spacing.s)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = Spacing.m),
    )
}

/**
 * Phone step (mockups "-2" and "-6", ticket 6.4): country selector, number with the "+33" prefix and an example, the
 * kind help "without the first zero", the MANDATORY mention of who sees the number, the calm error, and the round arrow
 * (grey until a digit is typed). [onBack] is the arrow of the header (null when this step stands alone, row 2b).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PhoneStepScreen(
    state: ProfileUiState,
    onBack: (() -> Unit)?,
    onCountrySelected: (String) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val context = LocalContext.current
    if (onBack == null) {
        // Alone (older account): there is nothing to go back to, "back" puts the app in the background.
        BackHandler { context.findActivity()?.moveTaskToBack(true) }
    }
    val locale: Locale = LocalConfiguration.current.locales[0]
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val country = remember(state.regionCode, locale) { PhoneNumbers.country(state.regionCode, locale) }
    val example = remember(state.regionCode) { PhoneNumbers.exampleHint(state.regionCode) }

    ProfileScaffold(
        paneTitle = stringResource(R.string.profile_phone_title),
        title = stringResource(R.string.profile_phone_title),
        onBack = onBack,
        nextButton = {
            RoundNextButton(
                enabled = state.canSubmitPhone,
                isLoading = state.isWriting,
                onClick = onSubmit,
            )
        },
    ) {
        CountryField(country = country, onClick = { pickerOpen = true }, enabled = !state.isWriting)
        Spacer(Modifier.size(Spacing.m))
        LabeledOutlinedField(
            value = state.phoneText,
            onValueChange = onPhoneChanged,
            label = stringResource(R.string.profile_phone_number_label),
            errorText = if (state.phoneInvalid) stringResource(R.string.profile_phone_invalid) else null,
            placeholder = example,
            prefix = country.dialCode,
            enabled = !state.isWriting,
            // Numeric keypad, no spelling correction; the system may propose the number of the phone (autofill),
            // which the app never reads by itself.
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { if (state.canSubmitPhone) onSubmit() }),
            fieldContentType = ContentType.PhoneNumberNational,
        )
        Spacer(Modifier.size(Spacing.s))
        Text(
            text = stringResource(R.string.profile_phone_help),
            style = textBodyRegular14,
            color = colorTextDescription,
        )
        Spacer(Modifier.size(Spacing.s))
        // Mandatory (decision N1): who sees the number, said where the number is typed, never hidden.
        Text(
            text = stringResource(R.string.profile_phone_visibility),
            style = textBodyRegular14,
            color = colorTextForm,
        )
        state.message?.let { message ->
            val text =
                when (message) {
                    ProfileMessage.NETWORK -> R.string.auth_error_network
                    else -> R.string.profile_save_failed
                }
            Spacer(Modifier.size(Spacing.m))
            MessageText(text = stringResource(text), isError = true)
        }
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

/** Which of the two steps is shown for the person; used by the host and the tests. */
fun ProfileUiState.isPhoneStep(): Boolean = step == DraftStep.PHONE
