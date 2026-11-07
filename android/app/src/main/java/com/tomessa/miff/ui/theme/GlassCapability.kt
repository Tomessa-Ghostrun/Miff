package com.tomessa.miff.ui.theme

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.tomessa.miff.ui.splash.ReduceMotion

/**
 * How much "glass" the tab bar may be on this phone right now (design/tab-bar-liquid-glass.md sections 4, 9 and 10).
 * - [BLUR]: the background behind the bar is blurred, plus a veil (Android 12 and up, nothing asked against it).
 * - [NO_BLUR]: nothing is blurred, a denser veil instead (before Android 12, a modest phone, the camera viewfinder).
 * - [OPAQUE]: no transparency at all (battery saver, animations removed, increased contrast).
 */
enum class GlassLevel { BLUR, NO_BLUR, OPAQUE }

/** What the decision reads from the phone: plain values, so that the rules are testable without a device. */
data class GlassConditions(
    val sdkInt: Int,
    val powerSaveMode: Boolean,
    /** "Remove animations" / animation scale at 0. */
    val animationsRemoved: Boolean,
    /** Increased contrast asked in the system (`UiModeManager.getContrast()`, Android 14 and up only: nothing is readable before). */
    val contrastIncreased: Boolean,
    val lowRamDevice: Boolean,
)

object GlassPolicy {
    /**
     * THE SWITCH of the fallback: set to `false` and rebuild to ship the bar WITHOUT any blur (the denser veil of [GlassLevel.NO_BLUR]
     * everywhere, and the recording of the content for the blur is never done). To use if the recipe on the OPPO Find X5 Lite shows
     * dropped frames while scrolling, or if the blur library has to be dropped (plan B of the ticket).
     */
    const val BLUR_ALLOWED = true

    /** `RenderEffect` (the blur of the platform) exists from Android 12 (API 31). Below, Haze would use RenderScript (deprecated): not used. */
    const val MIN_BLUR_SDK = 31

    /**
     * Opaque first (what the person asked the system for), then "nothing to blur" cases, then the blur. The viewfinder is a
     * `SurfaceView`, which cannot be captured, and a blur of a 30 images per second video would heat the phone: the variant ON_PHOTO
     * therefore never blurs (the preview of a still photo gets the same bar, for consistency).
     */
    fun level(
        conditions: GlassConditions,
        variant: GlassVariant,
        blurAllowed: Boolean = BLUR_ALLOWED,
    ): GlassLevel =
        when {
            conditions.powerSaveMode -> GlassLevel.OPAQUE
            conditions.animationsRemoved -> GlassLevel.OPAQUE
            conditions.contrastIncreased -> GlassLevel.OPAQUE
            !blurAllowed || conditions.lowRamDevice || conditions.sdkInt < MIN_BLUR_SDK -> GlassLevel.NO_BLUR
            variant == GlassVariant.ON_PHOTO -> GlassLevel.NO_BLUR
            else -> GlassLevel.BLUR
        }
}

/** Reads the conditions from the system. Every call is cheap and needs NO permission. */
object GlassConditionsReader {
    fun read(context: Context): GlassConditions =
        GlassConditions(
            sdkInt = Build.VERSION.SDK_INT,
            powerSaveMode = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true,
            animationsRemoved = ReduceMotion.isRequested(context),
            contrastIncreased = contrastIncreased(context),
            lowRamDevice = context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true,
        )

    private fun contrastIncreased(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        // Standard = 0, medium = 0.5, high = 1 (Android 14 "Contrast" setting).
        return runCatching { context.getSystemService(UiModeManager::class.java)?.contrast ?: 0f }.getOrDefault(0f) > 0f
    }
}

/**
 * The conditions of the phone, kept up to date while the composition lives: battery saver (system broadcast), animation scale
 * (setting observed), contrast (system listener, Android 14+). No polling. Nothing leaves the phone.
 */
@Composable
fun rememberGlassConditions(): GlassConditions {
    val context = LocalContext.current
    var conditions by remember(context) { mutableStateOf(GlassConditionsReader.read(context)) }
    DisposableEffect(context) {
        val refresh = { conditions = GlassConditionsReader.read(context) }
        refresh()
        val powerReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) = refresh()
            }
        // A protected system broadcast: delivered to a receiver that is NOT exported, no permission asked.
        ContextCompat.registerReceiver(
            context,
            powerReceiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val animationObserver =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(
                    selfChange: Boolean,
                    uri: Uri?,
                ) = refresh()
            }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            animationObserver,
        )
        val contrastWatch: AutoCloseable? =
            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            ) {
                ContrastWatch.start(context, refresh)
            } else {
                null
            }
        onDispose {
            context.unregisterReceiver(powerReceiver)
            context.contentResolver.unregisterContentObserver(animationObserver)
            contrastWatch?.close()
        }
    }
    return conditions
}

/** The listener of the contrast setting exists from Android 14 only: kept in its own class so that older phones never load it. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private class ContrastWatch private constructor(
    private val manager: UiModeManager?,
    private val listener: UiModeManager.ContrastChangeListener,
) : AutoCloseable {
    override fun close() {
        manager?.removeContrastChangeListener(listener)
    }

    companion object {
        fun start(
            context: Context,
            onChange: () -> Unit,
        ): ContrastWatch {
            val manager = context.getSystemService(UiModeManager::class.java)
            val listener = UiModeManager.ContrastChangeListener { onChange() }
            manager?.addContrastChangeListener(ContextCompat.getMainExecutor(context), listener)
            return ContrastWatch(manager, listener)
        }
    }
}
