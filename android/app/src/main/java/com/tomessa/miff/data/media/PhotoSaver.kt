package com.tomessa.miff.data.media

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.tomessa.miff.domain.feed.PhotoDownload
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes the photo of the full-screen Feed on the phone, on a deliberate touch ("Enregistrer la photo"). NO permission: MediaStore (Android 10+) and the
 * document picker of the system need none, and none is declared (the CI checks the merged manifests). Nothing is read from the phone, nothing is
 * written anywhere else, and nothing about the sender goes into the file name or the database of the pictures (see [PhotoDownload]).
 */
interface PhotoSaver {
    /** Android 10+: adds [name] to Pictures/Miff. True when the file is complete and visible; on any failure nothing is left behind. */
    suspend fun saveToPictures(
        name: String,
        jpeg: ByteArray,
    ): Boolean

    /** Any version: writes into the document the person just created with the system picker. True when the bytes were written. */
    suspend fun saveTo(
        target: Uri,
        jpeg: ByteArray,
    ): Boolean
}

class ContentPhotoSaver(
    private val resolver: ContentResolver,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : PhotoSaver {
    override suspend fun saveToPictures(
        name: String,
        jpeg: ByteArray,
    ): Boolean =
        withContext(io) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false
            insert(name, jpeg)
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun insert(
        name: String,
        jpeg: ByteArray,
    ): Boolean {
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, PhotoDownload.MIME_TYPE)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/${PhotoDownload.ALBUM}")
                // The file stays invisible to the other apps until it is complete.
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            val written =
                resolver.openOutputStream(uri, "w")?.use {
                    it.write(jpeg)
                    true
                } ?: false
            if (written) {
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            } else {
                resolver.delete(uri, null, null)
            }
            written
        } catch (_: Exception) {
            // A half-written file is never left in the pictures of the person.
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }

    override suspend fun saveTo(
        target: Uri,
        jpeg: ByteArray,
    ): Boolean =
        withContext(io) {
            try {
                resolver.openOutputStream(target, "w")?.use {
                    it.write(jpeg)
                    true
                } ?: false
            } catch (_: Exception) {
                false
            }
        }
}
