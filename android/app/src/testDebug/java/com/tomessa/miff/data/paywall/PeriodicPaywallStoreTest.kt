package com.tomessa.miff.data.paywall

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The one whole number of the periodic offer (criteria PP4 and PP6): a round trip, a damaged value, the erasure, and NOTHING else in the file. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PeriodicPaywallStoreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun store() = SharedPreferencesPeriodicPaywallStore(context)

    private fun prefs() = context.getSharedPreferences(SharedPreferencesPeriodicPaywallStore.FILE, Context.MODE_PRIVATE)

    @Test
    fun aFreshPhone_hasCountedNothing() {
        assertEquals(0, store().read())
    }

    @Test
    fun whatIsWritten_isReadBackByAnotherInstance() {
        store().write(7)
        assertEquals(7, store().read())
    }

    @Test
    fun theFileHoldsOnlyOneWholeNumber_noDate_noIdentifier_noName() {
        store().write(12)
        val all = prefs().all
        assertEquals(setOf(SharedPreferencesPeriodicPaywallStore.KEY_OPENINGS), all.keys)
        assertTrue("an Int and nothing else: ${all.values}", all.values.single() is Int)
    }

    @Test
    fun aDamagedValue_readsAsZero_neverCrashes() {
        prefs().edit().putString(SharedPreferencesPeriodicPaywallStore.KEY_OPENINGS, "not a number").commit()
        assertEquals(0, store().read())
        prefs()
            .edit()
            .clear()
            .putInt(SharedPreferencesPeriodicPaywallStore.KEY_OPENINGS, -5)
            .commit()
        assertEquals(0, store().read())
        prefs()
            .edit()
            .clear()
            .putInt(SharedPreferencesPeriodicPaywallStore.KEY_OPENINGS, Int.MAX_VALUE)
            .commit()
        assertEquals(1_000, store().read())
    }

    @Test
    fun clear_forgetsEverything_andIsHarmlessWhenThereIsNothing() {
        store().clear()
        store().write(9)
        store().clear()
        assertEquals(0, store().read())
        assertTrue(prefs().all.isEmpty())
    }
}
