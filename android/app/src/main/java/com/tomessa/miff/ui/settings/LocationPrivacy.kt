package com.tomessa.miff.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomessa.miff.AppContainer
import com.tomessa.miff.R
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.textBodyBold14
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular14

/*
 * LOC-2b, design 23 (C): what Settings > Confidentialité says about the location. Reads only the STATE of the permissions and the names of the Miffs
 * that wait for the agreement; it reads no position and writes nothing.
 */

/** The state of the location for the screen: is "Toujours" + precise granted, and the names of the Miffs that wait for the agreement. */
class LocationPrivacyUi(
    val permissionComplete: Boolean = false,
    val waitingMiffNames: List<String> = emptyList(),
) {
    override fun toString(): String = "LocationPrivacyUi(redacted)"
}

/** The state of the location, read again each time the screen comes back to the front (a permission changed in the phone settings shows at once). */
@Composable
fun rememberLocationPrivacy(container: AppContainer): LocationPrivacyUi {
    val waiting by container.awaitingAgreement.waiting.collectAsStateWithLifecycle()
    val directory by container.miffDirectory.state.collectAsStateWithLifecycle()
    var complete by remember { mutableStateOf(container.devicePermissions.snapshot().locationComplete) }
    LifecycleEventEffect(
        Lifecycle.Event.ON_RESUME,
    ) { complete = container.devicePermissions.snapshot().locationComplete }
    val names =
        (directory as? DirectoryState.Ready)
            ?.miffs
            ?.filter { it.id in waiting }
            ?.map { it.name }
            .orEmpty()
    return LocationPrivacyUi(complete, names)
}

/** The block under "Localisation": the state of the permission, the true sentence, one line per Miff that waits, the help link. */
@Composable
internal fun LocationBlock(
    location: LocationPrivacyUi,
    onReviewAgreement: () -> Unit,
    onOpenPhoneSettings: () -> Unit,
    onOpenDetectionHelp: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        // Icon + words, never the colour alone.
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            if (location.permissionComplete) {
                Icon(
                    painter = painterResource(R.drawable.ic_loc_check),
                    contentDescription = null,
                    tint = colorActionPrimary,
                    modifier = Modifier.size(Spacing.m),
                )
            }
            Text(
                text =
                    stringResource(
                        if (location.permissionComplete) {
                            R.string.settings_privacy_location_status_ok
                        } else {
                            R.string.settings_privacy_location_status_todo
                        },
                    ),
                style = textBodyBold14,
                color = colorTextForm,
            )
        }
        Text(
            text = stringResource(R.string.settings_privacy_location_body),
            style = textBodyRegular14,
            color = colorTextDescription,
        )
        location.waitingMiffNames.forEach { name ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s).semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = name, style = textBodyBold16, color = colorTextForm, modifier = Modifier.weight(1f))
                    Icon(
                        painter = painterResource(R.drawable.ic_pause),
                        contentDescription = null,
                        tint = colorActionPrimary,
                        modifier = Modifier.size(Spacing.m),
                    )
                    Text(
                        text = stringResource(R.string.settings_privacy_miff_waiting_pill),
                        style = textBodyBold14,
                        color = colorActionPrimary,
                    )
                }
                Text(
                    text = stringResource(R.string.settings_privacy_miff_waiting_body),
                    style = textBodyRegular14,
                    color = colorTextDescription,
                )
            }
        }
        if (location.waitingMiffNames.isNotEmpty()) {
            MiffSecondaryButton(
                text = stringResource(R.string.miff_waiting_review),
                onClick = onReviewAgreement,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        MiffSecondaryButton(
            text = stringResource(R.string.settings_privacy_open_phone_settings),
            onClick = onOpenPhoneSettings,
            modifier = Modifier.fillMaxWidth(),
        )
        MiffSecondaryButton(
            text = stringResource(R.string.settings_privacy_detection_help_link),
            onClick = onOpenDetectionHelp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Non-blocking help for the phones that stop the apps in the background to save the battery (ColorOS on the PM's OPPO, and others): optional steps,
 * said calmly, with the way to the settings of Miff. Nothing here is required: Miff works without, with a possible delay.
 */
@Composable
fun DetectionHelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsScaffold(title = stringResource(R.string.settings_detection_help_title), onBack = onBack) {
        Text(
            text = stringResource(R.string.settings_detection_help_intro),
            style = textBodyRegular14,
            color = colorTextDescription,
            modifier = Modifier.padding(bottom = Spacing.s),
        )
        listOf(
            R.string.settings_detection_help_step_battery,
            R.string.settings_detection_help_step_background,
            R.string.settings_detection_help_step_autostart,
            R.string.settings_detection_help_step_saver,
            R.string.settings_detection_help_step_forcestop,
        ).forEach {
            Text(
                text = stringResource(it),
                style = textBodyRegular14,
                color = colorTextForm,
                modifier = Modifier.padding(bottom = Spacing.s),
            )
        }
        MiffSecondaryButton(
            text = stringResource(R.string.settings_privacy_open_phone_settings),
            onClick = { ExternalLinks.openPhoneSettings(context) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.settings_detection_help_outro),
            style = textBodyRegular14,
            color = colorTextDescription,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}
