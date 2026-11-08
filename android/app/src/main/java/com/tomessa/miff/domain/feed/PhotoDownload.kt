package com.tomessa.miff.domain.feed

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "Enregistrer la photo" in the full-screen photo of the Feed (decision of the PM, 2026-11-08; THERE only: not on the card, not in the Update).
 *
 * PRIVACY, for this file: the photo is saved ONLY on a deliberate touch of the button, never by itself; the file name is neutral (the date of the
 * save, nothing else: no first name, no place, no position, nothing taken from the photo or from the Miff); the bytes are the ones of the photo, already
 * written from the pixels without EXIF by the sender's app. It is a CHANGE of posture: until now a photo never left the app (except by a screenshot);
 * from now on a member can keep it on the phone, outside Miff, where the 24 hours no longer apply.
 */
object PhotoDownload {
    /** The folder of the pictures where the photo goes on Android 10 and later (MediaStore, no permission). */
    const val ALBUM = "Miff"
    const val MIME_TYPE = "image/jpeg"

    private val stamp: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss", Locale.ROOT)

    /** `Miff-2026-11-08-143205.jpg`: the moment of the save, in the phone's time zone. Nothing else goes into a name. */
    fun fileName(
        now: Instant,
        zone: ZoneId,
    ): String = "Miff-" + stamp.format(now.atZone(zone)) + ".jpg"

    /** Only a real JPEG is offered for saving (the bytes were written by another member's phone: they are checked, not trusted). */
    fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size > 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()

    /**
     * Android 10 (API 29) and later write into the shared pictures through MediaStore without any permission. Before that, a file can only be
     * written without permission through the document picker of the system (the person chooses where). No storage permission is ever declared.
     */
    fun routeFor(sdkInt: Int): SaveRoute = if (sdkInt >= 29) SaveRoute.MEDIA_STORE else SaveRoute.DOCUMENT_PICKER
}

enum class SaveRoute { MEDIA_STORE, DOCUMENT_PICKER }

/** What the button shows. [Saved] and [Failed] are calm sentences, never an alarm; the person can touch the button again. */
enum class PhotoSaveState {
    Idle,
    Saving,
    Saved,
    Failed,
    ;

    /** A second touch while a save is under way does nothing (no two files for one tap). */
    val canStart: Boolean get() = this != Saving
}

enum class PhotoSaveOutcome { Saved, Failed, Cancelled }

/** The state after a save ends. Closing the document picker without choosing ([PhotoSaveOutcome.Cancelled]) is a choice, not an error: nothing is said. */
fun PhotoSaveState.after(outcome: PhotoSaveOutcome): PhotoSaveState =
    when (outcome) {
        PhotoSaveOutcome.Saved -> PhotoSaveState.Saved
        PhotoSaveOutcome.Failed -> PhotoSaveState.Failed
        PhotoSaveOutcome.Cancelled -> PhotoSaveState.Idle
    }
