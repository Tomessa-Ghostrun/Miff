package com.tomessa.miff.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.data.auth.SharedPreferencesPendingConsentStore
import com.tomessa.miff.domain.auth.PendingConsent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The acceptance recorded on the device belongs to ONE account: it can never be applied to another person, and it is
 * really gone once cleared (the real SharedPreferences implementation, on the JVM with Robolectric).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PendingConsentStoreTest {
    private fun store() = SharedPreferencesPendingConsentStore(ApplicationProvider.getApplicationContext<Application>())

    @Test
    fun whatIsSaved_isReadBackForTheSameAccount() {
        val store = store()
        val consent = PendingConsent("uid-1", "0.1", analyticsAccepted = true)
        store.save(consent)
        assertEquals(consent, store.read("uid-1"))
    }

    @Test
    fun anotherAccount_neverSeesIt() {
        val store = store()
        store.save(PendingConsent("uid-1", "0.1", analyticsAccepted = true))
        assertNull(store.read("uid-2"))
        assertNull(store.read(""))
    }

    @Test
    fun saving_forAnotherAccount_replacesTheOldOne() {
        val store = store()
        store.save(PendingConsent("uid-1", "0.1", analyticsAccepted = true))
        store.save(PendingConsent("uid-2", "0.1", analyticsAccepted = false))
        assertNull(store.read("uid-1"))
        assertEquals(PendingConsent("uid-2", "0.1", analyticsAccepted = false), store.read("uid-2"))
    }

    @Test
    fun clear_forgetsEverything_evenForANewInstance() {
        store().save(PendingConsent("uid-1", "0.1", analyticsAccepted = true))
        store().clear()
        assertNull(store().read("uid-1"))
    }

    @Test
    fun aSavedChoice_survivesANewInstance_likeARestartOfTheApp() {
        store().save(PendingConsent("uid-1", "0.1", analyticsAccepted = true))
        assertEquals(PendingConsent("uid-1", "0.1", analyticsAccepted = true), store().read("uid-1"))
    }
}
