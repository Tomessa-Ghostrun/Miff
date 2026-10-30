package com.tomessa.miff.ui.splash

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.tomessa.miff.R
import com.tomessa.miff.domain.splash.SplashTimeline
import com.tomessa.miff.domain.splash.burstParticles
import com.tomessa.miff.domain.splash.directionX
import com.tomessa.miff.domain.splash.directionY
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorBrandPrimary

/**
 * True while the splash covers the app. The screens composed underneath that handle the system "back" themselves
 * (the consent panel, where back means REFUSAL) must not react to it then: the person cannot see them. The system
 * dispatches "back" to the most recently registered handler, and those screens register theirs after the splash, so
 * they must stand down explicitly instead of relying on the order of registration.
 */
val LocalSplashCovering = staticCompositionLocalOf { false }

/**
 * Plays the launch animation ABOVE the app, once per cold start (rotating the phone keeps the timeline going; coming
 * back from the background does not replay it, the activity is simply still there).
 *
 * Order of the launch: system launch screen (blue, "M + i") -> this animation -> the app. The app ([content]) is
 * composed UNDER the animation from the second frame on, so the session is being resolved and the first screen is
 * ready by the time the animation fades out; the splash never waits for it and never lasts longer than
 * [SplashTimeline.totalMs] (no network wait: if the first screen is not ready yet, it shows its own loading state).
 * The carousel of the Onboarding ticket will be part of [content] (it only appears when the splash is gone).
 *
 * @param reduceMotion see [ReduceMotion]: no movement, the full logo stays still briefly.
 */
@Composable
fun SplashHost(
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val timeline = remember(reduceMotion) { SplashTimeline(reduceMotion) }
    var finished by rememberSaveable { mutableStateOf(false) }
    // Saved with the instance state: after a rotation the timeline goes on instead of starting again (which could
    // otherwise stretch the splash past its maximum duration).
    val elapsedMs = rememberSaveable { mutableLongStateOf(0L) }
    var contentMounted by remember { mutableStateOf(false) }

    // The system "back" during the animation does nothing (it must not reach the screen composed underneath, see
    // [LocalSplashCovering]); it does not leave the app either: the splash lasts 3 s at most.
    BackHandler(enabled = !finished) {}

    LaunchedEffect(timeline, finished) {
        // The first frame shows only the blue and the "M + i", exactly like the system launch screen. The app is
        // composed one frame later, so that its (heavier) first composition does not delay the very first frame.
        withFrameNanos { }
        contentMounted = true
        if (finished) return@LaunchedEffect
        val base = elapsedMs.longValue
        val start = withFrameNanos { it }
        // Real time from the frame clock, not an Animatable: the "animation scale" of the developer options must not
        // be able to stretch the splash beyond its maximum duration.
        while (!timeline.isFinished(elapsedMs.longValue)) {
            val now = withFrameNanos { it }
            elapsedMs.longValue = base + (now - start) / NANOS_PER_MILLI
        }
        finished = true
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (contentMounted || finished) {
            // While the animation covers the app, the screen reader must read only "Miff", not the screen underneath.
            Box(modifier = if (finished) Modifier else Modifier.clearAndSetSemantics { }) {
                CompositionLocalProvider(LocalSplashCovering provides !finished) { content() }
            }
        }
        if (!finished) {
            SplashScreen(timeline = timeline, elapsedMs = { elapsedMs.longValue })
        }
    }
}

/**
 * The animation itself. Decorative: the screen reader only gets "Miff". No sound, no vibration, no analytics event.
 * The frame is read in the draw and layer phases only: no recomposition per frame.
 */
@Composable
internal fun SplashScreen(
    timeline: SplashTimeline,
    elapsedMs: () -> Long,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.android_splash_a11y)
    val heartPath = remember { PathParser().parsePathString(SplashArtwork.HEART_PATH).toPath() }
    LightSystemBarIconsOff()

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer { alpha = timeline.frameAt(elapsedMs()).overlayAlpha }
                .background(colorActionPrimary)
                // The splash covers the app: touches must not reach it.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }.clearAndSetSemantics { contentDescription = description },
    ) {
        Image(
            painter = painterResource(R.drawable.splash_logo_mark),
            contentDescription = null,
            modifier = Modifier.align(Alignment.Center).size(SplashArtwork.CANVAS_DP.dp),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frame = timeline.frameAt(elapsedMs())
            val unit = SplashArtwork.CANVAS_DP.dp.toPx() / SplashArtwork.CANVAS
            val origin =
                Offset(
                    x = (size.width - SplashArtwork.CANVAS_DP.dp.toPx()) / 2f,
                    y = (size.height - SplashArtwork.CANVAS_DP.dp.toPx()) / 2f,
                )
            translate(origin.x, origin.y) {
                scale(unit, unit, pivot = Offset.Zero) {
                    drawHeart(heartPath, frame.heartScale, frame.heartAlpha)
                    frame.burstProgress?.let { drawBurst(it) }
                }
            }
        }
    }
}

private fun DrawScope.drawHeart(
    path: Path,
    heartScale: Float,
    alpha: Float,
) {
    if (alpha <= 0f || heartScale <= 0f) return
    val center = SplashArtwork.HEART_CENTER
    scale(heartScale, heartScale, pivot = center) {
        drawPath(path = path, color = colorBrandPrimary, alpha = alpha)
    }
}

private fun DrawScope.drawBurst(progress: Float) {
    val center = SplashArtwork.HEART_CENTER
    burstParticles(progress).forEach { piece ->
        if (piece.alpha <= 0f || piece.size <= 0f) return@forEach
        val radius = SplashArtwork.PARTICLE_START_RADIUS + SplashArtwork.PARTICLE_TRAVEL * piece.distance
        drawCircle(
            color = colorBrandPrimary,
            radius = SplashArtwork.PARTICLE_RADIUS * piece.size,
            center = Offset(center.x + piece.directionX() * radius, center.y + piece.directionY() * radius),
            alpha = piece.alpha,
        )
    }
}

/**
 * The blue splash needs light status/navigation bar icons; the rest of the app uses dark ones. Restored when the
 * splash leaves.
 */
@Composable
private fun LightSystemBarIconsOff() {
    val activity = LocalActivity.current
    val view = LocalView.current
    DisposableEffect(activity, view) {
        val window = activity?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val wasLightStatus = controller.isAppearanceLightStatusBars
            val wasLightNavigation = controller.isAppearanceLightNavigationBars
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            onDispose {
                controller.isAppearanceLightStatusBars = wasLightStatus
                controller.isAppearanceLightNavigationBars = wasLightNavigation
            }
        }
    }
}

private const val NANOS_PER_MILLI = 1_000_000L
