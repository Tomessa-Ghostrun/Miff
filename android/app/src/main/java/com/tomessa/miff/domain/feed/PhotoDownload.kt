package com.tomessa.miff.domain.feed

import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "Enregistrer la photo" in the full-screen photo of the Feed (decision of the PM, 2026-11-08; THERE only: not on the card, not in the Update).
 *
 * PRIVACY, for this file: the photo is saved ONLY on a deliberate touch of the button, never by itself; the file name is neutral (the date of the
 * save, nothing else: no first name, no place, no position, nothing taken from the photo or from the Miff); the bytes are those of the photo, already
 * written from the pixels without EXIF by the sender's app, and CLEANED again here by [sanitize] in case that app was modified. It is a CHANGE of
 * posture: until now a photo never left the app (except by a screenshot); from now on a member can keep it on the phone, outside Miff, where the 24 hours no longer apply.
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
     * The bytes that are written on the phone: the JPEG WITHOUT any metadata, whatever the sender's app did. The app of the sender writes a bare JPEG
     * (no EXIF), but another member's app could be modified, so the promise "no metadata" is kept HERE and not trusted: every APPn segment (EXIF with
     * its GPS, XMP, ICC, thumbnails, JFIF) and every comment (COM) is dropped, everything after the end of the image (EOI) is cut, the rest (tables,
     * frame, scans) is copied byte for byte, with NO re-encoding (no loss of quality). Returns null when the bytes are not a well-formed JPEG: a
     * frame (SOF), a scan (SOS) and a closing EOI are required, and a segment that runs past the end refuses everything.
     */
    fun sanitize(input: ByteArray): ByteArray? {
        if (!isJpeg(input)) return null
        val n = input.size
        val out = ByteArrayOutputStream(n)
        out.write(0xFF)
        out.write(0xD8)
        var i = 2
        var sawFrame = false
        var sawScan = false
        while (i < n) {
            if (input[i].u() != 0xFF) return null
            while (i < n && input[i].u() == 0xFF) i++
            if (i >= n) return null
            val marker = input[i].u()
            i++
            when {
                marker == 0xD9 -> {
                    if (!sawScan) return null
                    out.write(0xFF)
                    out.write(0xD9)
                    return out.toByteArray()
                }

                marker == 0x00 || marker == 0xD8 -> {
                    return null
                }

                marker == 0x01 || marker in 0xD0..0xD7 -> {
                    out.write(0xFF)
                    out.write(marker)
                }

                else -> {
                    if (i + 2 > n) return null
                    val length = (input[i].u() shl 8) or input[i + 1].u()
                    if (length < 2 || i + length > n) return null
                    val metadata = marker in 0xE0..0xEF || marker == 0xFE
                    if (!metadata) {
                        out.write(0xFF)
                        out.write(marker)
                        out.write(input, i, length)
                    }
                    if (isFrame(marker)) sawFrame = true
                    i += length
                    if (marker == 0xDA) {
                        if (!sawFrame) return null
                        sawScan = true
                        // The entropy-coded data of the scan: copied as is, up to the next real marker (FF00 is a stuffed byte, FFD0..D7 a restart).
                        val start = i
                        while (i < n) {
                            if (input[i].u() == 0xFF) {
                                val next = if (i + 1 < n) input[i + 1].u() else -1
                                if (next == 0x00 || next in 0xD0..0xD7) {
                                    i += 2
                                    continue
                                }
                                if (next == 0xFF) {
                                    i++
                                    continue
                                }
                                break
                            }
                            i++
                        }
                        out.write(input, start, i - start)
                    }
                }
            }
        }
        // The end of the data came before any EOI: truncated.
        return null
    }

    private fun Byte.u(): Int = toInt() and 0xFF

    /** SOF0 to SOF15 except DHT (C4), JPG (C8) and DAC (CC). */
    private fun isFrame(marker: Int): Boolean =
        marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC

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
