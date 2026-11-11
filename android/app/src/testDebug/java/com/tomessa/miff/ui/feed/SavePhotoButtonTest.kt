package com.tomessa.miff.ui.feed

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.domain.feed.PhotoDownload
import com.tomessa.miff.domain.feed.PhotoSaveState
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** The button "Enregistrer la photo" while it saves (announced "Enregistrement…", disabled), and the cleaning of a REAL JPEG that still decodes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w360dp-h800dp-port-xxhdpi")
class SavePhotoButtonTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun show(state: PhotoSaveState) {
        composeRule.setContent { MiffTheme { SavePhotoButton(state = state, onClick = {}) } }
    }

    private fun button() =
        composeRule.onNodeWithContentDescription(context.getString(R.string.feed_photo_download_a11y))

    @Test
    fun whileSaving_theButtonIsDisabled_andTalkBackSaysSaving() {
        show(PhotoSaveState.Saving)
        button().assertIsNotEnabled()
        val state = button().fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertEquals(context.getString(R.string.feed_photo_download_saving), state)
    }

    @Test
    fun otherwise_theButtonIsEnabled_withNoStateDescription() {
        show(PhotoSaveState.Idle)
        button().assertIsEnabled()
        assertFalse(button().fetchSemanticsNode().config.contains(SemanticsProperties.StateDescription))
    }

    @Test
    fun aRealJpeg_withAnExifBlockInjected_isCleaned_andStillDecodes() {
        val bitmap = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.rgb(30, 120, 200))
        val plain = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        // An EXIF block with a GPS text, inserted right after the start of image, and junk after the end.
        val payload = "Exif".toByteArray() + byteArrayOf(0, 0) + "GPS 48.8566N 2.3522E".toByteArray()
        val length = payload.size + 2
        val exif = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
        val dirty = plain.copyOfRange(0, 2) + exif + plain.copyOfRange(2, plain.size) + "TRAILER".toByteArray()
        assertTrue(String(dirty, Charsets.ISO_8859_1).contains("GPS 48.8566N"))

        val clean = PhotoDownload.sanitize(dirty)
        assertNotNull(clean)
        val text = String(clean!!, Charsets.ISO_8859_1)
        assertFalse(text.contains("GPS"))
        assertFalse(text.contains("Exif"))
        assertFalse(text.contains("TRAILER"))
        val decoded = BitmapFactory.decodeByteArray(clean, 0, clean.size)
        assertNotNull(decoded)
        assertEquals(48, decoded.width)
        assertEquals(32, decoded.height)
    }

    @Test
    fun theButton_isAnIconOnly_of48dp() {
        show(PhotoSaveState.Idle)
        composeRule.onAllNodesWithText(context.getString(R.string.feed_photo_download)).assertCountEquals(0)
        val bounds = button().getUnclippedBoundsInRoot()
        assertTrue(bounds.right.value - bounds.left.value >= 48f)
        assertTrue(bounds.bottom.value - bounds.top.value >= 48f)
    }

    @Test
    fun theCalmNote_saysSavedOrCouldNotSave_thenGoesAwayByItself() {
        var seen = 0
        var state by mutableStateOf(PhotoSaveState.Saved)
        composeRule.setContent {
            MiffTheme {
                CompositionLocalProvider(LocalSaveNoteMillis provides 1_000L) {
                    SaveResultNote(state = state, onSeen = { seen++ })
                }
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.feed_photo_download_done)).assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(1_500)
        assertEquals(1, seen)
        state = PhotoSaveState.Failed
        composeRule.onNodeWithText(context.getString(R.string.feed_photo_download_failed)).assertIsDisplayed()
        state = PhotoSaveState.Idle
        composeRule.onAllNodesWithText(context.getString(R.string.feed_photo_download_failed)).assertCountEquals(0)
    }
}
