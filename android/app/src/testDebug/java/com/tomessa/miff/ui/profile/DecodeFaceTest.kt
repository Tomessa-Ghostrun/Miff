package com.tomessa.miff.ui.profile

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/**
 * The photo of a member comes from ANOTHER account: the rules bound its size in bytes, not its dimensions. A photo that cannot be used (corrupted, or
 * announcing a gigantic image in a few bytes) is never decoded and never crashes the screen: the initials are drawn instead.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class DecodeFaceTest {
    private fun jpeg(side: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    }

    /** A JPEG header that announces [side] x [side] pixels, followed by nothing: a few bytes that claim a gigantic image. */
    private fun announcing(side: Int): ByteArray {
        val high = (side shr 8).toByte()
        val low = (side and 0xFF).toByte()
        return byteArrayOf(
            0xFF.toByte(),
            0xD8.toByte(),
            0xFF.toByte(),
            0xC0.toByte(),
            0x00,
            0x11,
            0x08,
            high,
            low,
            high,
            low,
            0x03,
            0x01,
            0x22,
            0x00,
            0x02,
            0x11,
            0x01,
            0x03,
            0x11,
            0x01,
            0xFF.toByte(),
            0xD9.toByte(),
        )
    }

    @Test
    fun aPlausiblePhoto_isDecoded() {
        assertNotNull(decodeFace(jpeg(256), targetPx = 96))
    }

    @Test
    fun aCorruptedPhoto_isNotDecoded_andDoesNotCrash() {
        assertNull(decodeFace(ByteArray(300) { (it * 31).toByte() }, targetPx = 96))
        assertNull(decodeFace(jpeg(256).copyOf(40), targetPx = 96))
        assertNull(decodeFace(ByteArray(0), targetPx = 96))
    }

    @Test
    fun aPhotoThatAnnouncesGiganticDimensions_isNotDecoded() {
        assertNull(decodeFace(announcing(30_000), targetPx = 96))
        assertNull(decodeFace(announcing(SIDE_LIMIT + 1), targetPx = 96))
    }

    private companion object {
        /** `AvatarDecodePlan.MAX_SIDE` (2048). */
        const val SIDE_LIMIT = com.tomessa.miff.domain.profile.AvatarDecodePlan.MAX_SIDE
    }
}
