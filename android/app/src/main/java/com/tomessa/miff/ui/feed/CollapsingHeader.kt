package com.tomessa.miff.ui.feed

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.animate
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext

/*
 * The header of the Feed (avatar, logo, name of the Miff and its selector, and later the button of the notifications) hides while the person scrolls
 * DOWN and comes back AT ONCE when they scroll up, without waiting to be at the top of the list ("enter always", decision of the PM, 2026-11-06,
 * which replaces "en-tête fixe" of the ticket FEED-1).
 *
 * How, and what it costs: the header is drawn over the list and moved by a translation read in the DRAW phase only (no recomposition, no layout, no
 * server reading); the list keeps a constant top padding equal to the header height, so the content never jumps and nothing is hidden. The two move
 * together, finger-for-finger, by a nested scroll (the list does its own scrolling, the header only follows the deltas).
 */

/**
 * The position of the header: 0 = fully shown, -[heightPx] = fully hidden. Plain state with no Android dependency: the rules below are tested on the JVM.
 */
class CollapsingHeaderState(
    initialHeightPx: Int = 0,
) {
    /** The translation of the header in pixels (0 or negative). Read in the draw phase. */
    var offsetPx by mutableFloatStateOf(0f)
        private set

    /** Height of the header with its margins, measured. 0 until it is measured: nothing hides before. */
    var heightPx by mutableIntStateOf(initialHeightPx)
        private set

    /**
     * True when the header must stay visible whatever the scroll does: loading, error, offline, accessibility service on, selector of the Miffs open,
     * a list too short to scroll. It then comes back (see [rememberHeaderLock]).
     */
    var locked by mutableStateOf(false)

    fun onMeasured(height: Int) {
        heightPx = height
        offsetPx = offsetPx.coerceIn(-height.toFloat(), 0f)
    }

    /** The finger moved by [deltaY] (negative = the content goes up = scrolling down): the header follows, in both directions, at once. */
    fun follow(deltaY: Float) {
        if (locked || heightPx == 0) return
        offsetPx = (offsetPx + deltaY).coerceIn(-heightPx.toFloat(), 0f)
    }

    /**
     * Never more hidden than the list has been scrolled while it is near its top: the list starts under the header (padding), so a header hidden more than
     * the scroll would leave a blank band above the first card. Away from the top ([scrolledFromTopPx] null) nothing limits it.
     */
    fun limitToScroll(scrolledFromTopPx: Int?) {
        if (scrolledFromTopPx == null) return
        offsetPx = offsetPx.coerceAtLeast(-scrolledFromTopPx.coerceAtLeast(0).toFloat())
    }

    fun show() {
        offsetPx = 0f
    }

    /** Eases the header back to fully shown (used when it gets locked, so that it does not jump). */
    suspend fun animateToShown() {
        if (offsetPx == 0f) return
        animate(initialValue = offsetPx, targetValue = 0f) { value, _ -> offsetPx = value }
    }

    companion object {
        /** Only the last measured height survives a rotation or a change of font size (the header itself is shown again): no jump of one image. */
        val Saver: Saver<CollapsingHeaderState, Int> =
            Saver(save = { it.heightPx }, restore = { CollapsingHeaderState(it) })
    }
}

/** Connects the scroll of the list to the header: pure follower, it never consumes anything (the list scrolls itself). */
internal class CollapsingHeaderConnection(
    private val state: CollapsingHeaderState,
    private val list: LazyListState,
) : NestedScrollConnection {
    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        state.follow(available.y)
        return Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        // Near the top of the list (first card still partly scrolled in), the header is never more hidden than the list has been scrolled.
        state.limitToScroll(if (list.firstVisibleItemIndex == 0) list.firstVisibleItemScrollOffset else null)
        return Offset.Zero
    }
}

/**
 * Keeps [state] locked while [locked] is true, and brings the header back smoothly when it gets locked. The reasons are decided by the screen: not a
 * loaded list, no network, an accessibility service on, the selector of the Miffs open.
 */
@Composable
internal fun rememberHeaderLock(
    state: CollapsingHeaderState,
    locked: Boolean,
) {
    LaunchedEffect(locked) {
        state.locked = locked
        if (locked) state.animateToShown()
    }
}

/**
 * True while an accessibility service is on (TalkBack, a switch, a voice service): then the header NEVER hides (it would take the focus of the
 * screen reader with it, or move away from what a switch is pointing at). Follows the setting while the screen is on.
 */
@Composable
internal fun rememberAccessibilityActive(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager }
    var active by remember { mutableStateOf(manager?.let(::accessibilityOn) ?: false) }
    DisposableEffect(manager) {
        if (manager == null) return@DisposableEffect onDispose { }
        val stateListener = AccessibilityManager.AccessibilityStateChangeListener { active = accessibilityOn(manager) }
        val touchListener =
            AccessibilityManager.TouchExplorationStateChangeListener {
                active =
                    accessibilityOn(
                        manager,
                    )
            }
        manager.addAccessibilityStateChangeListener(stateListener)
        manager.addTouchExplorationStateChangeListener(touchListener)
        active = accessibilityOn(manager)
        onDispose {
            manager.removeAccessibilityStateChangeListener(stateListener)
            manager.removeTouchExplorationStateChangeListener(touchListener)
        }
    }
    return active
}

private fun accessibilityOn(manager: AccessibilityManager): Boolean =
    manager.isEnabled || manager.isTouchExplorationEnabled
