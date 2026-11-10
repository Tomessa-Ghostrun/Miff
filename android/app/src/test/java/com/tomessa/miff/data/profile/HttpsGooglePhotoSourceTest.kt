package com.tomessa.miff.data.profile

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** The download of the Google photo: nothing leaves for another host, nothing too big comes in, every failure is a null. */
class HttpsGooglePhotoSourceTest {
    private class FakeConnection(
        url: URL,
        private val answer: Answer,
    ) : HttpURLConnection(url) {
        var disconnected = false
        var followRedirectsAtRequest: Boolean? = null

        override fun connect() = Unit

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy() = false

        override fun getResponseCode(): Int {
            followRedirectsAtRequest = instanceFollowRedirects
            if (answer.fail) throw IOException("boom")
            return answer.code
        }

        override fun getHeaderField(name: String?): String? = if (name == "Location") answer.location else null

        override fun getContentType(): String? = answer.type

        override fun getContentLengthLong(): Long = answer.announced ?: answer.body.size.toLong()

        override fun getInputStream(): InputStream = ByteArrayInputStream(answer.body)
    }

    private class Answer(
        val code: Int = 200,
        val type: String? = "image/jpeg",
        val body: ByteArray = ByteArray(2_000) { 3 },
        val location: String? = null,
        val announced: Long? = null,
        val fail: Boolean = false,
    )

    private val asked = mutableListOf<String>()
    private val connections = mutableListOf<FakeConnection>()

    private fun source(
        vararg answers: Answer,
        maxBytes: Int = 100_000,
        now: () -> Long = { 0L },
    ): HttpsGooglePhotoSource {
        val queue = ArrayDeque(answers.toList())
        return HttpsGooglePhotoSource(
            open = { url ->
                asked += url.toString()
                FakeConnection(url, queue.removeFirst()).also { connections += it }
            },
            maxBytes = maxBytes,
            nowMillis = now,
        )
    }

    private val google = "https://lh3.googleusercontent.com/a/x=s256-c"

    private fun fetch(
        s: HttpsGooglePhotoSource,
        url: String = google,
    ) = runBlocking { s.fetch(url) }

    @Test
    fun anImage_isReturned_andTheConnectionIsClosed_withoutFollowingRedirectsByItself() {
        val body = ByteArray(2_000) { 3 }
        assertArrayEquals(body, fetch(source(Answer(body = body))))
        assertEquals(listOf(google), asked)
        assertTrue(connections.single().disconnected)
        assertEquals(false, connections.single().followRedirectsAtRequest)
    }

    @Test
    fun anAddressThatIsNotGoogle_isNeverContacted() {
        assertNull(fetch(source(Answer()), "https://example.com/a.jpg"))
        assertNull(fetch(source(Answer()), "http://lh3.googleusercontent.com/a/x"))
        assertTrue(asked.isEmpty())
    }

    @Test
    fun aRedirectInsideGoogle_isFollowed_aRedirectElsewhere_isNot() {
        val inside = Answer(code = 302, location = "https://lh4.googleusercontent.com/a/y=s256-c")
        assertNotNull(fetch(source(inside, Answer())))
        assertEquals(listOf(google, "https://lh4.googleusercontent.com/a/y=s256-c"), asked)

        asked.clear()
        val outside = Answer(code = 302, location = "https://evil.example/steal.jpg")
        assertNull(fetch(source(outside, Answer())))
        assertEquals("the other host was never contacted", listOf(google), asked)

        asked.clear()
        val toClearText = Answer(code = 301, location = "http://lh3.googleusercontent.com/a/z")
        assertNull(fetch(source(toClearText, Answer())))
        assertEquals(listOf(google), asked)
    }

    @Test
    fun aRelativeRedirect_staysOnTheSameGoogleHost() {
        val relative = Answer(code = 307, location = "/a/other=s256-c")
        assertNotNull(fetch(source(relative, Answer())))
        assertEquals("https://lh3.googleusercontent.com/a/other=s256-c", asked.last())
    }

    @Test
    fun tooManyRedirects_giveUp() {
        val loop = Answer(code = 302, location = google)
        assertNull(fetch(source(loop, loop, loop, Answer())))
        assertEquals(HttpsGooglePhotoSource.MAX_REDIRECTS + 1, asked.size)
    }

    @Test
    fun aRefusal_aNonImage_orAnAnswerWithoutType_isNull() {
        assertNull(fetch(source(Answer(code = 404))))
        assertNull(fetch(source(Answer(code = 500))))
        assertNull(fetch(source(Answer(type = "text/html"))))
        assertNull(fetch(source(Answer(type = null))))
    }

    @Test
    fun aRedirectWithoutDestination_isNull() {
        assertNull(fetch(source(Answer(code = 302, location = null))))
    }

    @Test
    fun anAnswerThatIsTooBig_isDropped_announcedOrNot() {
        assertNull(fetch(source(Answer(announced = 5_000_000), maxBytes = 100_000)))
        assertNull(fetch(source(Answer(body = ByteArray(5_000), announced = -1), maxBytes = 4_000)))
    }

    @Test
    fun aNetworkError_isNull_notAnException() {
        assertNull(fetch(source(Answer(fail = true))))
    }

    @Test
    fun aDownloadThatDrags_isGivenUp() {
        var clock = 0L
        val dragging =
            source(Answer(), now = {
                clock += 5_000
                clock
            })
        assertNull(fetch(dragging))
    }

    @Test
    fun readBounded_readsEverythingUnderTheLimit_andNothingAboveIt() {
        val data = ByteArray(20_000) { (it % 251).toByte() }
        assertArrayEquals(data, HttpsGooglePhotoSource.readBounded(ByteArrayInputStream(data), 20_000, 1_000) { 0L })
        assertNull(HttpsGooglePhotoSource.readBounded(ByteArrayInputStream(data), 19_999, 1_000) { 0L })
    }
}
