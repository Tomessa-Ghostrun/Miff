package com.tomessa.miff.domain.feed

import com.tomessa.miff.testsupport.TestJpegs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Enregistrer la photo": the bytes written on the phone are cleaned HERE, so the promise "no metadata" holds even if the sender used a modified app
 * (decision of the PM, 2026-11-08, review of the PR): every APPn and COM segment is dropped, everything after the end of the image is cut, the rest is
 * copied byte for byte (no re-encoding), and bytes that are not a well-formed JPEG are refused.
 */
class PhotoSanitizeTest {
    private fun String.bytesOf() = toByteArray()

    private fun ByteArray.contains(text: String): Boolean = String(this, Charsets.ISO_8859_1).contains(text)

    @Test
    fun aCleanJpeg_comesOutUnchanged() {
        val clean = TestJpegs.clean()
        assertArrayEquals(clean, PhotoDownload.sanitize(clean))
    }

    @Test
    fun exifXmpIccJfifAndComments_areAllDropped_theRestIsKeptByteForByte() {
        val out = PhotoDownload.sanitize(TestJpegs.dirty())
        assertNotNull(out)
        assertArrayEquals(TestJpegs.clean(), out)
        assertFalse(out!!.contains("Exif"))
        assertFalse(out.contains("GPS"))
        assertFalse(out.contains("Camille"))
        assertFalse(out.contains("xmpmeta"))
        assertFalse(out.contains("ICC_PROFILE"))
        assertFalse(out.contains("JFIF"))
    }

    @Test
    fun whatFollowsTheEndOfTheImage_isCut() {
        val out =
            PhotoDownload.sanitize(
                TestJpegs.build(withMetadata = false, trailing = "TRAILER-GPS 48.85 2.35".bytesOf()),
            )!!
        assertArrayEquals(TestJpegs.clean(), out)
        assertFalse(out.contains("TRAILER"))
    }

    @Test
    fun aThumbnailInsideTheExif_whichLooksLikeAWholeJpeg_doesNotEndTheImageEarly() {
        // The EXIF of TestJpegs.dirty() carries SOI + EOI bytes inside: the image after it must still be complete.
        val out = PhotoDownload.sanitize(TestJpegs.dirty())!!
        assertTrue(out.contains(String(TestJpegs.SCAN_DATA, Charsets.ISO_8859_1)))
    }

    @Test
    fun theScan_keepsItsStuffedBytesAndRestartMarkers() {
        val out = PhotoDownload.sanitize(TestJpegs.dirty())!!
        assertTrue(out.contains(String(TestJpegs.SCAN_DATA, Charsets.ISO_8859_1)))
        assertEquals(0xD9, out.last().toInt() and 0xFF)
    }

    @Test
    fun aProgressiveJpeg_withSeveralScans_keepsAllOfThem() {
        val out = PhotoDownload.sanitize(TestJpegs.dirty(progressive = true))
        assertArrayEquals(TestJpegs.build(withMetadata = false, trailing = ByteArray(0), progressive = true), out)
    }

    @Test
    fun notAJpeg_orBroken_isRefused() {
        assertNull(PhotoDownload.sanitize(ByteArray(0)))
        assertNull(PhotoDownload.sanitize("<html>not a photo</html>".bytesOf()))
        assertNull(PhotoDownload.sanitize(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)))
        // Start of image and nothing else.
        assertNull(PhotoDownload.sanitize(TestJpegs.SOI + byteArrayOf(0xFF.toByte())))
    }

    @Test
    fun aTruncatedJpeg_withoutItsEnd_isRefused() {
        val whole = TestJpegs.clean()
        assertNull(PhotoDownload.sanitize(whole.copyOfRange(0, whole.size - 2)))
        assertNull(PhotoDownload.sanitize(whole.copyOfRange(0, whole.size - 12)))
    }

    @Test
    fun aSegmentThatRunsPastTheEnd_orWithAnImpossibleLength_isRefused() {
        val tooLong = TestJpegs.SOI + byteArrayOf(0xFF.toByte(), 0xE1.toByte(), 0xFF.toByte(), 0xFF.toByte(), 1, 2, 3)
        assertNull(PhotoDownload.sanitize(tooLong))
        val tooShort = TestJpegs.SOI + byteArrayOf(0xFF.toByte(), 0xE1.toByte(), 0, 1, 1, 2)
        assertNull(PhotoDownload.sanitize(tooShort))
    }

    @Test
    fun withoutAFrameOrWithoutAScan_isRefused() {
        // Only metadata and an end marker: nothing to see.
        assertNull(PhotoDownload.sanitize(TestJpegs.SOI + TestJpegs.segment(0xE1, "Exif".bytesOf()) + TestJpegs.EOI))
        // A scan before any frame.
        val scanFirst =
            TestJpegs.SOI + TestJpegs.segment(0xDA, byteArrayOf(1, 1, 0, 0, 0x3F, 0)) + byteArrayOf(1, 2) +
                TestJpegs.EOI
        assertNull(PhotoDownload.sanitize(scanFirst))
    }

    @Test
    fun theInputIsNeverModified() {
        val input = TestJpegs.dirty()
        val copy = input.copyOf()
        PhotoDownload.sanitize(input)
        assertArrayEquals(copy, input)
    }
}
