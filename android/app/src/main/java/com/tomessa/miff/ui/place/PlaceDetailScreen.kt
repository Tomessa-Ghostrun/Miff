package com.tomessa.miff.ui.place

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.domain.place.Place
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.components.RecentsPrivacy
import com.tomessa.miff.ui.components.miffScreenInsets
import com.tomessa.miff.ui.miff.MiffNotice
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeHairline
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint20
import com.tomessa.miff.ui.theme.colorActionPrimaryTint5
import com.tomessa.miff.ui.theme.colorBgCanvas
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderAction
import com.tomessa.miff.ui.theme.colorBorderDefault
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textDisplayTitle

/**
 * The detail of a place (design 16): the map with the circle, the address, "Voir sur la carte", the facts (radius, type, category,
 * who sees it, who made it) and a sentence that says what the place changes for the person. Everyone of the Miff reads it; only the
 * creator has "Modifier". The names and the address are hidden from the list of recent apps (Android 13 and later).
 */
@Composable
fun PlaceDetailScreen(
    state: PlaceDetailState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RecentsPrivacy()
    val title = state.place?.name ?: stringResource(R.string.place_detail_title_loading)
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colorBgCanvas)
                .miffScreenInsets()
                .semantics { paneTitle = title },
    ) {
        DetailHeader(
            title = title,
            canEdit = state.status == PlaceDetailStatus.READY && state.canEdit,
            onBack = onBack,
            onEdit = onEdit,
        )
        when (state.status) {
            PlaceDetailStatus.LOADING, PlaceDetailStatus.GONE, PlaceDetailStatus.ACCESS_LOST -> {
                // GONE and ACCESS_LOST are dealt with by the host (a short message and back / the single message of a lost access).
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val loading = stringResource(R.string.auth_loading_a11y)
                    CircularProgressIndicator(
                        color = colorActionPrimary,
                        modifier = Modifier.semantics { contentDescription = loading },
                    )
                }
            }

            PlaceDetailStatus.UNAVAILABLE -> {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.l),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    MiffNotice(stringResource(R.string.miff_unavailable))
                    MiffSecondaryButton(
                        text = stringResource(R.string.auth_retry_action),
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            PlaceDetailStatus.READY -> {
                state.place?.let { Content(state, it, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DetailHeader(
    title: String,
    canEdit: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget + Spacing.s)
                .padding(horizontal = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(Spacing.minTouchTarget)) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.auth_back_a11y),
                tint = colorTextForm,
            )
        }
        Text(
            text = title,
            style = textDisplayTitle,
            color = colorTextForm,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.s).semantics { heading() },
        )
        if (canEdit) {
            androidx.compose.material3.TextButton(
                onClick = onEdit,
                modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
            ) {
                Text(stringResource(R.string.place_detail_edit), style = textBodyBold16, color = colorActionPrimary)
            }
        }
    }
}

@Composable
private fun Content(
    state: PlaceDetailState,
    place: Place,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val noMaps = stringResource(R.string.place_detail_no_map_app)
    val address = place.address.orEmpty()
    val danger = place.type == PlaceType.RISK
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(
                    rememberScrollState(),
                ).padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        if (state.fromCache) MiffNotice(stringResource(R.string.place_detail_cache_notice))
        PlaceMapBox(
            spec =
                PlaceMapSpec(
                    lat = place.lat,
                    lng = place.lng,
                    radiusM = place.radiusM,
                    danger = danger,
                    description = stringResource(R.string.place_map_a11y, place.radiusM, address),
                ),
            height = MAP_HEIGHT,
        )
        if (address.isNotEmpty()) {
            val (first, rest) = splitAddress(address)
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBox(R.drawable.ic_loc_pin)
                Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Text(text = first, style = textBodyRegular, color = colorTextPrimary)
                    if (rest.isNotEmpty()) Text(text = rest, style = textBodyRegular14, color = colorTextDescription)
                }
            }
        }
        OutlinedButton(
            onClick = {
                // The map app opens on the PLACE (its coordinates), never on the position of a person.
                val uri = Uri.parse("geo:${place.lat},${place.lng}?q=${place.lat},${place.lng}")
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, noMaps, Toast.LENGTH_LONG).show()
                }
            },
            shape = ShapeMd,
            border =
                androidx.compose.foundation.BorderStroke(
                    com.tomessa.miff.ui.theme.StrokeAccent,
                    colorActionPrimary,
                ),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    containerColor = colorBgSurface,
                    contentColor = colorActionPrimary,
                ),
            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget),
        ) {
            Text(stringResource(R.string.place_detail_view_map), style = textBodyBold16)
            Icon(
                painter = painterResource(R.drawable.ic_loc_open),
                contentDescription = null,
                modifier = Modifier.padding(start = Spacing.s).size(Spacing.m + Spacing.xs),
            )
        }
        Facts(state, place)
        InfoCard(stringResource(if (danger) R.string.place_detail_info_danger else R.string.place_detail_info_trusted))
    }
}

