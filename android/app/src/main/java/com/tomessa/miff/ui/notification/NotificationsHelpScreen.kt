package com.tomessa.miff.ui.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.tomessa.miff.R
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.settings.SettingsScaffold
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.colorTextOnAction
import com.tomessa.miff.ui.theme.textBodyBold14
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14

/**
 * "Notifications coupées" (ticket 8.4): the calm help screen. It says that cutting them was a choice that counts (never a reproach), lists the three
 * steps in the settings of the phone, and offers ONE button that opens them. Nothing is promised that the phone would not do, and the person is
 * reminded that giving news from the app always works.
 */
@Composable
fun NotificationsHelpScreen(
    onBack: () -> Unit,
    onOpenPhoneSettings: () -> Unit,
) {
    SettingsScaffold(title = stringResource(R.string.notif_help_title), onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(text = stringResource(R.string.notif_help_intro), style = textBodyBold16, color = colorTextForm)
            Text(
                text = stringResource(R.string.notif_help_choice),
                style = textBodyRegular,
                color = colorTextDescription,
            )
            Text(
                text = stringResource(R.string.notif_help_lead),
                style = textBodyRegular,
                color = colorTextForm,
                modifier = Modifier.padding(top = Spacing.s),
            )
            HelpStep(1, stringResource(R.string.notif_help_step_1))
            HelpStep(2, stringResource(R.string.notif_help_step_2))
            HelpStep(3, stringResource(R.string.notif_help_step_3))
            MiffButton(
                text = stringResource(R.string.notif_state_open_settings),
                onClick = onOpenPhoneSettings,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
            )
            Text(
                text = stringResource(R.string.notif_help_footer),
                style = textBodyRegular14,
                color = colorTextDescription,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
            )
        }
    }
}

/** A numbered step: the number is in a disc AND spoken as part of the sentence, in one element. */
@Composable
private fun HelpStep(
    number: Int,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(Spacing.l).clip(CircleShape).background(colorActionPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = number.toString(), style = textBodyBold14, color = colorTextOnAction)
        }
        Text(text = text, style = textBodyRegular, color = colorTextForm, modifier = Modifier.weight(1f))
    }
}
