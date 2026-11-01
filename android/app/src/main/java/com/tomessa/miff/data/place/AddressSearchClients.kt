package com.tomessa.miff.data.place

import com.tomessa.miff.data.session.SessionTeardown
import com.tomessa.miff.domain.place.AddressSearch
import com.tomessa.miff.domain.place.AddressSearchResult
import com.tomessa.miff.domain.place.AddressSuggestionParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/*
 * SECURITY, for this file: the text typed in the address field (often a home address) leaves the phone here, towards the service
 * of suggestions, and nowhere else. It is NEVER logged, never in an analytics event, never in an exception message (no exception
 * of this file carries the URL). Everything goes over HTTPS: an address that is not https:// is refused, with no fallback to
 * clear text and no redirect followed. The provider sees the typed text and the IP address of the phone (to say in the privacy
 * policy: docs/dev/lieux-fournisseurs-gratuits.md). No key is committed; the optional one comes from a build property.
 */

/** One GET, text back. Behind an interface so that the clients are tested with a fake and never touch the network in a test. */
fun interface HttpFetcher {
    /** The body of the answer, or null for ANY failure (no network, slow, not 200, too big, not https). Honours cancellation. */
    suspend fun getText(url: String): String?
}

/** The fetcher of the app: `HttpsURLConnection` (no extra library), short timeouts, a bounded body, no redirect. */
class UrlConnectionFetcher(
    private val userAgent: String,
    private val timeoutMillis: Int = TIMEOUT_MS,
    private val maxBytes: Int = MAX_BYTES,
) : HttpFetcher {
    override suspend fun getText(url: String): String? =
        coroutineScope {
            val connection =
                try {
                    URL(url).openConnection() as? HttpsURLConnection
                } catch (e: IOException) {
                    null
                } ?: return@coroutineScope null
            val work = async(Dispatchers.IO) { read(connection) }
            try {
                work.await()
            } catch (e: CancellationException) {
                // A cancelled search (the person typed another letter) cuts the request instead of waiting for its answer.
                connection.disconnect()
                throw e
            } finally {
                connection.disconnect()
            }
        }

    /** The blocking part, on an I/O thread: any failure is "no answer" (null), never an exception that could carry the address. */
    private fun read(connection: HttpsURLConnection): String? =
        try {
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != HttpsURLConnection.HTTP_OK) {
                null
            } else {
                connection.inputStream.use { stream ->
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(CHUNK)
                    var tooBig = false
                    while (!tooBig) {
                        val count = stream.read(chunk)
                        if (count < 0) break
                        if (buffer.size() + count > maxBytes) tooBig = true else buffer.write(chunk, 0, count)
                    }
                    if (tooBig) null else buffer.toString(Charsets.UTF_8.name())
                }
            }
        } catch (e: Exception) {
            null
        }

    private companion object {
        const val TIMEOUT_MS = 5_000
        const val MAX_BYTES = 128 * 1024
        const val CHUNK = 8 * 1024
    }
}

/**
 * Photon, the public instance of komoot (`photon.komoot.io`): free, no key, search-as-you-type, OpenStreetMap data. No guarantee
 * of service and a fair-use policy ("extensive usage will be throttled or completely banned"): hence the 300 ms pause, the
 * cancellation, the cache and the 3-character minimum upstream. Switch signal: see docs/dev/lieux-fournisseurs-gratuits.md.
 */
class PhotonAddressSearch(
    private val baseUrl: String,
    private val fetcher: HttpFetcher,
) : AddressSearch {
    init {
        require(baseUrl.startsWith("https://")) { "https only" }
    }

    override suspend fun search(
        query: String,
        language: String,
    ): AddressSearchResult {
        val url = "$baseUrl?q=${encode(query)}&limit=$LIMIT&lang=${if (language == "fr") "fr" else "en"}"
        val body = fetcher.getText(url) ?: return AddressSearchResult.Unavailable
        val suggestions = AddressSuggestionParser.fromPhoton(body) ?: return AddressSearchResult.Unavailable
        return if (suggestions.isEmpty()) AddressSearchResult.NoResults else AddressSearchResult.Found(suggestions)
    }

    private companion object {
        const val LIMIT = 8 // a few more than the 5 shown: some are dropped (duplicates, no usable position)
    }
}

/**
 * Geoapify (free plan: 3 000 credits per day, one per request, attribution required): the PREPARED fallback. Only built when a key is
 * given at build time (BuildConfig.GEOAPIFY_KEY, never committed). Not tested against the real service (no key was available).
 */
class GeoapifyAddressSearch(
    private val apiKey: String,
    private val fetcher: HttpFetcher,
    private val baseUrl: String = "https://api.geoapify.com/v1/geocode/autocomplete",
) : AddressSearch {
    init {
        require(baseUrl.startsWith("https://")) { "https only" }
    }

    override suspend fun search(
        query: String,
        language: String,
    ): AddressSearchResult {
        val url =
            "$baseUrl?text=${encode(
                query,
            )}&limit=$LIMIT&lang=${if (language == "fr") "fr" else "en"}&format=geojson&apiKey=$apiKey"
        val body = fetcher.getText(url) ?: return AddressSearchResult.Unavailable
        val suggestions = AddressSuggestionParser.fromGeoapify(body) ?: return AddressSearchResult.Unavailable
        return if (suggestions.isEmpty()) AddressSearchResult.NoResults else AddressSearchResult.Found(suggestions)
    }

    private companion object {
        const val LIMIT = 8
    }
}

/** The provider chosen by the settings, then the fallback when (and only when) the first one is unavailable. */
class FallbackAddressSearch(
    private val primary: AddressSearch,
    private val fallback: AddressSearch?,
) : AddressSearch {
    override suspend fun search(
        query: String,
        language: String,
    ): AddressSearchResult {
        val first = primary.search(query, language)
        if (first != AddressSearchResult.Unavailable || fallback == null) return first
        return fallback.search(query, language)
    }
}

/**
 * Keeps the answers of the session (criterion ADR1): the same text typed again, or after a correction, does not ask the service
 * again. Only a real answer is kept (found / no result): an "unavailable" is never cached, so "Réessayer" really retries. The
 * texts typed are addresses: the cache lives in memory only and is emptied when the session ends.
 */
class CachedAddressSearch(
    private val delegate: AddressSearch,
    private val maxEntries: Int = MAX_ENTRIES,
) : AddressSearch,
    SessionTeardown {
    private val lock = Any()
    private val entries =
        object : LinkedHashMap<String, AddressSearchResult>(MAX_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AddressSearchResult>?): Boolean =
                size > maxEntries
        }

    override suspend fun search(
        query: String,
        language: String,
    ): AddressSearchResult {
        val key = "$language|${query.trim().lowercase()}"
        synchronized(lock) { entries[key] }?.let { return it }
        val answer = delegate.search(query, language)
        if (answer != AddressSearchResult.Unavailable) synchronized(lock) { entries[key] = answer }
        return answer
    }

    override suspend fun stop() {
        synchronized(lock) { entries.clear() }
    }

    /** Only for the tests. */
    internal fun size(): Int = synchronized(lock) { entries.size }

    private companion object {
        const val MAX_ENTRIES = 30
    }
}

private fun encode(text: String): String = URLEncoder.encode(text, Charsets.UTF_8.name())
