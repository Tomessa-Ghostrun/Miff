package com.tomessa.miff.data.media

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.FileNotFoundException

/**
 * The writing of the photo on the phone ("Enregistrer la photo", decision of the PM, 2026-11-08): the row added to the pictures carries ONLY a neutral
 * name, the type, the folder "Pictures/Miff" and the pending flag (nothing about the sender, a place or a position), the file is complete before it
 * shows, and a failure leaves nothing behind. A fake "media" provider records what the phone would be asked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ContentPhotoSaverTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class FakeMedia(
        private val file: File,
        private val failOpen: Boolean = false,
        private val updateCount: Int = 1,
    ) : ContentProvider() {
        val inserted = mutableListOf<ContentValues>()
        val updated = mutableListOf<ContentValues>()
        var deleted = 0

        override fun onCreate() = true

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri {
            inserted += ContentValues(values)
            return Uri.parse("content://media/external/images/media/42")
        }

        override fun openFile(
            uri: Uri,
            mode: String,
        ): ParcelFileDescriptor {
            if (failOpen) throw FileNotFoundException("no room")
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
        }

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int {
            updated += ContentValues(values)
            return updateCount
        }

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int {
            deleted++
            return 1
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null

        override fun getType(uri: Uri): String? = null

        val calls = mutableListOf<String>()

        override fun call(
            method: String,
            arg: String?,
            extras: Bundle?,
        ): Bundle? {
            calls += method
            return Bundle.EMPTY
        }
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var target: File
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)

    @Before
    fun setUp() {
        target = folder.newFile("photo.jpg")
    }

    private fun register(media: FakeMedia) {
        // The provider must be attached (a context and an authority), as the system does, or its transport refuses every call.
        media.attachInfo(
            context,
            ProviderInfo().apply {
                authority = "media"
                exported = true
            },
        )
        ShadowContentResolver.registerProviderInternal("media", media)
    }

    @Test
    fun saveToPictures_writesTheBytes_withANeutralRow_thenMakesItVisible() =
        runTest {
            val media = FakeMedia(target)
            register(media)
            val saved =
                ContentPhotoSaver(
                    context.contentResolver,
                    Dispatchers.Unconfined,
                ).saveToPictures("Miff-2026-11-08-133205.jpg", jpeg)
            assertTrue(saved)
            assertArrayEquals(jpeg, target.readBytes())
            val row = media.inserted.single()
            assertEquals("Miff-2026-11-08-133205.jpg", row.getAsString(MediaStore.Images.Media.DISPLAY_NAME))
            assertEquals("image/jpeg", row.getAsString(MediaStore.Images.Media.MIME_TYPE))
            assertEquals("Pictures/Miff", row.getAsString(MediaStore.Images.Media.RELATIVE_PATH))
            assertEquals(1, row.getAsInteger(MediaStore.Images.Media.IS_PENDING))
            // NOTHING else about the photo: no title, description, place or date taken.
            assertEquals(
                setOf("_display_name", "mime_type", "relative_path", "is_pending"),
                row.keySet(),
            )
            assertEquals(0, media.updated.single().getAsInteger(MediaStore.Images.Media.IS_PENDING))
            assertEquals(0, media.deleted)
        }

    @Test
    fun saveToPictures_whenTheFileCannotBeOpened_leavesNothingBehind_andSaysNo() =
        runTest {
            val media = FakeMedia(target, failOpen = true)
            register(media)
            val saved =
                ContentPhotoSaver(
                    context.contentResolver,
                    Dispatchers.Unconfined,
                ).saveToPictures("Miff-x.jpg", jpeg)
            assertFalse(saved)
            assertEquals(1, media.deleted)
            assertTrue("the row never becomes visible", media.updated.isEmpty())
        }

    @Test
    fun saveTo_writesIntoTheDocumentThePersonChose() =
        runTest {
            val media = FakeMedia(target)
            register(media)
            val saved =
                ContentPhotoSaver(
                    context.contentResolver,
                    Dispatchers.Unconfined,
                ).saveTo(Uri.parse("content://media/doc/1"), jpeg)
            assertTrue(saved)
            assertArrayEquals(jpeg, target.readBytes())
        }

    @Test
    fun saveTo_whenTheDocumentCannotBeWritten_saysNo_withoutCrashing() =
        runTest {
            register(FakeMedia(target, failOpen = true))
            val saved =
                ContentPhotoSaver(
                    context.contentResolver,
                    Dispatchers.Unconfined,
                ).saveTo(Uri.parse("content://media/doc/1"), jpeg)
            assertFalse(saved)
        }

    @Test
    fun saveToPictures_whenTheSystemUpdatesNoRow_isAFailure_andTheRowIsRemoved() =
        runTest {
            val media = FakeMedia(target, updateCount = 0)
            register(media)
            val saved =
                ContentPhotoSaver(
                    context.contentResolver,
                    Dispatchers.Unconfined,
                ).saveToPictures("Miff-x.jpg", jpeg)
            assertFalse("a photo that never becomes visible is not a success", saved)
            assertEquals("the invisible row is removed", 1, media.deleted)
        }

    @Test
    fun saveTo_whenTheWriteFails_triesToRemoveTheEmptyDocumentTheSystemCreated() =
        runTest {
            val media = FakeMedia(target, failOpen = true)
            register(media)
            val saved =
                ContentPhotoSaver(
                    context.contentResolver,
                    Dispatchers.Unconfined,
                ).saveTo(Uri.parse("content://media/document/1"), jpeg)
            assertFalse(saved)
            assertTrue(
                "deleteDocument was asked: ${media.calls}",
                media.calls.any {
                    it.contains("deleteDocument", ignoreCase = true)
                },
            )
        }

    @Test
    fun saveTo_whenTheWriteSucceeds_removesNothing() =
        runTest {
            val media = FakeMedia(target)
            register(media)
            assertTrue(
                ContentPhotoSaver(
                    context.contentResolver,
                    Dispatchers.Unconfined,
                ).saveTo(Uri.parse("content://media/document/1"), jpeg),
            )
            assertTrue(media.calls.isEmpty())
        }
}
