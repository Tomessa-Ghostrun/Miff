package com.tomessa.miff.ui.feed

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.tomessa.miff.R
import com.tomessa.miff.ui.components.miffTopInsets
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textDisplayHeadline

/**
 * The Feed tab, PROVISIONAL (ticket ONB-3a, Question 15): the real Feed is the FEED-1 and FEED-2 tickets and needs the Miff
 * screens. This one only says, honestly, that the screen is not built yet: no invented person, no "tout va bien", no promise
 * of a date, and no bell (decision Q15: nothing to show in a notifications centre yet). The wordmark of the mockups stays.
 * [scrollToTopSignal] goes up each time the person taps the tab they are already on.
 */
@Composable
fun FeedPlaceholderScreen(scrollToTopSignal: Int) {
    val scroll = rememberScrollState()
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) scroll.animateScrollTo(0)
    }
    val paneName = stringResource(R.string.feed_pane_a11y)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .miffTopInsets()
                .verticalScroll(scroll)
                .padding(horizontal = Spacing.l, vertical = Spacing.m)
                .semantics { paneTitle = paneName },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Image(
            painter = painterResource(R.drawable.miff_wordmark_color),
            contentDescription = stringResource(R.string.home_logo_a11y),
            modifier = Modifier.width(Spacing.logoWidth / 2),
        )
        Box(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text(
                    text = stringResource(R.string.feed_empty_title),
                    style = textDisplayHeadline,
                    color = colorTextForm,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.feed_empty_body),
                    style = textBodyRegular,
                    color = colorTextDescription,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
