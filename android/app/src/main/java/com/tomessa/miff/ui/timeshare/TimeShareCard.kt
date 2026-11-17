package com.tomessa.miff.ui.timeshare

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomessa.miff.R
import com.tomessa.miff.domain.permission.BackgroundRequestOutcome
import com.tomessa.miff.domain.permission.BackgroundRoute
import com.tomessa.miff.domain.permission.LocationFlow
import com.tomessa.miff.domain.place.Place
import com.tomessa.miff.domain.place.PlaceCategory
import com.tomessa.miff.domain.timeshare.PresenceLog
import com.tomessa.miff.domain.timeshare.SliceKind
import com.tomessa.miff.domain.timeshare.SummaryKind
import com.tomessa.miff.domain.timeshare.TimeShareRules
import com.tomessa.miff.domain.timeshare.TimeShareTracking
import com.tomessa.miff.domain.timeshare.TrackingHealth
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.components.miffBottomInsets
import com.tomessa.miff.ui.navigation.holdForDiagnostic
import com.tomessa.miff.ui.permission.rememberBackgroundLocationRequest
import com.tomessa.miff.ui.place.CategoryGlyph
import com.tomessa.miff.ui.settings.ExternalLinks
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeFull
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeHairline
import com.tomessa.miff.ui.theme.chartPlaceColors
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint10
import com.tomessa.miff.ui.theme.colorBgPlaceholder
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderDefault
import com.tomessa.miff.ui.theme.colorBorderStrong
import com.tomessa.miff.ui.theme.colorChartDanger
import com.tomessa.miff.ui.theme.colorChartElsewhere
import com.tomessa.miff.ui.theme.colorChartOther
import com.tomessa.miff.ui.theme.colorStatusDangerText
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold14
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyCaption
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textDisplayTitle
import com.tomessa.miff.ui.theme.textTitleBold24
import kotlinx.coroutines.delay
import java.text.NumberFormat

/*
 * "Répartition de ton temps", the card of the Profile (docs/tickets/profil-repartition-temps.md, TIME-2; mockup 19.webp of the PM, gaps listed in the PR).
 * Everything is drawn by Compose itself (a ring on a Canvas): no chart library. No colour is written here, only tokens (ui/theme/ChartColor.kt). Nothing is logged.
 *
 * Accessibility (section 9.2): the ring is decorative (hidden), the legend carries the same words as the ring (emoji + name + percentage, in the order of the slices), ONE
 * sentence describes the whole chart, the line "Zone de danger" is the only one that can be touched (it opens its detail), the selector is announced "Période, 7 jours",
 * the emoji is never read (the name is), targets are 48 dp, and with a large font the legend goes UNDER the ring and the text of the centre moves out of it (no truncation).
 */

/** What the card asks of its screen. */
class TimeShareActions(
    val onWindowChosen: (Int) -> Unit = {},
    val onRetry: () -> Unit = {},
    /** The line "Voir cette Miff" of the state "Partage arrêté": opens the screen of the Miff (where the sharing can be resumed). */
    val onOpenMiff: (String) -> Unit = {},
)

