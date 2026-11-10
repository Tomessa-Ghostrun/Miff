package com.tomessa.miff.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.data.permission.SharedPreferencesNotificationAskStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The flag "notification screen already shown" is per account, and really gone once the account is forgotten (AUTH-5). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NotificationAskStoreTest {
    private fun store() =
        SharedPreferencesNotificationAskStore(ApplicationProvider.getApplicationContext<Application>())

    @Test
    fun forget_removesOnlyThatAccount_evenForANewInstance() {
        store().markShown("uid-1")
        store().markShown("uid-2")
        store().forget("uid-1")
        assertFalse(store().wasShown("uid-1"))
        assertTrue(store().wasShown("uid-2"))
    }
}
