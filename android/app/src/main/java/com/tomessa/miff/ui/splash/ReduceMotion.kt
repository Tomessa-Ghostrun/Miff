package com.tomessa.miff.ui.splash

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings

/**
 * True when the person asked the system for no animation: "Remove animations" (accessibility) or the animation scale
 * set to "off" both end up as an animator duration scale of 0. The splash then shows the full logo, still.
 */
object ReduceMotion {
    fun isRequested(context: Context): Boolean = isRequested(context.contentResolver)

    internal fun isRequested(resolver: ContentResolver): Boolean = scaleIsOff(readAnimatorScale(resolver))

    internal fun scaleIsOff(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

    private fun readAnimatorScale(resolver: ContentResolver): Float =
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
}
