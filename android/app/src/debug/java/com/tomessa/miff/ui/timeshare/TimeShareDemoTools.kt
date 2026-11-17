package com.tomessa.miff.ui.timeshare

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.domain.place.Place
import com.tomessa.miff.domain.timeshare.DemoPresence
import com.tomessa.miff.domain.timeshare.PresenceLog
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderStrong
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.textBodyBold14
import com.tomessa.miff.ui.theme.textBodyRegular14

/**
 * DEBUG BUILD ONLY (criterion TS-23): under the card "Répartition de ton temps", two buttons that fill the LOCAL journal of the presence of the Miff shown with TEST data (three weeks
 * of a believable routine over the places of that Miff) or empty it, so that the PM can see every state of the card without waiting for days of real data. It is labelled "données
 * de test". The release build has a file of the same name in src/release that draws nothing; the marker [DemoPresence.MARKER] is looked for in the release bundle by the CI.
 * Neither writes anything to the server, and neither reads a position.
 */
object TimeShareDemoTools {
    @Composable
    fun Panel(
        log: PresenceLog,
        miffId: String,
        places: List<Place>,
    ) {
        var filled by remember(miffId) { mutableStateOf(false) }
        val shape = RoundedCornerShape(10.dp)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(DemoPresence.MARKER)
                    .clip(shape)
                    .background(colorBgSurface)
                    .border(1.dp, colorBorderStrong, shape)
                    .padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                stringResource(R.string.android_timeshare_demo_title),
                style = textBodyBold14,
                color = colorTextDescription,
            )
            Text(
                stringResource(R.string.android_timeshare_demo_body),
                style = textBodyRegular14,
                color = colorTextDescription,
            )
            MiffSecondaryButton(
                text = stringResource(R.string.android_timeshare_demo_fill),
                onClick = {
                    DemoPresence.fill(log, miffId, places.map { it.id to it.type })
                    filled = true
                },
                modifier = Modifier.fillMaxWidth(),
            )
            MiffSecondaryButton(
                text = stringResource(R.string.android_timeshare_demo_clear),
                onClick = {
                    log.forgetMiff(miffId)
                    filled = false
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (filled) {
                Text(
                    stringResource(R.string.android_timeshare_demo_done),
                    style = textBodyRegular14,
                    color = colorTextDescription,
                )
            }
        }
    }
}