/** The card with its view model, for the Profile tab: the places already read by the Profile (no reading of its own), the clock of now at each return to the front. */
@Composable
fun TimeShareSection(
    viewModel: TimeShareViewModel,
    miffId: String?,
    places: List<Place>?,
    onOpenMiff: (String) -> Unit,
    /** Only used by the debug build (the tool that fills the journal with test data, see [TimeShareDemoTools]); the release build draws nothing with it. */
    journal: PresenceLog,
) {
    LaunchedEffect(viewModel, miffId, places) { viewModel.onInputs(miffId, places) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions =
        remember(viewModel, onOpenMiff) {
            TimeShareActions(
                onWindowChosen = viewModel::onWindowChosen,
                onRetry = viewModel::onRetry,
                onOpenMiff = onOpenMiff,
            )
        }
    // No wrapper: a layout node with nothing in it would still take its share of the spacing of the tab while the card is hidden.
    TimeShareCard(state, actions)
    if (miffId != null && !places.isNullOrEmpty()) TimeShareDemoTools.Panel(journal, miffId, places)
}

@Composable
fun TimeShareCard(
    state: TimeShareUiState,
    actions: TimeShareActions,
    modifier: Modifier = Modifier,
) {
    when (state) {
        TimeShareUiState.Hidden -> Unit
        TimeShareUiState.Loading -> LoadingCard(modifier)
        TimeShareUiState.Failed -> FailedCard(actions, modifier)
        is TimeShareUiState.Shown -> ShownCard(state, actions, modifier)
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun CardFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .miffShadow(ShadowDefault, CardShape)
                .clip(CardShape)
                .background(colorBgSurface)
                .border(StrokeHairline, colorBorderDefault, CardShape)
                .padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        content()
    }
}

/** Nothing for the first 300 ms (the reading is local and almost instant); after that, a quiet placeholder without animation. */
@Composable
private fun LoadingCard(modifier: Modifier) {
    val visible by produceState(false) {
        delay(LOADING_DELAY_MILLIS)
        value = true
    }
    if (!visible) return
    CardFrame(modifier.clearAndSetSemantics {}) {
        Box(
            Modifier
                .fillMaxWidth(
                    0.5f,
                ).heightIn(min = Spacing.l)
                .clip(ShapeMd)
                .background(colorBgPlaceholder.copy(alpha = 0.4f)),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(
                    min = RING_SIZE,
                ).clip(ShapeMd)
                .background(colorBgPlaceholder.copy(alpha = 0.25f)),
        )
    }
}

@Composable
private fun FailedCard(
    actions: TimeShareActions,
    modifier: Modifier,
) {
    CardFrame(modifier) {
        Text(
            text = stringResource(R.string.profile_timeshare_title),
            style = textBodyBold16,
            color = colorTextPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.profile_timeshare_error),
            style = textBodyRegular14,
            color = colorTextDescription,
        )
        MiffSecondaryButton(text = stringResource(R.string.auth_retry_action), onClick = actions.onRetry)
    }
}

@Composable
private fun ShownCard(
    state: TimeShareUiState.Shown,
    actions: TimeShareActions,
    modifier: Modifier,
) {
    CardFrame(modifier) {
        Header(state.windowDays, actions.onWindowChosen)
        when (val body = state.body) {
            TimeShareBody.NotEnough -> NotEnough(state, actions)
            is TimeShareBody.Chart -> ChartBody(body, state)
        }
        PrivacyLine()
    }
}

// --- Header and selector ---------------------------------------------------------------------------------------------------------------------------

@Composable
private fun Header(
    windowDays: Int,
    onWindowChosen: (Int) -> Unit,
) {
    // A large font: the selector goes UNDER the title (a word is never cut in two to make room for it).
    if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            HeaderTitle(windowDays, Modifier.fillMaxWidth())
            WindowSelector(windowDays, onWindowChosen)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            HeaderTitle(windowDays, Modifier.weight(1f))
            WindowSelector(windowDays, onWindowChosen)
        }
    }
}

@Composable
private fun HeaderTitle(
    windowDays: Int,
    modifier: Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        // The badge is decorative: the title says it.
        Box(
            modifier = Modifier.size(BADGE_SIZE).clip(CircleShape).background(colorActionPrimaryTint10),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_timeshare_chart),
                contentDescription = null,
                tint = colorActionPrimary,
                modifier = Modifier.size(Spacing.l),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.profile_timeshare_title),
                style = textBodyBold16,
                color = colorTextPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = subtitle(windowDays), style = textBodyRegular14, color = colorTextDescription)
        }
    }
}

@Composable
private fun subtitle(days: Int): String =
    stringResource(
        when (days) {
            14 -> R.string.profile_timeshare_subtitle_14
            30 -> R.string.profile_timeshare_subtitle_30
            else -> R.string.profile_timeshare_subtitle_7
        },
    )

@Composable
private fun windowLabel(days: Int): String =
    stringResource(
        when (days) {
            14 -> R.string.profile_timeshare_window_14
            30 -> R.string.profile_timeshare_window_30
            else -> R.string.profile_timeshare_window_7
        },
    )

