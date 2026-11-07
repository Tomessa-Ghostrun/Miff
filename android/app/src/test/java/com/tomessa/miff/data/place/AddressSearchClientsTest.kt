package com.tomessa.miff.data.place

import com.tomessa.miff.domain.place.AddressSearch
import com.tomessa.miff.domain.place.AddressSearchResult
import com.tomessa.miff.testsupport.testSuggestion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The clients of the services of suggestions (ticket LOC-1, 12; ADR1, ADR3, ADR4). No network: a fake fetcher. */
class AddressSearchClientsTest {
    private val photonBody =
        """{"features":[{"properties":{"housenumber":"17","street":"Avenue d'Italie","city":"Paris",""" +
            """"postcode":"75013",""" +
            """"country":"France"},"geometry":{"type":"Point","coordinates":[2.3567938,48.8292675]}}]}"""

    private class FakeFetcher(
        var body: String? = null,
    ) : HttpFetcher {
        val urls = mutableListOf<String>()

        override suspend fun getText(url: String): String? {
            urls += url
            return body
        }
    }

    @Test
    fun photon_asksOverHttps_withTheTextEncoded_theLanguage_andAFewResults() =
        runTest {
            val fetcher = FakeFetcher(photonBody)
            val search = PhotonAddressSearch("https://photon.komoot.io/api/", fetcher)
            val result = search.search("17 av d'Italie & co", "fr")
            assertTrue(result is AddressSearchResult.Found)
            val url = fetcher.urls.single()
            assertTrue(url.startsWith("https://photon.komoot.io/api/?q=17+av+d%27Italie+%26+co"))
            assertTrue(url.contains("lang=fr"))
            assertTrue(url.contains("limit="))
            // No key, no position of the phone, nothing but the typed text.
            assertFalse(url.contains("key", ignoreCase = true))
            assertFalse(url.contains("lat="))
        }

    @Test
    fun photon_anyOtherLanguageAsksForEnglish() =
        runTest {
            val fetcher = FakeFetcher(photonBody)
            PhotonAddressSearch("https://photon.komoot.io/api/", fetcher).search("rue", "de")
            assertTrue(fetcher.urls.single().contains("lang=en"))
        }

    @Test
    fun photon_noAnswer_orAnAnswerThatMakesNoSense_isUnavailable_andAnEmptyOneIsNoResult() =
        runTest {
            val fetcher = FakeFetcher(null)
            val search = PhotonAddressSearch("https://photon.komoot.io/api/", fetcher)
            assertEquals(AddressSearchResult.Unavailable, search.search("rue", "fr"))
            fetcher.body = "<html>429</html>"
            assertEquals(AddressSearchResult.Unavailable, search.search("rue", "fr"))
            fetcher.body = """{"features":[]}"""
            assertEquals(AddressSearchResult.NoResults, search.search("rue", "fr"))
        }

    @Test
    fun clearText_isRefusedByConstruction() {
        val refused = runCatching { PhotonAddressSearch("http://photon.komoot.io/api/", FakeFetcher()) }
        assertTrue(refused.isFailure)
        val clear = runCatching { GeoapifyAddressSearch("key", FakeFetcher(), "http://api.geoapify.com/x") }
        assertTrue(clear.isFailure)
    }

    @Test
    fun geoapify_carriesItsKey_only_inItsOwnRequest() =
        runTest {
            val fetcher = FakeFetcher("""{"features":[]}""")
            GeoapifyAddressSearch("TESTKEY123", fetcher).search("rue", "en")
            val url = fetcher.urls.single()
            assertTrue(url.startsWith("https://api.geoapify.com/"))
            assertTrue(url.contains("apiKey=TESTKEY123"))
            assertTrue(url.contains("lang=en"))
        }

    private class Scripted(
        vararg answers: AddressSearchResult,
    ) : AddressSearch {
        private val queue = ArrayDeque(answers.toList())
        var calls = 0

        override suspend fun search(
            query: String,
            language: String,
        ): AddressSearchResult {
            calls++
            return queue.removeFirst()
        }
    }

    @Test
    fun fallback_isUsedOnlyWhenTheFirstIsUnavailable() =
        runTest {
            val found = AddressSearchResult.Found(listOf(testSuggestion()))
            val primary = Scripted(AddressSearchResult.Unavailable, AddressSearchResult.NoResults)
            val fallback = Scripted(found)
            val search = FallbackAddressSearch(primary, fallback)
            assertEquals(found, search.search("rue", "fr"))
            // "No result" is an answer: the fallback is not asked.
            assertEquals(AddressSearchResult.NoResults, search.search("rue", "fr"))
            assertEquals(1, fallback.calls)
        }

    @Test
    fun fallback_absent_meansUnavailableStaysUnavailable() =
        runTest {
            val search = FallbackAddressSearch(Scripted(AddressSearchResult.Unavailable), null)
            assertEquals(AddressSearchResult.Unavailable, search.search("rue", "fr"))
        }

    @Test
    fun cache_answersTheSameTextWithoutAskingAgain_neverCachesUnavailable_andIsEmptiedAtTheEndOfTheSession() =
        runTest {
            val found = AddressSearchResult.Found(listOf(testSuggestion()))
            val inner = Scripted(AddressSearchResult.Unavailable, found, found)
            val cached = CachedAddressSearch(inner)
            assertEquals(AddressSearchResult.Unavailable, cached.search("17 avenue", "fr"))
            assertEquals(found, cached.search("17 avenue", "fr"))
            // Same text (spaces and capitals do not matter), same language: no new request.
            assertEquals(found, cached.search("  17 AVENUE ", "fr"))
            assertEquals(2, inner.calls)
            // Another language is another answer.
            cached.search("17 avenue", "en")
            assertEquals(3, inner.calls)
            cached.stop()
            assertEquals(0, cached.size())
        }

    @Test
    fun cache_isBounded() =
        runTest {
            val inner = AddressSearch { _, _ -> AddressSearchResult.NoResults }
            val cached = CachedAddressSearch(inner, maxEntries = 3)
            repeat(10) { cached.search("rue $it", "fr") }
            assertEquals(3, cached.size())
        }
}
