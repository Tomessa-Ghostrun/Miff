package com.tomessa.miff.ui.feed

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.icu.util.ULocale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.domain.tracking.ColorsInfoDurations
import com.tomessa.miff.domain.tracking.StatusLevel
import com.tomessa.miff.domain.tracking.WholeDuration
import com.tomessa.miff.ui.settings.SettingsScaffold
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.textBodyBold14
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textDisplayLabel
import java.util.Locale

/*
 * "Les couleurs de ta Miff" (ticket COULEURS-INFO, COULEURS-1): the page that opens when the person touches the big bubble of the Feed. It explains,
 * calmly, what green, orange, red and grey mean for ONE PERSON (the ring of the photo, the strip of the card) and for the MIFF (the bubble), what
 * changes a colour, and what a colour does NOT say.
 *
 * STATIC: no reading of Firestore, no network, no event of Analytics, no state; it works in aeroplane mode and shows no personal data. The durations come from
 * StatusThresholds through [ColorsInfoDurations] and the formatter of the system (never an hour count written in a text, criterion STA9). The
 * pictures are drawn with the tokens of the Feed (the avatar of status without a glyph, the stain of the bubble): no image, no colour value written here.
 * The colour is never alone: each line says its name.
 */

/** From this font scale on, the picture of a line moves ABOVE its text instead of narrowing it. */
private const val STACKED_FONT_SCALE = 1.5f

private val PICTURE_SIZE = 40.dp

/** "2 heures" / "2 hours": a length of time written by the formatter of the system, in the language of the phone. */
internal fun formatDuration(
    millis: Long,
    locale: Locale,
): String {
    val whole = WholeDuration.of(millis)
    val unit = if (whole.unit == WholeDuration.Unit.HOUR) MeasureUnit.HOUR else MeasureUnit.MINUTE
    return MeasureFormat
        .getInstance(ULocale.forLocale(locale), MeasureFormat.FormatWidth.WIDE)
        .format(Measure(whole.amount, unit))
}

@Composable
fun ColorsInfoScreen(
    onBack: () -> Unit,
    durations: ColorsInfoDurations = ColorsInfoDurations.FromThresholds,
) {
    val locale = LocalLocale.current.platformLocale
    val orange = remember(durations, locale) { formatDuration(durations.orangeAfterMillis, locale) }
    val red = remember(durations, locale) { formatDuration(durations.redAfterMillis, locale) }
    val danger = remember(durations, locale) { formatDuration(durations.dangerGreenMillis, locale) }

    SettingsScaffold(title = stringResource(R.string.feed_colors_title), onBack = onBack) {
        Text(
            text = stringResource(R.string.feed_colors_intro),
            style = textBodyRegular14,
            color = colorTextDescription,
            modifier = Modifier.padding(bottom = Spacing.m),
        )

        // 1. The colour of a person.
        SectionTitle(R.string.feed_colors_person_title)
        SectionIntro(R.string.feed_colors_person_intro)
        PersonLine(
            StatusLevel.GREEN,
            R.string.feed_colors_person_green_title,
            stringResource(R.string.feed_colors_person_green_body, orange),
        )
        PersonLine(
            StatusLevel.ORANGE,
            R.string.feed_colors_person_orange_title,
            stringResource(R.string.feed_colors_person_orange_body, orange, red),
        )
        PersonLine(
            StatusLevel.RED,
            R.string.feed_colors_person_red_title,
            stringResource(R.string.feed_colors_person_red_body, red),
        )
        PersonLine(
            StatusLevel.NEUTRAL,
            R.string.feed_colors_person_gray_title,
            stringResource(R.string.feed_colors_person_gray_body),
        )
        PersonLine(
            StatusLevel.NEUTRAL,
            R.string.feed_colors_person_travel_title,
            stringResource(R.string.feed_colors_person_travel_body),
        )
        // The danger zone: a short paragraph of its own, it only concerns the Miffs that defined one.
        TextBlock(
            title = stringResource(R.string.feed_colors_danger_title),
            body = stringResource(R.string.feed_colors_danger_body, danger),
        )

        // 2. What changes a colour.
        SectionTitle(R.string.feed_colors_changes_title)
        PlainLine(stringResource(R.string.feed_colors_changes_arrive))
        PlainLine(stringResource(R.string.feed_colors_changes_leave))
        PlainLine(stringResource(R.string.feed_colors_changes_news))
        PlainLine(stringResource(R.string.feed_colors_changes_time, orange, red))
        PlainLine(stringResource(R.string.feed_colors_changes_pause))

        // 3. The colour of the Miff.
        SectionTitle(R.string.feed_colors_miff_title)
        SectionIntro(R.string.feed_colors_miff_intro)
        MiffLine(StatusLevel.GREEN, R.string.feed_colors_miff_green_title, R.string.feed_colors_miff_green_body)
        MiffLine(StatusLevel.ORANGE, R.string.feed_colors_miff_orange_title, R.string.feed_colors_miff_orange_body)
        MiffLine(StatusLevel.RED, R.string.feed_colors_miff_red_title, R.string.feed_colors_miff_red_body)
        MiffLine(StatusLevel.NEUTRAL, R.string.feed_colors_miff_gray_title, R.string.feed_colors_miff_gray_body)
        PlainLine(stringResource(R.string.feed_colors_miff_ignored))

        // 4. What the colours do not say.
        SectionTitle(R.string.feed_colors_not_title)
        PlainLine(stringResource(R.string.feed_colors_not_alert))
        PlainLine(stringResource(R.string.feed_colors_not_location))
        PlainLine(stringResource(R.string.feed_colors_not_verdict))
        PlainLine(stringResource(R.string.feed_colors_not_delay))
    }
}