@Composable
private fun WindowSelector(
    current: Int,
    onChosen: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val label = windowLabel(current)
    val spoken = stringResource(R.string.profile_timeshare_window_a11y, label)
    Box {
        Row(
            modifier =
                Modifier
                    .heightIn(min = Spacing.minTouchTarget)
                    .clip(ShapeFull)
                    .border(StrokeHairline, colorBorderStrong, ShapeFull)
                    .clickable(role = Role.Button) { open = true }
                    .padding(start = Spacing.m, end = Spacing.s)
                    .semantics(mergeDescendants = true) { contentDescription = spoken },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = textBodyBold14, color = colorTextForm)
            Icon(
                painter = painterResource(R.drawable.ic_arrow_drop_down),
                contentDescription = null,
                tint = colorTextForm,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colorBgSurface) {
            TimeShareRules.WINDOWS_DAYS.forEach { days ->
                val chosen = days == current
                DropdownMenuItem(
                    text = { Text(text = windowLabel(days), style = textBodyRegular, color = colorTextForm) },
                    trailingIcon = {
                        if (chosen) {
                            Icon(
                                painter = painterResource(R.drawable.ic_loc_check),
                                contentDescription = stringResource(R.string.profile_timeshare_window_selected_a11y),
                                tint = colorActionPrimary,
                            )
                        }
                    },
                    onClick = {
                        open = false
                        onChosen(days)
                    },
                    modifier = Modifier.semantics { selected = chosen },
                )
            }
        }
    }
}

// --- Nominal chart ---------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun ChartBody(
    body: TimeShareBody.Chart,
    state: TimeShareUiState.Shown,
) {
    val description = describe(body, state.windowDays)
    var detailOpen by remember { mutableStateOf(false) }
    val danger = body.items.firstOrNull { it.kind == SliceKind.DANGER }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // A large font (or a narrow screen): the legend goes UNDER the ring and the caption leaves its centre, so that nothing is cut.
        val stacked = LocalDensity.current.fontScale >= LARGE_FONT_SCALE || maxWidth < NARROW_WIDTH
        Column(
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (stacked) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    Donut(body, RING_SIZE_LARGE, centerCaption = false)
                    // Like the ring it belongs to, it is drawn and not read (the sentence of the chart says it).
                    Box(modifier = Modifier.clearAndSetSemantics {}) {
                        Text(
                            text = stringResource(R.string.profile_timeshare_center_caption),
                            style = textBodyRegular14,
                            color = colorTextDescription,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Legend(body, onOpenDanger = { detailOpen = true }, modifier = Modifier.fillMaxWidth())
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    Donut(body, RING_SIZE, centerCaption = true)
                    Legend(body, onOpenDanger = { detailOpen = true }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    if (state.tracking.health != TrackingHealth.ACTIVE) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) {},
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_pause),
                contentDescription = null,
                tint = colorTextDescription,
                modifier = Modifier.size(Spacing.m),
            )
            Text(
                text = stringResource(R.string.profile_timeshare_paused),
                style = textBodyRegular14,
                color = colorTextDescription,
            )
        }
    }
    if (state.tracking.overLimit) {
        Text(
            text = stringResource(R.string.profile_timeshare_over_limit),
            style = textBodyRegular14,
            color = colorTextDescription,
        )
    }
    SummaryBand(body, state.windowDays)
    if (detailOpen && danger != null) DangerDetailSheet(danger, onDismiss = { detailOpen = false })
}

@Composable
private fun describe(
    body: TimeShareBody.Chart,
    windowDays: Int,
): String {
    val parts = body.items.map { "${labelOf(it)} ${percentText(it.percent, it.tiny)}" }
    return stringResource(R.string.profile_timeshare_a11y_card, subtitle(windowDays), parts.joinToString(", "))
}

/** "Zone de danger" for one place, "Zones de danger" for several (two texts, not a plural: the number is not in the sentence). */
@Composable
private fun dangerLabel(count: Int): String =
    stringResource(if (count == 1) R.string.profile_timeshare_danger_zone else R.string.profile_timeshare_danger_zones)

@Composable
private fun labelOf(item: LegendItem): String =
    when (item.kind) {
        SliceKind.PLACE -> item.name.orEmpty()
        SliceKind.OTHER_PLACES -> pluralStringResource(R.plurals.profile_timeshare_other_places, item.count, item.count)
        SliceKind.DANGER -> dangerLabel(item.count)
        SliceKind.ELSEWHERE -> stringResource(R.string.profile_timeshare_elsewhere)
    }

/** A whole percentage as the language of the phone writes it ("72 %" in French with a non-breaking space, "72%" in English); a share under 1 % is "< 1 %". */
@Composable
private fun percentText(
    percent: Int,
    tiny: Boolean,
): String {
    if (tiny) return stringResource(R.string.profile_timeshare_percent_tiny)
    val locale = LocalConfiguration.current.locales[0]
    return remember(percent, locale) { NumberFormat.getPercentInstance(locale).format(percent / 100.0) }
}

