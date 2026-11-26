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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
import com.tomessa.miff.domain.timeshare.SliceKind
import com.tomessa.miff.domain.timeshare.TimeShareRules
import com.tomessa.miff.domain.timeshare.TimeShareTracking
import com.tomessa.miff.domain.timeshare.TrackingHealth
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.components.OverlayPresent
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
import com.tomessa.miff.ui.theme.colorChartBadge
import com.tomessa.miff.ui.theme.colorChartBadgeIcon
import com.tomessa.miff.ui.theme.colorChartDanger
import com.tomessa.miff.ui.theme.colorChartElsewhere
import com.tomessa.miff.ui.theme.colorChartOther
import com.tomessa.miff.ui.theme.colorChartSummaryBg
import com.tomessa.miff.ui.theme.colorChartSummaryTick
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
import com.tomessa.miff.ui.theme.textTitleBold20
import kotlinx.coroutines.delay
import java.text.NumberFormat

/*
 * "Répartition du temps", the card of the Profile (docs/tickets/profil-repartition-temps.md, TIME-2; mockup 19.webp of the PM, gaps listed in the PR). Layout of the PM (2026-11-17):
 * header (green badge, title, selector), the small RING on the left and the LEGEND on the right (the four biggest shares), then the green band with the
 * tick, the sentence and the party emoji. No chart before 3 days of history: the words only, without selector. No subtitle and no line about the data staying on the phone (PM).
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
    TimeShareCard(state, actions)
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
    val chart = state.body as? TimeShareBody.Chart
    CardFrame(modifier) {
        // The selector and the subtitle exist only with the chart (before 3 days of history there is nothing to choose a window for).
        Header(windowDays = state.windowDays, withChart = chart != null, onWindowChosen = actions.onWindowChosen)
        when (val body = state.body) {
            is TimeShareBody.NotEnough -> NotEnough(state, body, actions)
            is TimeShareBody.Chart -> ChartBody(body, state)
        }
    }
}

// --- Header and selector ---------------------------------------------------------------------------------------------------------------------------

@Composable
private fun Header(
    windowDays: Int,
    withChart: Boolean,
    onWindowChosen: (Int) -> Unit,
) {
    // A large font: the selector goes UNDER the title (a word is never cut in two to make room for it).
    if (!withChart) {
        HeaderTitle(Modifier.fillMaxWidth())
    } else if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            HeaderTitle(Modifier.fillMaxWidth())
            WindowSelector(windowDays, onWindowChosen)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            HeaderTitle(Modifier.weight(1f))
            WindowSelector(windowDays, onWindowChosen)
        }
    }
}

/** The badge (a green disc with the three bars, as on the mockup), the title and, with the chart, "Sur les N derniers jours". */
@Composable
private fun HeaderTitle(modifier: Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        // The badge is decorative: the title says it.
        Box(
            modifier = Modifier.size(BADGE_SIZE).clip(CircleShape).background(colorChartBadge),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_timeshare_chart),
                contentDescription = null,
                tint = colorChartBadgeIcon,
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
        }
    }
}

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
            Text(text = label, style = textBodyRegular, color = colorTextForm)
            Icon(
                painter = painterResource(R.drawable.ic_arrow_drop_down),
                contentDescription = null,
                tint = colorTextForm,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colorBgSurface) {
            OverlayPresent()
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
    val description = describe(body)
    var detailOpen by remember { mutableStateOf(false) }
    val danger = body.items.firstOrNull { it.kind == SliceKind.DANGER }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // A large font (or a narrow screen): the ring goes ABOVE the legend and the caption leaves its centre, so that nothing is cut.
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
                // The (small) ring on the LEFT, the legend on the RIGHT (the PM, after the recette of the APK; in a right-to-left language the row mirrors by itself).
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
    SummaryBand(body)
    if (detailOpen && danger != null) DangerDetailSheet(danger, onDismiss = { detailOpen = false })
}

/** The lines of the legend (see [LegendLines]): the four biggest shares, and always the danger zone when it is 1 % or more. */
private fun legendItems(items: List<LegendItem>): List<LegendItem> = LegendLines.select(items)

@Composable
private fun describe(body: TimeShareBody.Chart): String {
    // The danger line, when it is drawn in the legend, is a button that reads itself ("Zones de danger, 12 %, afficher le détail"): the sentence does not say it a second
    // time. When it is NOT among the lines (under 1 %, no button), the sentence still lists it.
    val shown = legendItems(body.items)
    val spoken = body.items.filterNot { it.kind == SliceKind.DANGER && it in shown }
    val parts = spoken.map { "${labelOf(it)} ${percentText(it.percent, it.tiny)}" }
    return stringResource(R.string.profile_timeshare_a11y_card, parts.joinToString(", "))
}

