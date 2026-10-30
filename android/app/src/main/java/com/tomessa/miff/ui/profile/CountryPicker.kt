package com.tomessa.miff.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.tomessa.miff.R
import com.tomessa.miff.domain.profile.PhoneCountry
import com.tomessa.miff.domain.profile.PhoneNumbers
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeHairline
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderStrong
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textTitleBold20
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The closed "Pays" selector of the mockup: flag, "France +33" and the little triangle. One element for the screen reader,
 * which says the country and the calling code, and that a tap offers another choice.
 */
@Composable
fun CountryField(
    country: PhoneCountry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val description = stringResource(R.string.profile_phone_country_field_a11y, country.name, country.dialCode)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.profile_phone_country_label),
            style = textBodyRegular,
            color = colorTextDescription,
            // Read as part of the field below.
            modifier = Modifier.clearAndSetSemantics {},
        )
        Spacer(Modifier.size(Spacing.xs))
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = ShapeMd,
            color = colorBgSurface,
            border = BorderStroke(StrokeHairline, colorBorderStrong),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.minTouchTarget)
                    .semantics {
                        role = Role.DropdownList
                        contentDescription = description
                    },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = country.flag, style = textBodyRegular)
                Spacer(Modifier.size(Spacing.s))
                Text(
                    text = "${country.name} ${country.dialCode}",
                    style = textBodyRegular,
                    color = colorTextForm,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_drop_down),
                    contentDescription = null,
                    tint = colorTextDescription,
                )
            }
        }
    }
}

/**
 * The list of ALL the countries (ticket 6.4) in a bottom sheet, with a search field (accents and case ignored; by name,
 * code or calling code). The list is built in the background the first time the sheet opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryPickerSheet(
    selectedRegion: String,
    locale: Locale,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val countries by produceState(initialValue = emptyList<PhoneCountry>(), locale) {
        value = withContext(Dispatchers.Default) { PhoneNumbers.countries(locale) }
    }
    var query by remember { mutableStateOf("") }
    val shown = remember(countries, query) { countries.filter { PhoneNumbers.matchesSearch(it, query) } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colorBgSurface) {
        Column(
            modifier = Modifier.navigationBarsPadding().padding(horizontal = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                text = stringResource(R.string.profile_phone_picker_title),
                style = textTitleBold20,
                color = colorTextForm,
                modifier = Modifier.semantics { heading() },
            )
            LabeledOutlinedField(
                value = query,
                onValueChange = { query = it.take(MAX_SEARCH_LENGTH) },
                label = stringResource(R.string.profile_phone_picker_search),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
            )
            if (countries.isNotEmpty() && shown.isEmpty()) {
                Text(
                    text = stringResource(R.string.profile_phone_picker_empty),
                    style = textBodyRegular,
                    color = colorTextDescription,
                    textAlign = TextAlign.Start,
                )
            }
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(shown, key = { it.region }) { country ->
                    CountryRow(
                        country,
                        selected = country.region == selectedRegion,
                        onPick = { onSelected(country.region) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CountryRow(
    country: PhoneCountry,
    selected: Boolean,
    onPick: () -> Unit,
) {
    val description = stringResource(R.string.profile_phone_picker_item_a11y, country.name, country.dialCode)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget)
                .selectable(selected = selected, onClick = onPick, role = Role.RadioButton)
                .semantics { contentDescription = description }
                .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = country.flag, style = textBodyRegular)
        Spacer(Modifier.size(Spacing.m))
        Text(
            text = country.name,
            style = textBodyRegular.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
            color = if (selected) colorActionPrimary else colorTextForm,
            modifier = Modifier.weight(1f),
        )
        Text(text = country.dialCode, style = textBodyRegular, color = colorTextDescription)
    }
}

private const val MAX_SEARCH_LENGTH = 40