@Composable
private fun Legend(
    body: TimeShareBody.Chart,
    onOpenDanger: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        body.items.forEach { item ->
            if (item.kind == SliceKind.DANGER) {
                DangerRow(item, onOpenDanger)
            } else {
                // The sentence of the chart already says it: a row only draws (no second reading of the same words).
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT).clearAndSetSemantics {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    RowContent(item)
                }
            }
        }
    }
}

@Composable
private fun RowScope.RowContent(item: LegendItem) {
    Box(modifier = Modifier.size(DOT_SIZE).clip(CircleShape).background(colorOf(item)))
    RowIcon(item)
    Text(text = labelOf(item), style = textBodyRegular, color = colorTextForm, modifier = Modifier.weight(1f))
    Text(text = percentText(item.percent, item.tiny), style = textBodyBold16, color = colorTextForm)
}

@Composable
private fun RowIcon(item: LegendItem) {
    Box(modifier = Modifier.size(Spacing.l), contentAlignment = Alignment.Center) {
        when (item.kind) {
            SliceKind.PLACE -> CategoryMark(item.category)
            SliceKind.OTHER_PLACES -> Mark(R.drawable.ic_timeshare_others, colorTextDescription)
            SliceKind.DANGER -> Mark(R.drawable.ic_loc_warning, colorStatusDangerText)
            SliceKind.ELSEWHERE -> Mark(R.drawable.ic_timeshare_elsewhere, colorTextDescription)
        }
    }
}

@Composable
private fun Mark(
    drawable: Int,
    tint: Color,
) = Icon(
    painter = painterResource(drawable),
    contentDescription = null,
    tint = tint,
    modifier = Modifier.size(Spacing.m + Spacing.xs),
)

/** The emoji of the category, THE SAME glyph and the same fallback as in the list of places (criterion TS-19); the neutral pin when the place has no category. */
@Composable
private fun CategoryMark(category: PlaceCategory?) {
    if (category == null) {
        Mark(R.drawable.ic_loc_pin, colorActionPrimary)
    } else {
        CategoryGlyph(
            category = category,
            // Like the list of places: the emoji keeps the size of its box whatever the font size of the phone (the words around it grow, it does not get cut).
            fontSize = with(LocalDensity.current) { EMOJI_SIZE.toSp() },
            fallbackStyle = textBodyBold14,
            fallbackColor = colorActionPrimary,
        )
    }
}

@Composable
private fun DangerRow(
    item: LegendItem,
    onOpen: () -> Unit,
) {
    val label = labelOf(item)
    val spoken = stringResource(R.string.profile_timeshare_a11y_danger_row, label, percentText(item.percent, item.tiny))
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget)
                .clip(ShapeMd)
                .clickable(role = Role.Button, onClick = onOpen)
                .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        RowContent(item)
    }
}

private fun colorOf(item: LegendItem): Color =
    when (item.kind) {
        SliceKind.PLACE -> chartPlaceColors[item.rank.coerceIn(0, chartPlaceColors.lastIndex)]
        SliceKind.OTHER_PLACES -> colorChartOther
        SliceKind.DANGER -> colorChartDanger
        SliceKind.ELSEWHERE -> colorChartElsewhere
    }

