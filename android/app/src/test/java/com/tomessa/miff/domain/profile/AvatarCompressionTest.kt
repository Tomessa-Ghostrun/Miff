package com.tomessa.miff.domain.profile

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Finding an avatar of at most 20 000 bytes (data-model.md 2.1, ticket 6.3, criterion PR4). The encoder is a fake here. */
class AvatarCompressionTest {
    @Test
    fun theLimitIsTheOneOfTheRules() {
        assertEquals(20_000, AvatarCompression.MAX_BYTES)
        assertEquals(160, AvatarCompression.SIDES.first())
    }

    @Test
    fun aPictureThatFitsAtTheTargetSizeAndBestQuality_isKeptAsIs() {
        val calls = mutableListOf<Pair<Int, Int>>()
        val result =
            AvatarCompression.fit { side, quality ->
                calls += side to quality
                ByteArray(8_000)
            }
        assertEquals(8_000, result!!.size)
        assertEquals(listOf(160 to 80), calls)
    }

    @Test
    fun theQualityGoesDownUntilItFits() {
        val calls = mutableListOf<Pair<Int, Int>>()
        val result =
            AvatarCompression.fit { side, quality ->
                calls += side to quality
                ByteArray(quality * 400) // 80 -> 32 000, 70 -> 28 000, 60 -> 24 000, 50 -> 20 000
            }
        assertEquals(20_000, result!!.size)
        assertEquals(listOf(160 to 80, 160 to 70, 160 to 60, 160 to 50), calls)
    }

    @Test
    fun ifTheQualityIsNotEnough_aSmallerPictureIsTried() {
        val result =
            AvatarCompression.fit { side, quality ->
                if (side == 160) ByteArray(25_000) else ByteArray(side * 100 + quality)
            }
        assertTrue(result!!.size <= 20_000)
    }

    @Test
    fun ifNothingFits_thereIsNoAvatar_neverAnOversizedOne() {
        assertNull(AvatarCompression.fit { _, _ -> ByteArray(20_001) })
    }

    @Test
    fun anEncoderThatFails_givesNoAvatar() {
        assertNull(AvatarCompression.fit { _, _ -> null })
    }

    @Test
    fun exactlyTheLimitIsAccepted() {
        val bytes = ByteArray(20_000) { 7 }
        assertArrayEquals(bytes, AvatarCompression.fit { _, _ -> bytes })
    }
}