@Composable
private fun SectionTitle(res: Int) {
    Text(
        text = stringResource(res),
        style = textDisplayLabel,
        color = colorTextForm,
        modifier = Modifier.padding(top = Spacing.l, bottom = Spacing.xs).semantics { heading() },
    )
}

@Composable
private fun SectionIntro(res: Int) {
    Text(
        text = stringResource(res),
        style = textBodyRegular14,
        color = colorTextDescription,
        modifier = Modifier.padding(bottom = Spacing.s),
    )
}

@Composable
private fun PlainLine(text: String) {
    Text(
        text = text,
        style = textBodyRegular14,
        color = colorTextForm,
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s),
    )
}

/** A bold name and its sentence, with no picture. Read as ONE block. */
@Composable
private fun TextBlock(
    title: String,
    body: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.s, bottom = Spacing.s)
                .semantics(mergeDescendants = true) { contentDescription = "$title. $body" },
    ) {
        Text(text = title, style = textBodyBold14, color = colorTextPrimary)
        Text(text = body, style = textBodyRegular14, color = colorTextForm)
    }
}

@Composable
private fun PersonLine(
    level: StatusLevel,
    titleRes: Int,
    body: String,
) {
    ColorLine(titleRes = titleRes, body = body) { PersonPicture(level) }
}

@Composable
private fun MiffLine(
    level: StatusLevel,
    titleRes: Int,
    bodyRes: Int,
) {
    ColorLine(titleRes = titleRes, body = stringResource(bodyRes)) { MiffPicture(level) }
}

/**
 * One line of colour: the picture (decorative: the screen reader skips it), the name of the colour in bold, then one or two sentences. The whole line is
 * read as ONE block ("Vert. Tout va bien. ..."). At the largest font sizes the picture goes above the text.
 */
@Composable
private fun ColorLine(
    titleRes: Int,
    body: String,
    picture: @Composable () -> Unit,
) {
    val title = stringResource(titleRes)
    val stacked = LocalDensity.current.fontScale >= STACKED_FONT_SCALE
    val text: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = title, style = textBodyBold14, color = colorTextPrimary)
            Text(text = body, style = textBodyRegular14, color = colorTextForm)
        }
    }
    val rowModifier =
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.s)
            .semantics(mergeDescendants = true) { contentDescription = "$title. $body" }
    if (stacked) {
        Column(modifier = rowModifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            picture()
            text(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = rowModifier,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.Top,
        ) {
            picture()
            text(Modifier.weight(1f))
        }
    }
}

/** The avatar of status of the Feed: a round disc and the ring of the level, no glyph (decision of the PM, 2026-11-06). */
@Composable
private fun PersonPicture(level: StatusLevel) {
    Box(modifier = Modifier.size(PICTURE_SIZE)) {
        FeedAvatar(jpeg = null, initials = "", level = level, size = PICTURE_SIZE)
    }
}

/** A small stain of the shape of the bubble of the Feed, in the tint of the level: no text, no glyph. */
@Composable
private fun MiffPicture(level: StatusLevel) {
    Box(
        modifier =
            Modifier
                .size(width = PICTURE_SIZE + Spacing.s, height = PICTURE_SIZE)
                .clip(BubbleShape)
                .background(level.tint()),
    )
}