// --- The ring --------------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun Donut(
    body: TimeShareBody.Chart,
    size: Dp,
    centerCaption: Boolean,
) {
    val locale = LocalConfiguration.current.locales[0]
    val centerText =
        remember(body.centerPercent, locale) {
            NumberFormat.getPercentInstance(locale).format(body.centerPercent / 100.0)
        }
    val colors = body.items.map(::colorOf)
    // A decoration: hidden from the screen reader (the sentence of the chart and the legend say everything).
    Box(modifier = Modifier.size(size).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = this.size.minDimension * RING_RATIO
            var angle = START_ANGLE
            val gap = if (body.items.size > 1) GAP_DEGREES else 0f
            body.items.forEachIndexed { index, item ->
                val full = item.share * 360f
                val sweep = (full - gap).coerceAtLeast(MIN_SWEEP_DEGREES)
                val start = angle + (full - sweep) / 2f
                drawSlice(colors[index], start, sweep, stroke)
                if (item.kind == SliceKind.DANGER) drawHatch(start, sweep, stroke, colorStatusDangerText)
                angle += full
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = Spacing.s),
        ) {
            Text(text = centerText, style = textTitleBold24, color = colorTextPrimary)
            if (centerCaption) {
                Text(
                    text = stringResource(R.string.profile_timeshare_center_caption),
                    style = textBodyCaption,
                    color = colorTextDescription,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun DrawScope.drawSlice(
    color: Color,
    start: Float,
    sweep: Float,
    stroke: Float,
) {
    drawArc(
        color = color,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        style = Stroke(width = stroke),
    )
}

/** The danger zone is never told by its colour alone: diagonal hatching over its slice (section 9.2). */
private fun DrawScope.drawHatch(
    start: Float,
    sweep: Float,
    stroke: Float,
    color: Color,
) {
    val outer = Rect(Offset.Zero, size)
    val inner = Rect(Offset(stroke, stroke), Size(size.width - 2 * stroke, size.height - 2 * stroke))
    val path =
        Path().apply {
            arcTo(outer, start, sweep, true)
            arcTo(inner, start + sweep, -sweep, false)
            close()
        }
    val step = HATCH_STEP.toPx()
    val width = HATCH_WIDTH.toPx()
    clipPath(path) {
        var x = -size.height
        while (x < size.width) {
            drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = width)
            x += step
        }
    }
}

// --- Sentence, privacy, states -----------------------------------------------------------------------------------------------------------------------

@Composable
private fun SummaryBand(
    body: TimeShareBody.Chart,
    windowDays: Int,
) {
    val text =
        when (body.summary) {
            SummaryKind.PLACE -> {
                val name = body.summaryPlaceName
                if (name != null) {
                    stringResource(summaryPlaceRes(windowDays), name)
                } else {
                    stringResource(summaryGenericRes(windowDays))
                }
            }

            SummaryKind.ELSEWHERE -> {
                stringResource(summaryElsewhereRes(windowDays))
            }

            SummaryKind.GENERIC -> {
                stringResource(summaryGenericRes(windowDays))
            }
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .background(colorActionPrimaryTint10)
                .padding(Spacing.m)
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_loc_info),
            contentDescription = null,
            tint = colorActionPrimary,
            modifier = Modifier.size(Spacing.l),
        )
        Text(text = text, style = textBodyRegular14, color = colorTextForm, modifier = Modifier.weight(1f))
    }
}

private fun summaryPlaceRes(days: Int): Int =
    when (days) {
        14 -> R.string.profile_timeshare_summary_place_14
        30 -> R.string.profile_timeshare_summary_place_30
        else -> R.string.profile_timeshare_summary_place_7
    }

private fun summaryElsewhereRes(days: Int): Int =
    when (days) {
        14 -> R.string.profile_timeshare_summary_elsewhere_14
        30 -> R.string.profile_timeshare_summary_elsewhere_30
        else -> R.string.profile_timeshare_summary_elsewhere_7
    }

private fun summaryGenericRes(days: Int): Int =
    when (days) {
        14 -> R.string.profile_timeshare_summary_generic_14
        30 -> R.string.profile_timeshare_summary_generic_30
        else -> R.string.profile_timeshare_summary_generic_7
    }

@Composable
private fun PrivacyLine() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lock),
            contentDescription = null,
            tint = colorTextDescription,
            modifier = Modifier.size(Spacing.m),
        )
        Text(
            text = stringResource(R.string.profile_timeshare_privacy),
            style = textBodyCaption,
            color = colorTextDescription,
        )
    }
}

/** Less than 24 h measured, or a tracking that is not running: a neutral empty ring and the words that are TRUE for the situation (never the colour of an alert). */
@Composable
private fun NotEnough(
    state: TimeShareUiState.Shown,
    actions: TimeShareActions,
) {
    val kind = notEnoughKind(state.tracking.health)
    val text =
        stringResource(
            when (kind) {
                NotEnoughKind.COMING_SOON -> R.string.profile_timeshare_not_enough
                NotEnoughKind.TRAVEL -> R.string.profile_timeshare_travel
                NotEnoughKind.SHARING_STOPPED -> R.string.profile_timeshare_sharing_stopped
                NotEnoughKind.LOCATION_NEEDED -> R.string.profile_timeshare_location_needed
            },
        )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stacked = LocalDensity.current.fontScale >= LARGE_FONT_SCALE || maxWidth < NARROW_WIDTH
        val ring: @Composable () -> Unit = {
            Canvas(
                modifier = Modifier.size(if (stacked) RING_SIZE_EMPTY_STACKED else RING_SIZE).clearAndSetSemantics {},
            ) {
                val stroke = this.size.minDimension * RING_RATIO
                drawSlice(colorBgPlaceholder.copy(alpha = 0.5f), START_ANGLE, 360f, stroke)
            }
        }
        val words: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(text = text, style = textBodyRegular, color = colorTextForm)
                NotEnoughAction(kind, state, actions)
            }
        }
        if (stacked) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                ring()
                words(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                ring()
                words(Modifier.weight(1f))
            }
        }
    }
}

