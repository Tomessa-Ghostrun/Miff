package com.tomessa.miff.ui.splash

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
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
import com.tomessa.miff.domain.startup.StartupMark
import com.tomessa.miff.startup.StartupTrace
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorBrandPrimary
import com.tomessa.miff.ui.theme.colorTextOnAction

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
 * Order of the launch: system launch screen (blue, "M + i") -> this animation -> the app. The first image of the
 * animation is deliberately LIGHT: only the blue and the "M + i", whose outlines are prepared off the main thread. The app
 * ([content]) is composed UNDER the animation only once the heart has appeared ([SplashTimeline.contentMountMs]): its first
 * composition freezes the screen for a moment, and before that change the "M" alone stayed for as long as that freeze
 * lasted (PM recette 2026-10-31). The splash never waits for the app and never lasts longer than [SplashTimeline.totalMs]
 * (no network wait: if the first screen is not ready yet, it shows its own loading state). The carousel of the
 * Onboarding ticket is part of [content] (it only appears when the splash is gone).
 *
 * @param reduceMotion see [ReduceMotion]: no movement, the full logo stays still briefly.
 * @param onFirstFrame called once the first image of the animation has been drawn (and also, as a safety net, when the
 *   animation is already over after the screen was restored): the place to start what can wait for that image, such as
 *   the resolution of the session. It must be safe to call twice.
 */
@Composable
fun SplashHost(
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    onFirstFrame: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val timeline = remember(reduceMotion) { SplashTimeline(reduceMotion) }
    var finished by rememberSaveable { mutableStateOf(false) }
    // Saved with the instance state: after a rotation the timeline goes on instead of starting again (which could
    // otherwise stretch the splash past its maximum duration).
    val elapsedMs = rememberSaveable { mutableLongStateOf(0L) }
    var contentMounted by remember { mutableStateOf(false) }
    val currentOnFirstFrame by rememberUpdatedState(onFirstFrame)

    // The system "back" during the animation does nothing (it must not reach the screen composed underneath, see
    // [LocalSplashCovering]); it does not leave the app either: the splash lasts 3 s at most.
    BackHandler(enabled = !finished) {}

    LaunchedEffect(timeline, finished) {
        if (finished) {
            // Restored after the animation was over (process recreated): there is no first image to wait for.
            withFrameNanos { }
            contentMounted = true
            currentOnFirstFrame()
            return@LaunchedEffect
        }
        val base = elapsedMs.longValue
        // The clock starts at the frame after the first image (the first image is drawn before this effect resumes).
        val start = withFrameNanos { it }
        // Real time from the frame clock, not an Animatable: the "animation scale" of the developer options must not
        // be able to stretch the splash beyond its maximum duration. A freeze of the main thread (the first composition
        // of the app) therefore costs animation, never extra seconds.
        var elapsed = base
        while (!timeline.isFinished(elapsed)) {
            if (!contentMounted && timeline.shouldMountContent(elapsed)) {
                StartupTrace.mark(StartupMark.CONTENT_MOUNTED)
                contentMounted = true
            }
            val now = withFrameNanos { it }
            elapsed = base + (now - start) / NANOS_PER_MILLI
            elapsedMs.longValue = elapsed
        }
        finished = true
        StartupTrace.onSplashDone()
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (contentMounted || finished) {
            // While the animation covers the app, the screen reader must read only "Miff", not the screen underneath.
            Box(modifier = if (finished) Modifier else Modifier.clearAndSetSemantics { }) {
                CompositionLocalProvider(LocalSplashCovering provides !finished) { content() }
            }
        }
        if (!finished) {
            SplashScreen(
                timeline = timeline,
                elapsedMs = { elapsedMs.longValue },
                onFirstDraw = { currentOnFirstFrame() },
            )
        }
    }
}

/**
 * The animation itself. Decorative: the screen reader only gets "Miff". No sound, no vibration, no analytics event.
 * The frame is read in the draw and layer phases only: no recomposition per frame.
 *
 * [onFirstDraw] is posted to the main thread right after the first image has been drawn (never run inside the draw).
 */
@Composable
internal fun SplashScreen(
    timeline: SplashTimeline,
    elapsedMs: () -> Long,
    modifier: Modifier = Modifier,
    onFirstDraw: () -> Unit = {},
) {
    val description = stringResource(R.string.android_splash_a11y)
    // Prepared on a background thread at the start of the process; parsed here only if that did not finish in time.
    val paths = remember { SplashArtwork.paths }
    val view = LocalView.current
    val firstDrawDone = remember { BooleanArray(1) }
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
                    // The "M + i" of the system launch screen, same outline, same canvas, same size: no visible jump.
                    drawPath(path = paths.mark, color = colorTextOnAction)
                    drawHeart(paths.heart, frame.heartScale, frame.heartAlpha)
                    frame.burstProgress?.let { drawBurst(it) }
                }
            }
            // Measure only (one clock read): the first image, the first image with the heart, the longest gap between two.
            StartupTrace.onSplashDraw(heartVisible = frame.heartAlpha > 0f && frame.heartScale > 0f)
            if (!firstDrawDone[0]) {
                firstDrawDone[0] = true
                view.post(onFirstDraw)
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
