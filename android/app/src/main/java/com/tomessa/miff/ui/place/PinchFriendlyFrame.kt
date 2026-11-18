package com.tomessa.miff.ui.place

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * The box around the map. The page scrolls under one finger, even when the finger starts on the map; as soon as a SECOND finger
 * touches the map (a pinch), the page stops taking the gesture, so that a pinch is never taken for a scroll and the page does not
 * jump while the person zooms. The page takes the hand back when the fingers leave.
 */
internal class PinchFriendlyFrame(
    context: Context,
) : FrameLayout(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return super.dispatchTouchEvent(event)
    }
}
