package com.tomessa.miff.domain.profile

import com.tomessa.miff.testsupport.FakeAvatarCodec
import com.tomessa.miff.testsupport.FakeGooglePhotoSource
import com.tomessa.miff.testsupport.FakePhoto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The Google photo of the account as a profile photo (decision of the PM, 2026-11-08): which address is accepted and at
 * which size, and the path address -> avatar, which never blocks and never throws.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GooglePhotoTest {
    private val google = "https://lh3.googleusercontent.com/a/ACg8ocJabc123-XYZ"

    // --- The address ---------------------------------------------------------------------------------------------

    @Test
    fun aGoogleAddress_isAskedAtTheSizeOfTheAvatar() {
        assertEquals("$google=s256-c", GooglePhotoUrl.select("$google=s96-c"))
        assertEquals("$google=s256-c", GooglePhotoUrl.select("$google=s96"))
        assertEquals("$google=s256-c", GooglePhotoUrl.select("$google=w96-h96-c"))
        assertEquals("$google=s256-c", GooglePhotoUrl.select(google))
    }

    @Test
    fun theOldStyleAddress_isAskedAtTheSizeOfTheAvatarToo() {
        assertEquals(
            "https://lh3.googleusercontent.com/-Xd/AAAAAAAAAAI/Bbbbbbbbbbb/4252/s256-c/photo.jpg",
            GooglePhotoUrl.select("https://lh3.googleusercontent.com/-Xd/AAAAAAAAAAI/Bbbbbbbbbbb/4252/s96-c/photo.jpg"),
        )
    }

    @Test
    fun anAddressWithAQuery_isKeptAsItIs() {
        val url = "https://lh3.googleusercontent.com/a/x?sz=50"
        assertEquals(url, GooglePhotoUrl.select(url))
    }

    @Test
    fun theFragmentIsDropped_andSpacesAreTrimmed() {
        assertEquals("$google=s256-c", GooglePhotoUrl.select("  $google=s96-c#frag  "))
    }

    @Test
    fun nothingIsSelected_whenThereIsNoAddress() {
        assertNull(GooglePhotoUrl.select(null))
        assertNull(GooglePhotoUrl.select(""))
        assertNull(GooglePhotoUrl.select("   "))
    }

    @Test
    fun anAddressThatIsNotGoogle_isRefused() {
        listOf(
            "https://example.com/photo.jpg",
            "https://googleusercontent.com.evil.example/a/x",
            "https://evilgoogleusercontent.com/a/x",
            "https://lh3.googleusercontent.com.evil.example/a/x",
            "https://graph.facebook.com/me/picture",
            "https://www.gravatar.com/avatar/abc",
        ).forEach { assertNull(it, GooglePhotoUrl.select(it)) }
    }

    @Test
    fun anAddressThatIsNotHttps_isRefused() {
        listOf(
            "http://lh3.googleusercontent.com/a/x=s96-c",
            "ftp://lh3.googleusercontent.com/a/x",
            "file:///sdcard/a.jpg",
            "content://media/picker/1",
            "//lh3.googleusercontent.com/a/x",
            "javascript:alert(1)",
        ).forEach { assertNull(it, GooglePhotoUrl.select(it)) }
    }

    @Test
    fun userInfo_otherPort_orGarbage_isRefused() {
        listOf(
            "https://user:pw@lh3.googleusercontent.com/a/x",
            "https://lh3.googleusercontent.com@evil.example/a/x",
            "https://lh3.googleusercontent.com:8443/a/x",
            "https://lh3.googleusercontent.com/a/ x y",
            "not an address",
            "https://",
        ).forEach { assertNull(it, GooglePhotoUrl.select(it)) }
        assertEquals(
            "https://lh3.googleusercontent.com:443/a/x=s256-c",
            GooglePhotoUrl.select("https://lh3.googleusercontent.com:443/a/x"),
        )
    }

    @Test
    fun anAbsurdlyLongAddress_isRefused() {
        assertNull(GooglePhotoUrl.select("https://lh3.googleusercontent.com/a/" + "x".repeat(3000)))
    }

    @Test
    fun theGenericPictureOfAnAccountWithoutPhoto_isNotUsed() {
        assertNull(GooglePhotoUrl.select("https://lh3.googleusercontent.com/a/default-user=s96-c"))
        assertNull(
            GooglePhotoUrl.select(
                "https://lh3.googleusercontent.com/-XdUIqdMkCWA/AAAAAAAAAAI/AAAAAAAAAAA/4252rscbv5M/photo.jpg",
            ),
        )
    }

    @Test
    fun theHostCheckIsCaseInsensitive_andAllowsTheBareDomain() {
        assertTrue(GooglePhotoUrl.isAllowed("https://LH3.GoogleUserContent.com/a/x"))
        assertTrue(GooglePhotoUrl.isAllowed("https://googleusercontent.com/a/x"))
        assertFalse(GooglePhotoUrl.isAllowed("https://lh3.googleusercontent.org/a/x"))
    }

    // --- The path address -> avatar ------------------------------------------------------------------------------

    private val codec = FakeAvatarCodec()
    private val source = FakeGooglePhotoSource()

    @Test
    fun theWholePath_givesTheAvatarJpegOfTheCodec_downloadedOnce() =
        runTest {
            val jpeg = ByteArray(8_000) { 5 }
            codec.encoded = jpeg
            codec.decoded = FakePhoto(256, 256)
            source.bytes = ByteArray(40_000) { 9 }
            val result = GooglePhotoPrefill(source, codec).load("$google=s96-c")
            assertArrayEquals(jpeg, result)
            assertEquals(listOf("$google=s256-c"), source.requested)
            assertEquals(1, codec.decodedBytes.size)
            // A square cut in the centre of the photo, in full.
            assertEquals(SquareCrop(0, 0, 256), codec.crops.single())
        }

    @Test
    fun aNonGoogleAddress_isNeverDownloaded() =
        runTest {
            assertNull(GooglePhotoPrefill(source, codec).load("https://example.com/me.jpg"))
            assertNull(GooglePhotoPrefill(source, codec).load("http://lh3.googleusercontent.com/a/x"))
            assertNull(GooglePhotoPrefill(source, codec).load(null))
            assertTrue(source.requested.isEmpty())
        }

    @Test
    fun aNetworkFailure_isNull_notAnException() =
        runTest {
            source.failure = IOException("no route")
            assertNull(GooglePhotoPrefill(source, codec).load(google))
            source.failure = IllegalStateException("anything")
            assertNull(GooglePhotoPrefill(source, codec).load(google))
            source.failure = null
            source.bytes = null
            assertNull(GooglePhotoPrefill(source, codec).load(google))
        }

    @Test
    fun aSlowDownload_isGivenUpAfterTheDelay() =
        runTest {
            source.gate = CompletableDeferred()
            assertNull(GooglePhotoPrefill(source, codec, timeoutMs = 50).load(google))
            assertTrue("the delay was virtual and short", testScheduler.currentTime <= 100)
        }

    @Test
    fun aPictureThatCannotBeDecodedOrFitted_isNull() =
        runTest {
            codec.decoded = null
            assertNull(GooglePhotoPrefill(source, codec).load(google))
            codec.decoded = FakePhoto(256, 256)
            codec.encoded = null
            assertNull(GooglePhotoPrefill(source, codec).load(google))
            codec.encoded = ByteArray(AvatarCompression.MAX_BYTES + 1)
            assertNull("never above the limit of the rules", GooglePhotoPrefill(source, codec).load(google))
        }
}
