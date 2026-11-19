package com.tomessa.miff.ui.miff

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeAccent
import com.tomessa.miff.ui.theme.StrokeHairline
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderAction
import com.tomessa.miff.ui.theme.colorBorderDefault
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.textBodyBold14
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyCaption
import com.tomessa.miff.ui.theme.textBodyRegular14

/*
 * The text of the consent, version "2026-11-v04" (MiffSharingConsent.VERSION; design screens 21 and 24 + the two lines of CONS-4-A: the place of the
 * photo and "Sur ton téléphone seulement"; docs/tickets/consentement-localisation-historique.md section 7; the version 3 text is archived in
 * docs/legal/consentement-localisation-v3.md). ONE text for the entry (create, join), the resume of the sharing and the renewal screen: a line
 * changed here is changed for all of them (and in the catalog of iOS, key `miff_consent3_*`: the prefix is historical, it does not name the
 * version). Nothing in this file reads or writes anything.
 */

/** One line of the consent: a blue icon, a bold heading, the sentence of the text. Read as ONE element, heading first, then the sentence. */
@Composable
fun ConsentLine(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes body: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colorActionPrimary,
            modifier = Modifier.size(Spacing.m + Spacing.xs),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(title), style = textBodyBold14, color = colorTextPrimary)
            Text(text = stringResource(body), style = textBodyRegular14, color = colorTextDescription)
        }
    }
}

@Composable
private fun ConsentDivider() {
    Box(Modifier.fillMaxWidth().height(StrokeHairline).background(colorBorderDefault))
}

/** The bordered card that holds the lines (the border of the consent cards of the flow: `border/action`). */
@Composable
private fun ConsentFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .background(colorBgSurface)
                .border(StrokeAccent, colorBorderAction, ShapeMd)
                .padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        content()
    }
}

/**
 * The consent shown before "Créer et partager", "Rejoindre et partager" and in the dialog "Reprendre le partage" (screen 24): the title, the
 * sentence that counts as the agreement ([sentence]), the eight lines of the text, and the note about the phone's own permission (option A of
 * the PM: no "you can refuse", Miff needs "Always"). Replaces the card of the version `2026-10-v2`. The screen scrolls (the host of the card):
 * with a large font nothing is cut and the buttons stay reachable.
 */
@Composable
fun SharingConsentCard(
    sentence: String,
    modifier: Modifier = Modifier,
) {
    ConsentFrame(modifier = modifier) {
        Text(
            text = stringResource(R.string.miff_consent3_title),
            style = textBodyBold16,
            color = colorTextPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = sentence, style = textBodyRegular14, color = colorTextPrimary)
        ConsentLine(R.drawable.ic_loc_pin, R.string.miff_consent3_kept_title, R.string.miff_consent3_kept_body)
        ConsentDivider()
        ConsentWhoSeesLines()
        Text(
            text = stringResource(R.string.miff_consent3_system_note),
            style = textBodyCaption,
            color = colorTextDescription,
        )
    }
}

/**
 * The lines shared by the entry card and the renewal screen (the "kept" line is the introduction of the renewal): the place of the photo, who
 * sees, the phone's location, what stays on the phone only (CONS-4-A: this line is never removed, it is what closes the gap J65), the erasure,
 * what the members also see, the places.
 */
@Composable
private fun ConsentWhoSeesLines() {
    ConsentLine(R.drawable.ic_update_pin, R.string.miff_consent3_photo_title, R.string.miff_consent3_photo_body)
    ConsentDivider()
    ConsentLine(R.drawable.ic_visibility, R.string.miff_consent3_who_title, R.string.miff_consent3_who_body)
    ConsentDivider()
    ConsentLine(R.drawable.ic_perm_phone, R.string.miff_consent3_closed_title, R.string.miff_consent3_closed_body)
    ConsentDivider()
    ConsentLine(R.drawable.ic_lock, R.string.miff_consent3_local_title, R.string.miff_consent3_local_body)
    ConsentDivider()
    ConsentLine(R.drawable.ic_perm_trash, R.string.miff_consent3_erase_title, R.string.miff_consent3_erase_body)
    ConsentDivider()
    ConsentLine(R.drawable.ic_group, R.string.miff_consent3_members_title, R.string.miff_consent3_members_body)
    ConsentDivider()
    ConsentLine(R.drawable.ic_perm_place, R.string.miff_consent3_places_title, R.string.miff_consent3_places_body)
}

/** The card of the renewal screen (screen 21): the same lines, without the "kept" one (the introduction says it). */
@Composable
fun RenewalDetailsCard(modifier: Modifier = Modifier) {
    ConsentFrame(modifier = modifier) { ConsentWhoSeesLines() }
}
