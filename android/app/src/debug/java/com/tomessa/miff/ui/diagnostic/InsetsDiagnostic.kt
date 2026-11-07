package com.tomessa.miff.ui.diagnostic

import android.os.Build
import android.util.DisplayMetrics
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tomessa.miff.ui.components.SystemBarSizes
import com.tomessa.miff.ui.components.miffSystemBarsFallbackInUse
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.textBodyRegular14

/**
 * DEBUG ONLY (this file does not exist in the release build). "System areas": the raw values that THIS phone reports, live, so
 * that a photo of the card tells us why a screen would go under the status bar or the navigation bar. Every value is in
 * pixels, format "left, top, right, bottom". Nothing leaves the phone and nothing is written.
 *
 * It is the one place besides ui/components/ScreenInsets.kt allowed to read the raw insets (see ScreenInsetsRuleTest).
 */
@Composable
internal fun InsetsDiagnosticCard() {
    SectionCard("Marges système (insets) : à photographier") {
        InsetsLines().forEach { line ->
            Text(
                text = line,
                style = textBodyRegular14.copy(fontFamily = FontFamily.Monospace),
                color = colorTextPrimary,
            )
        }
    }
}

/** Marker of the layout rules in this build, to be sure from a photo which APK is installed. */
internal const val INSETS_BUILD_MARKER = "insets-r2 (replis actifs)"

@Composable
private fun InsetsLines(): List<String> {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current

    fun raw(insets: WindowInsets): String =
        "${insets.getLeft(
            density,
            direction,
        )}, ${insets.getTop(density)}, ${insets.getRight(density, direction)}, ${insets.getBottom(density)}"

    val compose =
        listOf(
            "statusBars" to raw(WindowInsets.statusBars),
            "navigationBars" to raw(WindowInsets.navigationBars),
            "systemBars" to raw(WindowInsets.systemBars),
            "displayCutout" to raw(WindowInsets.displayCutout),
            "ime" to raw(WindowInsets.ime),
            "safeDrawing" to raw(WindowInsets.safeDrawing),
            "tappableElement" to raw(WindowInsets.tappableElement),
            "captionBar" to raw(WindowInsets.captionBar),
            "systemGestures" to raw(WindowInsets.systemGestures),
            "mandatoryGestures" to raw(WindowInsets.mandatorySystemGestures),
        )

    val root = ViewCompat.getRootWindowInsets(view)

    fun platform(
        name: String,
        type: Int,
    ): String {
        root ?: return "$name: (rootWindowInsets absent)"
        val i = root.getInsets(type)
        val v = if (root.isVisible(type)) "visible" else "hidden"
        return "$name: ${i.left}, ${i.top}, ${i.right}, ${i.bottom} $v"
    }

    val cutout = root?.displayCutout
    val onScreen = IntArray(2).also { view.getLocationOnScreen(it) }
    val display = DisplayMetrics()

    @Suppress("DEPRECATION")
    view.display?.getRealMetrics(display)
    val fallback = miffSystemBarsFallbackInUse()
    val navigationMode =
        when (SystemBarSizes.navigationMode()) {
            0 -> "3 boutons"
            1 -> "2 boutons"
            2 -> "gestes"
            else -> "inconnu"
        }

    return buildList {
        add("Version des marges : $INSETS_BUILD_MARKER")
        add("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        add("Navigation : $navigationMode (config_navBarInteractionMode=${SystemBarSizes.navigationMode()})")
        add("Densité : ${density.density} (${(density.density * DP_BASE).toInt()} dpi), police x${fontScale(density)}")
        add(
            "Écran réel : ${display.widthPixels} x ${display.heightPixels} px, " +
                orientation(configuration.orientation),
        )
        add("Fenêtre : ${view.width} x ${view.height} px, position à l'écran ${onScreen[0]}, ${onScreen[1]}")
        add("Sens d'écriture : ${if (direction == LayoutDirection.Ltr) "gauche-droite" else "droite-gauche"}")
        add("--- Compose (gauche, haut, droite, bas) ---")
        compose.forEach { (name, value) -> add("$name: $value") }
        add("--- Système (rootWindowInsets) ---")
        add(platform("statusBars", WindowInsetsCompat.Type.statusBars()))
        add(platform("navigationBars", WindowInsetsCompat.Type.navigationBars()))
        add(platform("systemBars", WindowInsetsCompat.Type.systemBars()))
        add(platform("displayCutout", WindowInsetsCompat.Type.displayCutout()))
        add(platform("ime", WindowInsetsCompat.Type.ime()))
        add(platform("tappableElement", WindowInsetsCompat.Type.tappableElement()))
        add(
            "encoche (cutout) : " +
                (
                    cutout?.let {
                        "haut ${it.safeInsetTop}, bas ${it.safeInsetBottom}, gauche ${it.safeInsetLeft}, droite ${it.safeInsetRight}"
                    }
                        ?: "aucune"
                ),
        )
        add("--- Tailles déclarées par le système ---")
        add(
            "status_bar_height: ${SystemBarSizes.statusBarHeight(
                landscape = false,
            )} px (paysage ${SystemBarSizes.statusBarHeight(true)})",
        )
        add("navigation_bar_height: ${SystemBarSizes.navigationBarHeight()} px")
        add("--- Repli de l'application ---")
        add(
            "ajouté en haut: ${fallback.top} px, en bas: ${fallback.bottom} px (0 = le téléphone donne de vraies valeurs)",
        )
    }
}

private const val DP_BASE = 160

private fun fontScale(density: Density) = density.fontScale

private fun orientation(value: Int) =
    if (value ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    ) {
        "paysage"
    } else {
        "portrait"
    }
