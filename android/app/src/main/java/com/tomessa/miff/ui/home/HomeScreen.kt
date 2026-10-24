package com.tomessa.miff.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.tomessa.miff.R
import com.tomessa.miff.ui.theme.GradientAuthBackground
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textDisplayHeadline

/**
 * Minimal welcome screen (what the release build shows). The logo is PROVISIONAL, see android/README.md.
 *
 * The content is vertically centered when it fits, and scrolls when it does not (largest system font, landscape,
 * split screen): nothing may be cut off without a way to reach it (AND-003).
 */
@Composable
fun HomeScreen() {
    // fillMaxSize() BEFORE verticalScroll(): the column is at least as tall as the screen, so Arrangement.Center
    // keeps the same look as before when there is room, and the content scrolls when there is not.
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GradientAuthBackground)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = stringResource(R.string.home_logo_a11y),
            modifier =
                Modifier
                    .size(Spacing.logoSize)
                    .clip(ShapeMd)
                    .background(colorActionPrimary),
        )
        Text(
            text = stringResource(R.string.home_title),
            style = textDisplayHeadline,
            color = colorTextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.home_tagline),
            style = textBodyRegular,
            color = colorTextDescription,
            textAlign = TextAlign.Center,
        )
    }
}