/** "Zone de danger" for one place, "Zones de danger" for several (two texts, not a plural: the number is not in the sentence). */
@Composable
private fun dangerLabel(count: Int): String =
    stringResource(
        if (count ==
            1
        ) {
            R.string.profile_timeshare_danger_zones
        } else {
            R.string.profile_timeshare_danger_zones_several
        },
    )

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
        legendItems(body.items).forEach { item ->
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
            Text(text = centerText, style = textTitleBold20, color = colorTextPrimary)
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

/**
 * The band under the chart, as the mockup of the PM: a very light green, a tick on the left, "Tu as passé 72 % de ton temps dans tes zones de confiance" (the figure in bold) and
 * the party emoji on the right. Shown whenever the chart is. It never names the danger zone.
 */
@Composable
private fun SummaryBand(body: TimeShareBody.Chart) {
    val figure = percentText(body.centerPercent, tiny = false)
    val sentence = stringResource(R.string.profile_timeshare_summary, figure)
    val text =
        remember(sentence, figure) {
            buildAnnotatedString {
                append(sentence)
                val start = sentence.indexOf(figure)
                if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + figure.length)
            }
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BandShape)
                .background(colorChartSummaryBg)
                .padding(horizontal = Spacing.m, vertical = Spacing.s + Spacing.xs)
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check_circle),
            contentDescription = null,
            tint = colorChartSummaryTick,
            modifier = Modifier.size(Spacing.xl - Spacing.xs),
        )
        Text(text = text, style = textBodyRegular14, color = colorTextForm, modifier = Modifier.weight(1f))
        // Decorative: the sentence says everything.
        Box(modifier = Modifier.clearAndSetSemantics {}) {
            Text(text = PARTY_EMOJI, fontSize = with(LocalDensity.current) { PARTY_SIZE.toSp() })
        }
    }
}

/** Under 3 days of history, or a tracking that is not running: no chart at all, only the words that are TRUE for the situation (never the colour of an alert). */
@Composable
private fun NotEnough(
    state: TimeShareUiState.Shown,
    body: TimeShareBody.NotEnough,
    actions: TimeShareActions,
) {
    val kind = notEnoughKind(state.tracking.health)
    val text =
        when (kind) {
            // "Ton graphique arrive dans X jours, nous analysons tes habitudes": the wording fixed by the PM, with its plural (1 jour / X jours).
            NotEnoughKind.COMING_SOON -> {
                // The 3 days are over but nothing was measured yet: no countdown (it would stay at "1 jour"), a calm text that says what is awaited.
                if (body.historyReached) {
                    stringResource(R.string.profile_timeshare_no_time_yet)
                } else {
                    pluralStringResource(
                        R.plurals.profile_timeshare_not_enough,
                        body.daysLeft,
                        body.daysLeft,
                    )
                }
            }

            NotEnoughKind.TRAVEL -> {
                stringResource(R.string.profile_timeshare_travel)
            }

            NotEnoughKind.SHARING_STOPPED -> {
                stringResource(R.string.profile_timeshare_sharing_stopped)
            }

            NotEnoughKind.LOCATION_NEEDED -> {
                stringResource(R.string.profile_timeshare_location_needed)
            }
        }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(text = text, style = textBodyRegular, color = colorTextForm)
        NotEnoughAction(kind, state, actions)
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
        OverlayPresent()
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

private val CardShape = RoundedCornerShape(20.dp)
private val BADGE_SIZE = 40.dp
private val RING_SIZE = 120.dp
private val EMOJI_SIZE = 18.dp
private val PARTY_SIZE = 24.dp
private val BandShape = RoundedCornerShape(16.dp)
private const val PARTY_EMOJI = "\uD83C\uDF89"
private val RING_SIZE_LARGE = 200.dp
private val DOT_SIZE = 10.dp
private val ROW_HEIGHT = 32.dp
private val HATCH_STEP = 5.dp
private val HATCH_WIDTH = 1.5.dp
private val NARROW_WIDTH = 270.dp
private const val LARGE_FONT_SCALE = 1.3f
private const val RING_RATIO = 0.2f
private const val START_ANGLE = -90f
private const val GAP_DEGREES = 3f
private const val MIN_SWEEP_DEGREES = 1f
private const val LOADING_DELAY_MILLIS = 300L
