package com.tomessa.miff.data.place

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The tiles of the map go with the session (review of PR #56, m9). The map library itself is not started in these tests. */
class MapTileCachePurgerTest {
    @Test
    fun noCacheFile_nothingIsStarted() =
        runTest {
            var cleared = 0
            MapTileCachePurger(hasCache = { false }, clear = { cleared++ }).stop()
            assertEquals(0, cleared)
        }

    @Test
    fun aCacheFile_isErasedAtTheEndOfTheSession() =
        runTest {
            var cleared = 0
            MapTileCachePurger(hasCache = { true }, clear = { cleared++ }).stop()
            assertEquals(1, cleared)
        }

    @Test
    fun aFailure_neverBlocksASignOut() =
        runTest {
            MapTileCachePurger(hasCache = { true }, clear = { error("boom") }).stop()
        }

    @Test
    fun aClearThatNeverEnds_isGivenUpAfterTheLimitOfTime() =
        runTest {
            MapTileCachePurger(hasCache = { true }, clear = { delay(60_000) }, timeoutMillis = 1_000).stop()
        }

    @Test
    fun theFileIsTheOneOfMapLibre() {
        assertEquals("mbgl-offline.db", MapTileCachePurger.DATABASE_NAME)
    }
}
