package com.tomessa.miff.data.profile

import com.tomessa.miff.domain.profile.GooglePhotoSource
import com.tomessa.miff.domain.profile.GooglePhotoUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/*
 * SECURITY, for this file: the address and the bytes of the Google photo are personal data. Never logged, never in an
 * exception message, never written to disk here (the bytes go straight to the avatar codec, in memory).
 */

/**
 * Downloads the Google photo of the account, ONCE, with the plain Java HTTPS client (no new dependency).
 *
 * - HTTPS only and only `googleusercontent.com` ([GooglePhotoUrl.isAllowed]), checked again at EVERY redirect (at most
 *   [MAX_REDIRECTS]; the system never follows one by itself). The cleartext traffic is also refused by the network
 *   security config of the app.
 * - Nothing personal goes out: no cookie, no token, no header of ours; only the address given by Google.
 * - Short: [CONNECT_TIMEOUT_MS] to connect, [READ_TIMEOUT_MS] per read and [TOTAL_DEADLINE_MS] for the whole download,
 *   whatever the speed of the server.
 * - Bounded: an answer that announces more than [maxBytes], or that gives more, is dropped; so is anything that is not
 *   an image (content type "image/...").
 * - Every failure is a null: the caller carries on without photo and says nothing about it.
 */
class HttpsGooglePhotoSource(
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val maxBytes: Int = MAX_BYTES,
    private val nowMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) : GooglePhotoSource {
    override suspend fun fetch(url: String): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                download(url)
            } catch (e: IOException) {
                null
            } catch (e: RuntimeException) {
                null
            }
        }

    private fun download(first: String): ByteArray? {
        val deadline = nowMillis() + TOTAL_DEADLINE_MS
        var current = first
        repeat(MAX_REDIRECTS + 1) {
            if (!GooglePhotoUrl.isAllowed(current)) return null
            val connection = open(URL(current))
            try {
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.requestMethod = "GET"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("Accept", "image/*")
                val code = connection.responseCode
                if (code in REDIRECT_CODES) {
                    val location = connection.getHeaderField("Location") ?: return null
                    current = URI(current).resolve(location).toString()
                    return@repeat
                }
                if (code != HttpURLConnection.HTTP_OK) return null
                if (connection.contentType?.startsWith("image/", ignoreCase = true) != true) return null
                if (connection.contentLengthLong > maxBytes) return null
                return connection.inputStream.use { readBounded(it, maxBytes, deadline, nowMillis) }
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    companion object {
        /** A 256 px profile picture weighs a few tens of kilobytes; this leaves room and still protects the memory. */
        const val MAX_BYTES = 512 * 1024
        const val MAX_REDIRECTS = 2
        const val CONNECT_TIMEOUT_MS = 4_000
        const val READ_TIMEOUT_MS = 4_000
        const val TOTAL_DEADLINE_MS = 6_000L
        private const val NANOS_PER_MILLI = 1_000_000L
        private val REDIRECT_CODES =
            setOf(
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_SEE_OTHER,
                307,
                308,
            )

        /** Reads at most [limit] bytes before [deadline]; null if there is more, or if it takes too long. */
        internal fun readBounded(
            input: InputStream,
            limit: Int,
            deadline: Long,
            nowMillis: () -> Long,
        ): ByteArray? {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                if (nowMillis() > deadline) return null
                val read = input.read(buffer)
                if (read < 0) return out.toByteArray()
                if (out.size() + read > limit) return null
                out.write(buffer, 0, read)
            }
        }
    }
}
