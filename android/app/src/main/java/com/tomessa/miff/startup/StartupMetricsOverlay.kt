package com.tomessa.miff.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomessa.miff.R
import com.tomessa.miff.domain.startup.StartupReport
import com.tomessa.miff.ui.components.miffTopInsets
import kotlinx.coroutines.delay

/**
 * Small readout of the durations of the launch, shown at the top of the screen once the animation has finished, in the
 * debug and "releaseLike" builds only (BuildConfig.STARTUP_METRICS; the call site is removed from release builds). The PM
 * reads it and reports the numbers. Tap to close; it closes by itself after 45 s. It never touches the screen reader's
 * path of the real app (it is a plain internal tool) and shows no personal data.
 */
@Composable
fun StartupMetricsOverlay(modifier: Modifier = Modifier) {
    val report by StartupTrace.completedReport.collectAsStateWithLifecycle()
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(report) {
        if (report != null) {
            delay(AUTO_CLOSE_MS)
            dismissed = true
        }
    }
    val shown = report
    if (shown == null || dismissed) return

    Column(
        modifier =
            modifier
                .testTag("STARTUPMETRICSOVERLAY")
                .miffTopInsets()
                .padding(8.dp)
                .fillMaxWidth()
                .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                .clickable { dismissed = true }
                .padding(10.dp),
    ) {
        val style = TextStyle(color = Color.White, fontSize = 12.sp, lineHeight = 16.sp)
        Text(stringResource(R.string.android_startup_metrics_title), style = style.copy(fontSize = 13.sp))
        Text(
            stringResource(
                if (shown.cold) R.string.android_startup_metrics_cold else R.string.android_startup_metrics_warm,
            ),
            style = style,
        )
        lines(shown).forEach { Text(it, style = style) }
        Text(stringResource(R.string.android_startup_metrics_close), style = style.copy(color = Color(0xFFBBBBBB)))
    }
}

@Composable
private fun lines(report: StartupReport): List<String> {
    fun ms(value: Long?): String = value?.toString() ?: "-"
    return listOf(
        stringResource(R.string.android_startup_metrics_to_heart, ms(report.toHeartMs)),
        stringResource(R.string.android_startup_metrics_to_first_frame, ms(report.toFirstFrameMs)),
        stringResource(R.string.android_startup_metrics_hold, ms(report.firstFrameToHeartMs)),
        stringResource(
            R.string.android_startup_metrics_application,
            ms(report.processToApplicationMs),
            ms(report.applicationCreateMs),
        ),
        stringResource(R.string.android_startup_metrics_app_to_frame, ms(report.appToFirstFrameMs)),
        stringResource(R.string.android_startup_metrics_mounted, ms(report.toContentMountedMs)),
        stringResource(R.string.android_startup_metrics_freeze, report.maxFrameGapMs, report.frames),
    )
}

private const val AUTO_CLOSE_MS = 45_000L
