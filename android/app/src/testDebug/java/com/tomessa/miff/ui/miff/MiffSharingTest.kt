package com.tomessa.miff.ui.miff

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the share sheet and the clipboard get (ticket Miff 5.3, criteria G6, J3, V2): the message holds the name, the code by
 * groups and the link, nothing else; the copy holds the code alone, marked sensitive from Android 13; the clipboard is only
 * read when asked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "fr")
class MiffSharingTest {
    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun clipboard() = context().getSystemService(ClipboardManager::class.java)

    private val code = "XTZRSP367988"

    @Test
    fun message_hasTheNameTheCodeByGroupsAndTheLink_andNothingElse() {
        val message = MiffSharing.message(context(), "Famille Martin", code)
        assertTrue(message.contains("« Famille Martin »"))
        assertTrue(message.contains("XTZR SP36 7988"))
        assertTrue(message.contains(BuildConfig.LEGAL_BASE_URL.trimEnd('/') + "/"))
        assertFalse("the code is not given twice, nor in a raw form", message.contains(code))
    }

    @Test
    fun message_neverCarriesAnInvisibleOrBidirectionalCharacterOfTheName() {
        val hostile = "Fam" + 0x202E.toChar() + "ille" + 0x200B.toChar()
        val message = MiffSharing.message(context(), hostile, code)
        assertTrue(message.contains("« Famille »"))
        assertFalse(message.contains(0x202E.toChar().toString()))
    }

    @Test
    fun copy_putsTheCodeAlone_markedSensitive_withALabelThatDoesNotHoldTheCode() {
        val confirmsItself = MiffSharing.copy(context(), code)
        val clip = clipboard().primaryClip!!
        assertEquals(code, clip.getItemAt(0).text.toString())
        assertFalse(
            clip.description.label
                .toString()
                .contains("XTZR"),
        )
        val extras = clip.description.extras
        assertNotNull(extras)
        assertTrue(extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
        // From Android 13 the system shows its own confirmation: the app does not repeat it.
        assertFalse(confirmsItself)
    }

    @Test
    fun pastedText_isOnlyWhatIsInTheClipboard_andNullWhenEmpty() {
        clipboard().clearPrimaryClip()
        assertNull(MiffSharing.pastedText(context()))
        clipboard().setPrimaryClip(ClipData.newPlainText("x", "xtzr sp36 7988"))
        assertEquals("xtzr sp36 7988", MiffSharing.pastedText(context()))
    }
}