/** The one action a state can have: the step of permission that already exists ("Toujours autoriser", never insisted on), or the screen of the Miff. */
@Composable
private fun NotEnoughAction(
    kind: NotEnoughKind,
    state: TimeShareUiState.Shown,
    actions: TimeShareActions,
) {
    when (kind) {
        NotEnoughKind.LOCATION_NEEDED -> {
            LocationAction(state.tracking)
        }

        NotEnoughKind.SHARING_STOPPED -> {
            MiffSecondaryButton(
                text = stringResource(R.string.profile_timeshare_sharing_stopped_action),
                onClick = { actions.onOpenMiff(state.miffId) },
            )
        }

        NotEnoughKind.COMING_SOON, NotEnoughKind.TRAVEL -> {
            Unit
        }
    }
}

@Composable
private fun LocationAction(tracking: TimeShareTracking) {
    val context = LocalContext.current
    // Same step as Settings > Confidentialité: the system asks for "all the time" when it can, the settings of the app otherwise.
    var systemScreenUnavailable by remember { mutableStateOf(false) }
    val requestBackground =
        rememberBackgroundLocationRequest { outcome ->
            if (outcome == BackgroundRequestOutcome.NO_UI) {
                systemScreenUnavailable = true
                ExternalLinks.openPhoneSettings(context)
            }
        }
    MiffSecondaryButton(
        text = stringResource(R.string.permission_location_action),
        onClick = {
            when (LocationFlow.backgroundRoute(tracking.backgroundRequestable, systemScreenUnavailable)) {
                BackgroundRoute.REQUEST_FROM_SYSTEM -> requestBackground()
                BackgroundRoute.OPEN_APP_SETTINGS -> ExternalLinks.openPhoneSettings(context)
            }
        },
    )
}

// --- Detail of the danger zone ---------------------------------------------------------------------------------------------------------------------

/**
 * The places under the line "Zone de danger", one per line (emoji, name, percentage), whose percentages add up to the line. A sheet of the bottom of the screen: the
 * "close" button and the back gesture of the system both close it. Nothing is said about WHY a place is a danger zone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DangerDetailSheet(
    item: LegendItem,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorBgSurface,
        modifier = Modifier.holdForDiagnostic(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .miffBottomInsets()
                    .padding(horizontal = Spacing.l)
                    .padding(bottom = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier.semantics(mergeDescendants = true) { heading() },
            ) {
                Mark(R.drawable.ic_loc_warning, colorStatusDangerText)
                Text(
                    text = dangerLabel(item.count),
                    style = textDisplayTitle,
                    color = colorTextForm,
                )
            }
            item.details.forEach { detail ->
                val shown = percentText(detail.percent, detail.tiny)
                val spoken = stringResource(R.string.profile_timeshare_a11y_row, detail.name, shown)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Spacing.minTouchTarget)
                            .semantics(mergeDescendants = true) { contentDescription = spoken },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    Box(
                        modifier = Modifier.size(Spacing.l),
                        contentAlignment = Alignment.Center,
                    ) { CategoryMark(detail.category) }
                    Text(
                        text = detail.name,
                        style = textBodyRegular,
                        color = colorTextForm,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = shown, style = textBodyBold16, color = colorTextForm)
                }
            }
            MiffSecondaryButton(
                text = stringResource(R.string.profile_timeshare_detail_close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val CardShape = ShapeMd
private val BADGE_SIZE = 40.dp
private val RING_SIZE = 136.dp
private val EMOJI_SIZE = 18.dp
private val RING_SIZE_LARGE = 200.dp
private val RING_SIZE_EMPTY_STACKED = 160.dp
private val DOT_SIZE = 10.dp
private val ROW_HEIGHT = 32.dp
private val HATCH_STEP = 5.dp
private val HATCH_WIDTH = 1.5.dp
private val NARROW_WIDTH = 300.dp
private const val LARGE_FONT_SCALE = 1.3f
private const val RING_RATIO = 0.16f
private const val START_ANGLE = -90f
private const val GAP_DEGREES = 3f
private const val MIN_SWEEP_DEGREES = 1f
private const val LOADING_DELAY_MILLIS = 300L
