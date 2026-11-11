package com.tomessa.miff.testsupport

import java.io.ByteArrayOutputStream

/**
 * Small hand-made JPEG files for the tests of "Enregistrer la photo": the structure (segments, a scan with stuffed bytes) is real, the picture is not,
 * so they are built byte by byte and can carry metadata on purpose (EXIF with a GPS text, a comment) or junk after the end of the image.
 */
object TestJpegs {
    val EXIF_GPS_TEXT = "Exif--GPS 48.8566N 2.3522E--Camille"
    val COMMENT_TEXT = "photo of Camille at home"

    fun segment(
        marker: Int,
        payload: ByteArray,
    ): ByteArray {
        val length = payload.size + 2
        return byteArrayOf(0xFF.toByte(), marker.toByte(), (length shr 8).toByte(), length.toByte()) + payload
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    val SOI = bytes(0xFF, 0xD8)
    val EOI = bytes(0xFF, 0xD9)

    private val jfif = segment(0xE0, "JFIF".toByteArray() + bytes(0, 1, 1, 0, 0, 1, 0, 1, 0, 0))

    /** An EXIF block whose thumbnail contains what looks like a whole JPEG, end marker included: it must not end the image early. */
    private val exif = segment(0xE1, ("Exif".toByteArray() + bytes(0, 0) + EXIF_GPS_TEXT.toByteArray() + SOI + EOI))
    private val xmp = segment(0xE1, "http://ns.adobe.com/xap/1.0/ <x:xmpmeta/>".toByteArray())
    private val icc = segment(0xE2, "ICC_PROFILE".toByteArray() + bytes(0, 1, 1, 9, 9, 9))
    private val comment = segment(0xFE, COMMENT_TEXT.toByteArray())

    private val dqt = segment(0xDB, bytes(0) + ByteArray(64) { (it + 1).toByte() })
    private val sof = segment(0xC0, bytes(8, 0, 4, 0, 4, 1, 1, 0x11, 0))
    private val dht = segment(0xC4, bytes(0) + ByteArray(16) { if (it == 0) 1 else 0 } + bytes(0))
    private val sos = segment(0xDA, bytes(1, 1, 0, 0, 0x3F, 0))

    /** Entropy-coded data: a stuffed byte (FF 00) and a restart marker (FF D0) belong to the scan and must survive. */
    val SCAN_DATA = bytes(0x12, 0xFF, 0x00, 0x34, 0xFF, 0xD0, 0x56, 0x78)

    /** What a JPEG with no metadata looks like: the expected result of cleaning any of the others. */
    fun clean(): ByteArray = build(withMetadata = false, trailing = ByteArray(0))

    fun build(
        withMetadata: Boolean,
        trailing: ByteArray,
        progressive: Boolean = false,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(SOI)
        if (withMetadata) {
            out.write(jfif)
            out.write(exif)
            out.write(xmp)
            out.write(icc)
            out.write(comment)
        }
        out.write(dqt)
        out.write(if (progressive) segment(0xC2, bytes(8, 0, 4, 0, 4, 1, 1, 0x11, 0)) else sof)
        out.write(dht)
        out.write(sos)
        out.write(SCAN_DATA)
        if (progressive) {
            if (withMetadata) out.write(comment)
            out.write(dht)
            out.write(sos)
            out.write(bytes(0x9A, 0xBC))
        }
        out.write(EOI)
        out.write(trailing)
        return out.toByteArray()
    }

    /** The same photo with a lot of metadata and a GPS text after the image. */
    fun dirty(progressive: Boolean = false): ByteArray =
        build(withMetadata = true, trailing = "TRAILER-GPS 48.85 2.35".toByteArray(), progressive)
}
