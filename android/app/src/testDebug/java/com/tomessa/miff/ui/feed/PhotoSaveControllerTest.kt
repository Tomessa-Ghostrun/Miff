package com.tomessa.miff.ui.feed

import android.app.Application
import android.net.Uri
import com.tomessa.miff.data.feed.PhotoSaver
import com.tomessa.miff.domain.feed.PhotoSaveState
import com.tomessa.miff.domain.feed.SaveRoute
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

/** A recording [PhotoSaver] for the tests of "Enregistrer la photo". */
class FakePhotoSaver(
    var succeeds: Boolean = true,
) : PhotoSaver {
    val pictures = mutableListOf<Pair<String, ByteArray>>()
    val documents = mutableListOf<Pair<Uri, ByteArray>>()

    override suspend fun saveToPictures(
        name: String,
        jpeg: ByteArray,
    ): Boolean {
        pictures += name to jpeg
        return succeeds
    }

    override suspend fun saveTo(
        target: Uri,
        jpeg: ByteArray,
    ): Boolean {
        documents += target to jpeg
        return succeeds
    }
}

internal val TEST_JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 7, 7, 7)

/** What the button does apart from the screen: the route, the neutral name, the states, the checks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PhotoSaveControllerTest {
    private val moment = Instant.parse("2026-11-08T13:32:05Z")

    private fun controller(
        saver: PhotoSaver,
        sdk: Int = 35,
    ) = PhotoSaveController(saver, sdk, now = { moment }, zone = { ZoneId.of("UTC") })

    @Test
    fun nothingIsSaved_untilTheTouch() {
        val saver = FakePhotoSaver()
        val controller = controller(saver)
        assertEquals(PhotoSaveState.Idle, controller.state.value)
        assertTrue(saver.pictures.isEmpty() && saver.documents.isEmpty())
    }

    @Test
    fun theRouteFollowsTheVersionOfAndroid() {
        assertEquals(SaveRoute.MEDIA_STORE, controller(FakePhotoSaver(), sdk = 33).route)
        assertEquals(SaveRoute.DOCUMENT_PICKER, controller(FakePhotoSaver(), sdk = 28).route)
    }

    @Test
    fun saveToPictures_savesTheBytesWithTheNeutralName_andSaysSaved() =
        runTest {
            val saver = FakePhotoSaver()
            val controller = controller(saver)
            assertTrue(controller.begin())
            assertEquals(PhotoSaveState.Saving, controller.state.value)
            controller.saveToPictures(TEST_JPEG)
            assertEquals(PhotoSaveState.Saved, controller.state.value)
            assertEquals("Miff-2026-11-08-133205.jpg", saver.pictures.single().first)
            assertEquals(
                TEST_JPEG.toList(),
                saver.pictures
                    .single()
                    .second
                    .toList(),
            )
        }

    @Test
    fun aFailure_isSaidCalmly_andTheButtonCanBeUsedAgain() =
        runTest {
            val saver = FakePhotoSaver(succeeds = false)
            val controller = controller(saver)
            controller.begin()
            controller.saveToPictures(TEST_JPEG)
            assertEquals(PhotoSaveState.Failed, controller.state.value)
            assertTrue(controller.begin())
        }

    @Test
    fun aSecondTouchWhileSaving_startsNothing() {
        val controller = controller(FakePhotoSaver())
        assertTrue(controller.begin())
        assertFalse(controller.begin())
    }

    @Test
    fun somethingThatIsNotAJpeg_isNeverWritten() =
        runTest {
            val saver = FakePhotoSaver()
            val controller = controller(saver)
            controller.begin()
            controller.saveToPictures("<html>".toByteArray())
            assertEquals(PhotoSaveState.Failed, controller.state.value)
            assertTrue(saver.pictures.isEmpty())
            controller.begin()
            controller.saveTo(Uri.parse("content://doc/1"), "<html>".toByteArray())
            assertEquals(PhotoSaveState.Failed, controller.state.value)
            assertTrue(saver.documents.isEmpty())
        }

    @Test
    fun thePicker_answeredWithADocument_writesIntoIt() =
        runTest {
            val saver = FakePhotoSaver()
            val controller = controller(saver, sdk = 28)
            controller.begin()
            val target = Uri.parse("content://doc/1")
            controller.saveTo(target, TEST_JPEG)
            assertEquals(PhotoSaveState.Saved, controller.state.value)
            assertEquals(target, saver.documents.single().first)
        }

    @Test
    fun thePicker_closedWithoutAChoice_isNotAnError_andWritesNothing() =
        runTest {
            val saver = FakePhotoSaver()
            val controller = controller(saver, sdk = 28)
            controller.begin()
            controller.saveTo(null, TEST_JPEG)
            assertEquals(PhotoSaveState.Idle, controller.state.value)
            assertTrue(saver.documents.isEmpty())
        }
}
