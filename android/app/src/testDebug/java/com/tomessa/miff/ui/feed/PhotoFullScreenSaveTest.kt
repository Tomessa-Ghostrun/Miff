package com.tomessa.miff.ui.feed

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.domain.feed.PhotoDownload
import com.tomessa.miff.domain.update.SharedPhoto
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneOffset

/**
 * The button "Enregistrer la photo" of the photo opened in full screen (decision of the PM, 2026-11-08), on the JVM: it is there, with a spoken name and
 * a 48 dp target; nothing is saved before the touch; a touch saves once with a neutral name and says it calmly; a failure is calm too; a photo that is
 * not a real JPEG does not offer it; on Android 9 and older the document picker of the system is used (a closing without a choice writes nothing).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w360dp-h800dp-port-xxhdpi")
class PhotoFullScreenSaveTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-11-08T13:30:00Z").toEpochMilli()

    private fun realJpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.rgb(200, 120, 60))
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return out.toByteArray()
    }

    private fun opened(jpeg: ByteArray = realJpeg()) =
        OpenedPhoto(
            "Camille",
            SharedPhoto(
                jpeg,
                now - 600_000,
                now + 3_600_000,
                null,
            ),
        )

    private fun show(
        photo: OpenedPhoto,
        saver: FakePhotoSaver,
        sdkInt: Int = 35,
        registry: ActivityResultRegistry? = null,
    ) {
        composeRule.setContent {
            val owner =
                registry?.let { r ->
                    object : ActivityResultRegistryOwner {
                        override val activityResultRegistry: ActivityResultRegistry = r
                    }
                }
            MiffTheme {
                CompositionLocalProvider(
                    LocalPhotoSaver provides saver,
                    *(owner?.let { arrayOf(LocalActivityResultRegistryOwner provides it) } ?: emptyArray()),
                ) {
                    PhotoFullScreen(
                        photo,
                        FeedTexts(context, ZoneOffset.UTC),
                        now,
                        onOpenMap = { true },
                        onDismiss = {},
                        sdkInt = sdkInt,
                    )
                }
            }
        }
    }

    private fun button() =
        composeRule.onNodeWithContentDescription(context.getString(R.string.feed_photo_download_a11y))

    @Test
    fun theButtonIsThere_withASpokenName_andATargetOfAtLeast48dp_andNothingIsSavedBeforeTheTouch() {
        val saver = FakePhotoSaver()
        show(opened(), saver)
        button().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.feed_photo_download)).assertIsDisplayed()
        assertTrue(button().getUnclippedBoundsInRoot().let { it.bottom - it.top }.value >= 48f)
        assertTrue(saver.pictures.isEmpty() && saver.documents.isEmpty())
        composeRule.onAllNodesWithText(context.getString(R.string.feed_photo_download_done)).assertCountEquals(0)
    }

    @Test
    fun aTouch_savesOnce_withTheNeutralName_andSaysItCalmly() {
        val saver = FakePhotoSaver()
        val jpeg = realJpeg()
        show(opened(jpeg), saver)
        button().performClick()
        composeRule.waitForIdle()
        val name = saver.pictures.single().first
        assertTrue(name, Regex("Miff-\\d{4}-\\d{2}-\\d{2}-\\d{6}\\.jpg").matches(name))
        assertEquals(
            PhotoDownload.sanitize(jpeg)!!.toList(),
            saver.pictures
                .single()
                .second
                .toList(),
        )
        composeRule.onNodeWithText(context.getString(R.string.feed_photo_download_done)).assertIsDisplayed()
        // No first name in the name of the file.
        assertTrue(!name.contains("Camille"))
    }

    @Test
    fun aFailure_isCalm_andTheButtonStaysUsable() {
        val saver = FakePhotoSaver(succeeds = false)
        show(opened(), saver)
        button().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.feed_photo_download_failed)).assertIsDisplayed()
        assertEquals(1, saver.pictures.size)
        button().performClick()
        composeRule.waitForIdle()
        assertEquals(2, saver.pictures.size)
        listOf("erreur", "Erreur", "échec", "Impossible").forEach {
            composeRule.onAllNodesWithText(it, substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun aPhotoThatIsNotAJpeg_doesNotOfferTheButton() {
        val saver = FakePhotoSaver()
        show(opened("not a photo".toByteArray()), saver)
        composeRule
            .onAllNodesWithContentDescription(
                context.getString(R.string.feed_photo_download_a11y),
            ).assertCountEquals(0)
    }

    private fun registryAnswering(
        answer: Uri?,
        launched: MutableList<String>,
    ) = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            launched += input as String

            @Suppress("UNCHECKED_CAST")
            dispatchResult(requestCode, answer as O)
        }
    }

    @Test
    fun onAndroid9AndOlder_thePickerOfTheSystemIsUsed_andTheChosenDocumentReceivesThePhoto() {
        val saver = FakePhotoSaver()
        val launched = mutableListOf<String>()
        val chosen = Uri.parse("content://documents/photo")
        show(opened(), saver, sdkInt = 28, registry = registryAnswering(chosen, launched))
        button().performClick()
        composeRule.waitForIdle()
        assertTrue(launched.single().startsWith("Miff-") && launched.single().endsWith(".jpg"))
        assertEquals(chosen, saver.documents.single().first)
        assertTrue(saver.pictures.isEmpty())
        composeRule.onNodeWithText(context.getString(R.string.feed_photo_download_done)).assertIsDisplayed()
    }

    @Test
    fun onAndroid9AndOlder_closingThePickerWithoutAChoice_writesNothing_andSaysNothing() {
        val saver = FakePhotoSaver()
        val launched = mutableListOf<String>()
        show(opened(), saver, sdkInt = 26, registry = registryAnswering(null, launched))
        button().performClick()
        composeRule.waitForIdle()
        assertEquals(1, launched.size)
        assertTrue(saver.documents.isEmpty() && saver.pictures.isEmpty())
        composeRule.onAllNodesWithText(context.getString(R.string.feed_photo_download_done)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.feed_photo_download_failed)).assertCountEquals(0)
        button().assertIsDisplayed()
    }
}
