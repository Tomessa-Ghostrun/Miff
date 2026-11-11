package com.tomessa.miff.domain.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** "Enregistrer la photo" of the full-screen photo (decision of the PM, 2026-11-08): the name, the route, the checks and the states of the button. */
class PhotoDownloadTest {
    @Test
    fun fileName_isNeutral_theDateOfTheSaveAndNothingElse() {
        val name = PhotoDownload.fileName(Instant.parse("2026-11-08T13:32:05Z"), ZoneId.of("UTC"))
        assertEquals("Miff-2026-11-08-133205.jpg", name)
    }

    @Test
    fun fileName_followsTheTimeZoneOfThePhone() {
        val name = PhotoDownload.fileName(Instant.parse("2026-11-08T23:30:00Z"), ZoneId.of("Europe/Paris"))
        assertEquals("Miff-2026-11-09-003000.jpg", name)
    }

    @Test
    fun fileName_takesNothingFromThePhotoOrTheMiff_andIsAlwaysSafeToWrite() {
        // The function only receives a moment and a zone: no first name, no place, no position can reach it. Whatever they are, the shape is fixed.
        val shape = Regex("""Miff-\d{4}-\d{2}-\d{2}-\d{6}\.jpg""")
        listOf("UTC", "Asia/Tokyo", "America/Los_Angeles", "Pacific/Kiritimati").forEach {
            assertTrue(shape.matches(PhotoDownload.fileName(Instant.parse("2026-01-02T03:04:05Z"), ZoneId.of(it))))
        }
    }

    @Test
    fun route_isMediaStoreFromAndroid10_andThePickerBefore() {
        assertEquals(SaveRoute.DOCUMENT_PICKER, PhotoDownload.routeFor(26))
        assertEquals(SaveRoute.DOCUMENT_PICKER, PhotoDownload.routeFor(28))
        assertEquals(SaveRoute.MEDIA_STORE, PhotoDownload.routeFor(29))
        assertEquals(SaveRoute.MEDIA_STORE, PhotoDownload.routeFor(36))
    }

    @Test
    fun isJpeg_acceptsJpegBytes_andRefusesTheRest() {
        assertTrue(PhotoDownload.isJpeg(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
        assertFalse(PhotoDownload.isJpeg(byteArrayOf()))
        assertFalse(PhotoDownload.isJpeg(byteArrayOf(0xFF.toByte(), 0xD8.toByte())))
        // PNG
        assertFalse(PhotoDownload.isJpeg(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D)))
        // HTML or a script dressed as a photo
        assertFalse(PhotoDownload.isJpeg("<html>".toByteArray()))
    }

    @Test
    fun state_aSecondTouchWhileSavingStartsNothing() {
        assertTrue(PhotoSaveState.Idle.canStart)
        assertTrue(PhotoSaveState.Saved.canStart)
        assertTrue(PhotoSaveState.Failed.canStart)
        assertFalse(PhotoSaveState.Saving.canStart)
    }

    @Test
    fun state_afterTheOutcome() {
        assertEquals(PhotoSaveState.Saved, PhotoSaveState.Saving.after(PhotoSaveOutcome.Saved))
        assertEquals(PhotoSaveState.Failed, PhotoSaveState.Saving.after(PhotoSaveOutcome.Failed))
        // Closing the picker without choosing is a choice, not an error: back to the start, nothing is said.
        assertEquals(PhotoSaveState.Idle, PhotoSaveState.Saving.after(PhotoSaveOutcome.Cancelled))
    }
}
