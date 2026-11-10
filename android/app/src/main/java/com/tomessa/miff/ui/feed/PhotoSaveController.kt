package com.tomessa.miff.ui.feed

import android.net.Uri
import com.tomessa.miff.data.media.PhotoSaver
import com.tomessa.miff.domain.feed.PhotoDownload
import com.tomessa.miff.domain.feed.PhotoSaveOutcome
import com.tomessa.miff.domain.feed.PhotoSaveState
import com.tomessa.miff.domain.feed.SaveRoute
import com.tomessa.miff.domain.feed.after
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.ZoneId

/**
 * What "Enregistrer la photo" does, apart from the screen (testable on the JVM). Nothing here runs by itself: [saveToPictures] and [saveTo] are only
 * called on a touch of the button (or on the answer of the document picker that touch opened). The bytes are the ones of the opened photo and nothing
 * is kept afterwards.
 */
class PhotoSaveController(
    private val saver: PhotoSaver,
    private val sdkInt: Int,
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val mutableState = MutableStateFlow(PhotoSaveState.Idle)
    val state: StateFlow<PhotoSaveState> = mutableState

    val route: SaveRoute get() = PhotoDownload.routeFor(sdkInt)

    /** The neutral name of the file: the date of the save and nothing else. */
    fun fileName(): String = PhotoDownload.fileName(now(), zone())

    /** True when a save may start now (not while another one is under way). Marks the start. */
    fun begin(): Boolean {
        if (!mutableState.value.canStart) return false
        mutableState.value = PhotoSaveState.Saving
        return true
    }

    /** The photo is a real JPEG only. Android 10+ route. */
    suspend fun saveToPictures(jpeg: ByteArray) {
        finish(
            if (PhotoDownload.isJpeg(jpeg) &&
                saver.saveToPictures(fileName(), jpeg)
            ) {
                PhotoSaveOutcome.Saved
            } else {
                PhotoSaveOutcome.Failed
            },
        )
    }

    /** The answer of the document picker: [target] is null when the person closed it without choosing. */
    suspend fun saveTo(
        target: Uri?,
        jpeg: ByteArray,
    ) {
        if (target == null) {
            finish(PhotoSaveOutcome.Cancelled)
            return
        }
        finish(
            if (PhotoDownload.isJpeg(jpeg) &&
                saver.saveTo(target, jpeg)
            ) {
                PhotoSaveOutcome.Saved
            } else {
                PhotoSaveOutcome.Failed
            },
        )
    }

    private fun finish(outcome: PhotoSaveOutcome) {
        mutableState.value = mutableState.value.after(outcome)
    }
}