private val MAP_HEIGHT = 210.dp

private fun splitAddress(address: String): Pair<String, String> {
    val cut = address.indexOf(',')
    return if (cut < 0) address to "" else address.substring(0, cut).trim() to address.substring(cut + 1).trim()
}

@Composable
private fun Facts(
    state: PlaceDetailState,
    place: Place,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .miffShadow(ShadowDefault, ShapeMd)
                .clip(ShapeMd)
                .background(colorBgSurface)
                .border(StrokeHairline, colorBorderDefault, ShapeMd),
    ) {
        Fact(
            R.drawable.ic_loc_radius,
            stringResource(R.string.place_detail_radius),
            stringResource(R.string.place_radius_value, place.radiusM),
        )
        Divider()
        FactWith(R.drawable.ic_visibility, stringResource(R.string.place_detail_type)) { PlaceTypeTag(place.type) }
        Divider()
        FactWithCategory(place)
        Divider()
        Fact(R.drawable.ic_group, stringResource(R.string.place_detail_visible_by), state.miffName)
        if (state.creatorName.isNotEmpty()) {
            Divider()
            val who =
                if (state.creatorIsMe) {
                    stringResource(R.string.place_detail_created_by_me, state.creatorName)
                } else {
                    state.creatorName
                }
            Fact(R.drawable.ic_edit, stringResource(R.string.place_detail_created_by), who)
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(StrokeHairline).background(colorBorderDefault))
}

@Composable
private fun IconBox(icon: Int) {
    Box(
        modifier = Modifier.size(Spacing.xl + Spacing.s).clip(ShapeMd).background(colorActionPrimaryTint20),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colorActionPrimary,
            modifier = Modifier.size(Spacing.l),
        )
    }
}

@Composable
private fun Fact(
    icon: Int,
    label: String,
    value: String,
) {
    FactWith(icon, label) { Text(text = value, style = textBodyRegular, color = colorTextPrimary) }
}

@Composable
private fun FactWith(
    icon: Int,
    label: String,
    value: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget + Spacing.l)
                .padding(horizontal = Spacing.m, vertical = Spacing.s)
                .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        IconBox(icon)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs / 2)) {
            Text(text = label, style = textBodyRegular14, color = colorTextDescription)
            value()
        }
    }
}

@Composable
private fun FactWithCategory(place: Place) {
    val category = place.category
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget + Spacing.l)
                .padding(horizontal = Spacing.m, vertical = Spacing.s)
                .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        CategoryIcon(category, size = Spacing.xl + Spacing.s)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs / 2)) {
            Text(
                text = stringResource(R.string.place_detail_category),
                style = textBodyRegular14,
                color = colorTextDescription,
            )
            Text(
                text = stringResource((category ?: com.tomessa.miff.domain.place.PlaceCategory.OTHER).labelRes),
                style = textBodyRegular,
                color = colorTextPrimary,
            )
        }
    }
}

/** "Ce que ça change pour toi": who sees what, and what triggers what, in plain words (trust, store review, risk R16). */
@Composable
private fun InfoCard(text: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .background(colorActionPrimaryTint5)
                .border(StrokeHairline, colorBorderAction, ShapeMd)
                .padding(Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_loc_info),
            contentDescription = null,
            tint = colorActionPrimary,
            modifier = Modifier.size(Spacing.l),
        )
        Text(text = text, style = textBodyRegular, color = colorTextPrimary)
    }
}
