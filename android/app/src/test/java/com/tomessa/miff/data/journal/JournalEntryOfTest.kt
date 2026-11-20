package com.tomessa.miff.data.journal

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

/** CNC-A: a document of the journal that does not have the expected shape is ignored ALONE: the reading of the others goes on (never a failure that is tried again for ever). */
class JournalEntryOfTest {
    private val now = 1_800_000_000_000L

    private fun doc(
        createdAt: Any? = Timestamp(Date(now)),
        expireAt: Any? = Timestamp(Date(now + 30L * 24 * 3_600_000L)),
    ): Map<String, Any?> =
        mapOf(
            "v" to 1L,
            "kind" to "update",
            "miffId" to "miffAAAA",
            "actorUid" to "actor1",
            "createdAt" to createdAt,
            "expireAt" to expireAt,
        )

    @Test
    fun aWellFormedDocumentIsAnEntry() {
        val entry = journalEntryOf("u-1", doc())!!
        assertEquals(now, entry.createdAtMillis)
        assertEquals(now + 30L * 24 * 3_600_000L, entry.expireAtMillis)
    }

    @Test
    fun aDateThatIsNotATimestamp_ignoresThatDocumentOnly_withoutThrowing() {
        assertNull(journalEntryOf("u-1", doc(createdAt = "yesterday")))
        assertNull(journalEntryOf("u-1", doc(createdAt = 12345L)))
        assertNull(journalEntryOf("u-1", doc(expireAt = mapOf("a" to 1))))
        assertNull(journalEntryOf("u-1", doc(expireAt = null)))
        // the next document of the same page is still read
        assertNotNull(journalEntryOf("u-2", doc()))
    }

    @Test
    fun aVersionOrFieldOfAnotherShape_isIgnored_neverAnError() {
        assertNull(journalEntryOf("u-1", doc() + ("v" to "2")))
        assertNull(journalEntryOf("u-1", doc() + ("miffId" to 7)))
        assertNull(journalEntryOf("u-1", doc() + ("kind" to listOf("update"))))
    }
}
