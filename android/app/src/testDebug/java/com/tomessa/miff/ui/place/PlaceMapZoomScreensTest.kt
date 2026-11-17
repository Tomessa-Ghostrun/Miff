package com.tomessa.miff.ui.place

import android.app.Application
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.testPlace
import com.tomessa.miff.testsupport.testSuggestion
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The zoom of the map of a place (ticket `docs/tickets/lieu-carte-zoom.md`), on the JVM: which screens allow it, what the map is
 * given, how the page and the map share the fingers. The real map (MapLibre, native) is checked on the phone (PM checklist).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w390dp-h844dp-port-xxhdpi")
class PlaceMapZoomScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private val specs = mutableListOf<PlaceMapSpec>()

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent {
            MiffTheme {
                CompositionLocalProvider(
                    LocalPlaceMapRenderer provides { spec, _, modifier ->
                        specs += spec
                        Box(modifier)
                    },
                ) { Box(Modifier.fillMaxSize()) { content() } }
            }
        }
    }

    private fun filled(
        radiusM: Int = 150,
        type: PlaceType = PlaceType.TRUSTED,
    ) = PlaceFormState(
        loading = false,
        name = "Maison",
        radiusM = radiusM,
        type = type,
        addressText = testSuggestion().label,
        chosen = testSuggestion(),
    )

    @Test
    fun form_theMapOfAChosenAddress_canBeZoomed_andKeepsItsPlaceAndRadius() {
        show { PlaceFormScreen(filled(radiusM = 300), PlaceFormActions()) }
        val spec = specs.last()
        assertTrue(spec.zoomable)
        // What the map draws is only the chosen point and the chosen radius: zooming never changes them.
        assertEquals(300, spec.radiusM)
        assertEquals(testSuggestion().lat, spec.lat, 0.0)
        assertEquals(testSuggestion().lng, spec.lng, 0.0)
        assertFalse(spec.danger)
    }

    @Test
    fun form_aDangerZone_staysZoomable_andRed() {
        show { PlaceFormScreen(filled(type = PlaceType.RISK), PlaceFormActions()) }
        assertTrue(specs.last().zoomable)
        assertTrue(specs.last().danger)
    }

    @Test
    fun form_withoutAddress_hasNoMapToZoom() {
        show { PlaceFormScreen(PlaceFormState(loading = false), PlaceFormActions()) }
        assertTrue(specs.isEmpty())
    }

    @Test
    fun form_theMapStaysOneDescribedPicture_forTheScreenReader() {
        show { PlaceFormScreen(filled(radiusM = 150), PlaceFormActions()) }
        // No button on the map (decision of the PM: gestures only): the picture is described, the address and the slider are the way
        // to make a place without the map (criterion TRA2).
        composeRule
            .onNodeWithContentDescription(context().getString(R.string.place_map_a11y, 150, testSuggestion().label))
            .assertIsDisplayed()
    }

    @Test
    fun detail_theMapCanBeZoomed_likeInTheForm() {
        show {
            PlaceDetailScreen(
                PlaceDetailState(
                    status = PlaceDetailStatus.READY,
                    place =
                        testPlace(
                            "p1",
                            "Maison de Papa",
                            radiusM = 150,
                            address = "26 rue du Volnay, 21000 Dijon, France",
                        ),
                    miffName = "Famille Martin",
                    creatorName = "Emmanuelle Martin",
                ),
                onBack = {},
                onEdit = {},
                onRetry = {},
            )
        }
        val spec = specs.last()
        assertTrue(spec.zoomable)
        // Same rules as the form: the map is given the place and its real radius, nothing else (no button, a described picture).
        assertEquals(150, spec.radiusM)
        composeRule
            .onNodeWithContentDescription(
                context().getString(R.string.place_map_a11y, 150, "26 rue du Volnay, 21000 Dijon, France"),
            ).assertIsDisplayed()
    }

    // --- The page and the map share the fingers --------------------------------------------------------------------------------

    /** A parent that notes what the box asks of it. */
    private class RecordingParent(
        context: Context,
    ) : FrameLayout(context) {
        val asked = mutableListOf<Boolean>()

        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            asked += disallowIntercept
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        }
    }

    private fun event(
        action: Int,
        pointers: Int = 1,
    ): MotionEvent {
        val properties =
            Array(pointers) { id ->
                MotionEvent.PointerProperties().apply {
                    this.id = id
                    toolType =
                        MotionEvent.TOOL_TYPE_FINGER
                }
            }
        val coords =
            Array(pointers) { id ->
                MotionEvent.PointerCoords().apply {
                    x = 10f + id * 20f
                    y = 10f
                }
            }
        return MotionEvent.obtain(0, 0, action, pointers, properties, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    @Test
    fun frame_oneFinger_leavesThePageFree_toScroll() {
        val parent = RecordingParent(context())
        val frame = PinchFriendlyFrame(context())
        parent.addView(frame, ViewGroup.LayoutParams(100, 100))
        frame.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN))
        frame.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE))
        assertTrue("one finger must never block the scrolling of the page", parent.asked.none { it })
    }

    @Test
    fun frame_aSecondFinger_stopsThePageFromTakingThePinch_andGivesItBackAtTheEnd() {
        val parent = RecordingParent(context())
        val frame = PinchFriendlyFrame(context())
        parent.addView(frame, ViewGroup.LayoutParams(100, 100))
        frame.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN))
        frame.dispatchTouchEvent(
            event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), pointers = 2),
        )
        assertEquals(listOf(true), parent.asked)
        frame.dispatchTouchEvent(event(MotionEvent.ACTION_UP))
        assertEquals(listOf(true, false), parent.asked)
    }

    @Test
    fun frame_aCancelledGesture_givesThePageItsHandBack() {
        val parent = RecordingParent(context())
        val frame: View = PinchFriendlyFrame(context())
        parent.addView(frame, ViewGroup.LayoutParams(100, 100))
        frame.dispatchTouchEvent(event(MotionEvent.ACTION_CANCEL))
        assertEquals(listOf(false), parent.asked)
    }
}
