package com.tomessa.miff.data.onboarding

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The local draft (Onboarding ticket 6.2, criterion TEL9, case 33): kept for the right account only, erased on demand. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ProfileDraftStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val avatarFile get() = File(File(context.filesDir, "profile_draft"), "avatar.jpg")

    private fun store() = SharedPreferencesProfileDraftStore(context)

    private val draft =
        ProfileDraft(
            uid = "uid-1",
            step = DraftStep.PHONE,
            firstName = "Léa",
            lastName = "Martin",
            regionCode = "BE",
            phoneText = "0470 12 34 56",
            avatar = ByteArray(1234) { it.toByte() },
        )

    @Test
    fun whatIsSavedIsReadBack_afterTheAppWasClosed() {
        store().save(draft)
        val read = store().read("uid-1")!! // a NEW store: the process was restarted
        assertEquals(DraftStep.PHONE, read.step)
        assertEquals("Léa", read.firstName)
        assertEquals("Martin", read.lastName)
        assertEquals("BE", read.regionCode)
        assertEquals("0470 12 34 56", read.phoneText)
        assertArrayEquals(draft.avatar, read.avatar)
    }

    @Test
    fun withoutAnyChoice_theCountryAndThePhotoAreAbsent() {
        store().save(draft.copy(regionCode = null, avatar = null, phoneText = ""))
        val read = store().read("uid-1")!!
        assertNull(read.regionCode)
        assertNull(read.avatar)
        assertEquals("", read.phoneText)
    }

    @Test
    fun theDraftOfAnotherAccount_isNeverReturned_andIsErased() {
        val s = store()
        s.save(draft)
        assertNull(s.read("uid-2"))
        assertNull("erased by the other account's visit", store().read("uid-1"))
        assertFalse(avatarFile.exists())
    }

    @Test
    fun clearErasesEverything_theNamesTheNumberAndThePhoto() {
        val s = store()
        s.save(draft)
        assertTrue(avatarFile.exists())
        s.clear()
        assertNull(store().read("uid-1"))
        assertFalse("the photo file is gone", avatarFile.exists())
        val prefs = context.getSharedPreferences("miff_profile_draft", Context.MODE_PRIVATE)
        assertTrue("nothing is left in the preferences", prefs.all.isEmpty())
    }

    @Test
    fun removingThePhoto_deletesTheFile() {
        val s = store()
        s.save(draft)
        s.save(draft.copy(avatar = null))
        assertFalse(avatarFile.exists())
        assertNull(store().read("uid-1")!!.avatar)
    }

    @Test
    fun theFileIsOnlyRewrittenWhenThePhotoChanges() {
        val s = store()
        s.save(draft)
        val first = avatarFile.lastModified()
        avatarFile.setLastModified(first - 10_000)
        s.save(draft.copy(firstName = "Léa2"))
        assertEquals("same picture: untouched", first - 10_000, avatarFile.lastModified())
    }

    @Test
    fun aDamagedStepFallsBackToTheProfileStep() {
        store().save(draft)
        context
            .getSharedPreferences(
                "miff_profile_draft",
                Context.MODE_PRIVATE,
            ).edit()
            .putString("step", "???")
            .commit()
        assertEquals(DraftStep.PROFILE, store().read("uid-1")!!.step)
    }

    @Test
    fun aFileThatIsNotOurs_bigger_thanTheLimit_isIgnored() {
        store().save(draft)
        avatarFile.writeBytes(ByteArray(50_000))
        assertNull(store().read("uid-1")!!.avatar)
    }

    @Test
    fun theDraftLivesInThePrivateStorageOfTheApp_andOutsideAnyBackup() {
        store().save(draft)
        assertTrue(avatarFile.absolutePath.startsWith(context.filesDir.absolutePath))
        assertNotNull(context.getSharedPreferences("miff_profile_draft", Context.MODE_PRIVATE))
        // Nothing of the app is backed up or transferred (res/xml/backup_rules.xml and data_extraction_rules.xml).
        val xml = File("src/main/res/xml/data_extraction_rules.xml").readText()
        assertTrue(xml.contains("""<exclude domain="file" />""") && xml.contains("""<exclude domain="sharedpref" />"""))
        assertTrue(File("src/main/AndroidManifest.xml").readText().contains("""android:allowBackup="false""""))
    }
}
