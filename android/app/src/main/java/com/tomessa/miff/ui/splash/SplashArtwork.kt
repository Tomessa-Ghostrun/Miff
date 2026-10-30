package com.tomessa.miff.ui.splash

import androidx.compose.ui.geometry.Offset
import com.tomessa.miff.domain.splash.SplashTimeline

/**
 * Geometry of the launch animation, in the 108-unit canvas of the adaptive icon (res/drawable/splash_logo_mark.xml
 * uses the same canvas). Colors are NOT here: they come from the design tokens (ui/theme/Color.kt).
 */
internal object SplashArtwork {
    /** Canvas size, in units. */
    const val CANVAS = 108f

    /** Size at which the canvas is drawn: 288 dp, like the Android 12 launch-screen icon, so there is no jump. */
    const val CANVAS_DP = 288f

    /**
     * The heart, copied from the second path of ic_launcher_foreground.xml (the official icon).
     * SplashArtworkTest fails if the two drift apart.
     */
    const val HEART_PATH =
        "M68.59 30.23C69.88 28.95 71.77 28.45 73.53 28.95C75.28 29.44 76.64 30.84 77.07 32.61" +
            "C77.5 34.38 76.94 36.25 75.61 37.5L68.71 43.95C68.42 44.23 68.13 44.22 67.84 43.93" +
            "L61.25 37.17C59.97 35.86 59.5 33.97 60.02 32.22C60.53 30.47 61.95 29.13 63.73 28.72" +
            "C65.5 28.31 67.36 28.89 68.59 30.23Z"

    /** Middle of the heart: the balloon swells around this point. */
    val HEART_CENTER = Offset(68.5f, 36.3f)

    /** Half width of the resting heart, in units. */
    const val HEART_HALF_WIDTH = 8.6f

    /** Pieces start on the edge of the burst heart (a bit inside, the heart is not round) and fly this far, in units. */
    val PARTICLE_START_RADIUS = HEART_HALF_WIDTH * SplashTimeline.MAX_SCALE * 0.85f
    const val PARTICLE_TRAVEL = 17f

    /** Radius of a full-size piece, in units. */
    const val PARTICLE_RADIUS = 2.2f
}
