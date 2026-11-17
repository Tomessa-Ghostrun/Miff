package com.tomessa.miff.data.notification

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.domain.feed.PlaceKind
import com.tomessa.miff.domain.notification.VisitMember
import com.tomessa.miff.domain.notification.VisitState
import com.tomessa.miff.domain.tracking.PresenceValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What "Depuis ta dernière visite" keeps on the phone (NOTIF-P1A, criteria C1-7 and C1-8): it round-trips, a damaged file is no picture, nothing but identifiers, a keyword of the
 * presence, a category and times is written, and the sign-out leaves nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LastVisitStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    private fun file() = context.getSharedPreferences("miff_notification_last_visit", Context.MODE_PRIVATE)

    private val memory =
        LastVisitMemory(
            mapOf(
                "miff-1" to
                    VisitState(
                        "miff-1",
                        listOf(
                            VisitMember("t", false, PresenceValue.OUT, PlaceKind.SCHOOL, 10L, 20L, silent = true),
                            VisitMember("a", true, PresenceValue.TRAVEL, null, null, null, silent = false),
                            VisitMember("c", false, PresenceValue.IN_RISK, null, 30L, 40L, silent = false),
                        ),
                    ),
            ),
            "miff-1",
        )

    @Test
    fun theMemoryRoundTrips_everyField() {
        SharedPreferencesLastVisitStore(context).save(memory)
        val back = SharedPreferencesLastVisitStore(context).load()
        assertEquals("miff-1", back.lastMiffId)
        assertEquals(memory.states.getValue("miff-1"), back.states.getValue("miff-1"))
    }

    @Test
    fun aDamagedFile_isNoPicture_neverACrash() {
        file().edit().putString("memory", "{not json").commit()
        val back = SharedPreferencesLastVisitStore(context).load()
        assertTrue(back.states.isEmpty())
        assertNull(back.lastMiffId)
        // A category written by a newer version that this one does not know is read as "a trusted place", never a crash.
        file()
            .edit()
            .putString(
                "memory",
                """{"states":[{"miffId":"m","members":[{"uid":"t","place":"MOON","presence":"out"}]}],"lastMiffId":"m"}""",
            ).commit()
        assertNull(
            SharedPreferencesLastVisitStore(context)
                .load()
                .states
                .getValue("m")
                .members
                .single()
                .place,
        )
    }

    @Test
    fun theFileHoldsNoPositionNoAddressNoNameOfAPlaceOrAPerson_c1_7() {
        SharedPreferencesLastVisitStore(context).save(memory)
        val raw = file().getString("memory", "").orEmpty()
        listOf("lat", "lng", "lon", "address", "name", "photo", "battery", "phone", "coordinates").forEach {
            assertFalse("the picture must not hold \"$it\"", raw.contains(it, ignoreCase = true))
        }
        // The presence is a keyword of the status and the place a category: nothing more precise.
        assertTrue(raw.contains("in_risk"))
        assertTrue(raw.contains("SCHOOL"))
    }

    @Test
    fun clear_leavesNothingOnThePhone_c1_8() {
        val store = SharedPreferencesLastVisitStore(context)
        store.save(memory)
        store.clear()
        assertTrue(file().all.isEmpty())
        assertTrue(SharedPreferencesLastVisitStore(context).load().states.isEmpty())
    }
}
