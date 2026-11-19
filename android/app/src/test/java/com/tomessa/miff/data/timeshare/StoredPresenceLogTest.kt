package com.tomessa.miff.data.timeshare

import com.tomessa.miff.domain.timeshare.TimeShareRules
import com.tomessa.miff.domain.tracking.PresenceValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The local journal of the presence: what it keeps (TS-2: no coordinate, no name, no address, no identifier of a person), how long (TS-7), and when it is erased (TS-5).
 */
class StoredPresenceLogTest {
    private class MemoryStorage : PresenceLogStorage {
        var text: String? = null
        var writes = 0

        override fun read() = text

        override fun write(text: String): Boolean {
            this.text = text
            writes++
            return true
        }

        override fun clear() {
            text = null
        }
    }

    private val storage = MemoryStorage()
    private var uid: String? = "uid1"
    private var now = 1_800_000_000_000L
    private val day = TimeShareRules.DAY_MILLIS
    private val log = StoredPresenceLog(storage, { uid }, { now })

    @Test
    fun aChangeOfPresence_isKeptWithTheMiffTheStateThePlaceAndTheInstant() {
        log.record("miffA", PresenceValue.IN_TRUSTED, "home", now - 1000)
        val fact = log.facts("miffA").single()
        assertEquals(now - 1000, fact.atMillis)
        assertEquals(PresenceValue.IN_TRUSTED, fact.presence)
        assertEquals("home", fact.placeId)
        assertTrue(log.facts("miffB").isEmpty())
    }

    @Test
    fun theStoredTextHoldsNoCoordinateNoNameNoAddress_onlyIdentifiersAndInstants() {
        log.record("miffA", PresenceValue.IN_TRUSTED, "home", now - 3000)
        log.record("miffA", PresenceValue.OUT, "home", now - 2000)
        log.record("miffA", PresenceValue.IN_RISK, "danger1", now - 1000)
        val text = storage.text!!
        // A closed set of keys: the account, the Miff, the state, the place identifier, the instant.
        val keys = Regex("\"([a-z]+)\":").findAll(text).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("uid", "facts", "m", "s", "p", "t"), keys)
        assertFalse(
            Regex(
                "(?i)lat|lng|longitude|latitude|address|adresse|name|nom",
            ).containsMatchIn(text.replace("\"uid\"", "")),
        )
        // No decimal number (a coordinate would have one).
        assertFalse(Regex("[0-9]\\.[0-9]").containsMatchIn(text))
        // The state of an "out" never keeps the place it came from (only the states "in" name a place).
        assertEquals(null, log.facts("miffA")[1].placeId)
    }

    @Test
    fun aFactThatRepeatsThePreviousOne_isNotKeptTwice() {
        log.record("miffA", PresenceValue.IN_TRUSTED, "home", now - 3000)
        log.record("miffA", PresenceValue.IN_TRUSTED, "home", now - 2000)
        log.record("miffA", PresenceValue.IN_TRUSTED, "sport", now - 1500)
        log.record("miffA", PresenceValue.OUT, null, now - 1000)
        log.record("miffA", PresenceValue.OUT, null, now - 500)
        assertEquals(listOf("home", "sport", null), log.facts("miffA").map { it.placeId })
    }

    @Test
    fun anUnknownWithNothingBeforeIt_isNotKept_butOneAfterAPlaceIs() {
        log.record("miffA", PresenceValue.UNKNOWN, null, now - 3000)
        assertTrue(log.facts("miffA").isEmpty())
        assertNull(storage.text)
        log.record("miffA", PresenceValue.IN_TRUSTED, "home", now - 2000)
        log.record("miffA", PresenceValue.UNKNOWN, null, now - 1000)
        assertEquals(listOf(PresenceValue.IN_TRUSTED, PresenceValue.UNKNOWN), log.facts("miffA").map { it.presence })
    }

    @Test
    fun aTripIsKeptAsAnUnknown() {
        log.record("miffA", PresenceValue.IN_TRUSTED, "home", now - 3000)
        log.record("miffA", PresenceValue.TRAVEL, null, now - 1000)
        assertEquals(PresenceValue.UNKNOWN, log.facts("miffA").last().presence)
    }

    @Test
    fun aStateThatMustNameAPlaceButDoesNot_isAnUnknown() {
        log.record("miffA", PresenceValue.IN_TRUSTED, "home", now - 3000)
        log.record("miffA", PresenceValue.IN_RISK, null, now - 1000)
        assertEquals(PresenceValue.UNKNOWN, log.facts("miffA").last().presence)
        // A place identifier that does not look like one is never written.
        log.record("miffB", PresenceValue.IN_TRUSTED, "48.85,2.35", now - 1000)
        assertTrue(log.facts("miffB").isEmpty())
        log.record("a miff name", PresenceValue.IN_TRUSTED, "home", now - 1000)
        assertTrue(log.facts("a miff name").isEmpty())
    }

    @Test
    fun factsOlderThan35Days_areErased() {
        log.record("miffA", PresenceValue.IN_TRUSTED, "home", now - 40 * day)
        log.record("miffA", PresenceValue.OUT, null, now - 34 * day)
        log.record("miffA", PresenceValue.IN_TRUSTED, "home", now - 1 * day)
        val kept = log.facts("miffA")
        assertEquals(listOf(PresenceValue.OUT, PresenceValue.IN_TRUSTED), kept.map { it.presence })
        // And they are gone from the stored text too (not only hidden): written again, the file holds two facts.
        assertEquals(2, Regex("\"t\":").findAll(storage.text!!).count())
    }

    @Test
    fun nothingIsRecordedWithoutAnAccount_andTheFileOfAnotherAccountIsNeverUsed() {
        uid = null
        log.record("miffA", PresenceValue.IN_TRUSTED, "home", now)
        assertNull(storage.text)
        uid = "uid1"
        log.record("miffA", PresenceValue.IN_TRUSTED, "home", now - 2000)
        // Another person signs in on the same phone without the teardown having run: what was written for the first one is not shown, and is replaced.
        uid = "uid2"
        assertTrue(log.facts("miffA").isEmpty())
        assertNull(storage.text)
        log.record("miffA", PresenceValue.OUT, null, now - 1000)
        assertEquals(1, log.facts("miffA").size)
    }

    @Test
    fun aFileThatCannotBeRead_isErased_andTheChartStartsAgain() {
        storage.text = "{ not json"
        assertTrue(log.facts("miffA").isEmpty())
        assertNull(storage.text)
    }

    @Test
    fun forgettingAPlace_turnsItsFactsIntoUnknown_neverIntoElsewhere() {
        log.record("miffA", PresenceValue.OUT, null, now - 4000)
        log.record("miffA", PresenceValue.IN_TRUSTED, "gone", now - 3000)
        log.record("miffA", PresenceValue.OUT, null, now - 2000)
        log.record("miffA", PresenceValue.IN_TRUSTED, "home", now - 1000)
        log.forgetPlace("miffA", "gone")
        assertEquals(
            listOf(PresenceValue.OUT, PresenceValue.UNKNOWN, PresenceValue.OUT, PresenceValue.IN_TRUSTED),
            log.facts("miffA").map { it.presence },
        )
        assertFalse(storage.text!!.contains("gone"))
    }

    @Test
    fun forgettingAMiffOrKeepingOnlySome_erasesTheOthers() {
        log.record("miffA", PresenceValue.IN_TRUSTED, "home", now - 3000)
        log.record("miffB", PresenceValue.IN_TRUSTED, "home", now - 3000)
        log.record("miffC", PresenceValue.IN_TRUSTED, "home", now - 3000)
        log.forgetMiff("miffA")
        assertTrue(log.facts("miffA").isEmpty())
        assertEquals(1, log.facts("miffB").size)
        log.retainOnly(setOf("miffC"))
        assertTrue(log.facts("miffB").isEmpty())
        assertEquals(1, log.facts("miffC").size)
        log.retainOnly(emptySet())
        assertNull(storage.text)
    }

    @Test
    fun clearing_erasesEverything_andTheChartIsToldTheJournalChanged() {
        log.record("miffA", PresenceValue.IN_TRUSTED, "home", now - 3000)
        val before = log.revision.value
        log.clear()
        assertNull(storage.text)
        assertTrue(log.facts("miffA").isEmpty())
        assertTrue(log.revision.value > before)
    }

    @Test
    fun theJournalIsBounded_neverAnUnboundedFile() {
        val small = StoredPresenceLog(storage, { uid }, { now }, maxFacts = 50)
        repeat(200) { i ->
            small.record(
                "miffA",
                if (i % 2 ==
                    0
                ) {
                    PresenceValue.IN_TRUSTED
                } else {
                    PresenceValue.OUT
                },
                if (i % 2 == 0) "home" else null,
                now - 1000 + i,
            )
        }
        assertEquals(50, small.facts("miffA").size)
        // The newest are the ones kept.
        assertEquals(now - 1000 + 199, small.facts("miffA").last().atMillis)
    }

    @Test
    fun theStorageIsAPrivatePreferenceFile_excludedFromEveryBackupAndTransfer() {
        // TS-3: the journal is a shared preference (not a file in the shared storage) and both rule files exclude every shared preference, from the cloud backup and from the transfer.
        val source = File("src/main/java/com/tomessa/miff/data/timeshare/StoredPresenceLog.kt").readText()
        assertTrue(source.contains("getSharedPreferences(\"miff_presence_log\", Context.MODE_PRIVATE)"))
        assertFalse(
            source.contains("externalFilesDir") || source.contains("getExternalFilesDir") ||
                source.contains("MODE_WORLD"),
        )
        val backup = File("src/main/res/xml/backup_rules.xml").readText()
        val extraction = File("src/main/res/xml/data_extraction_rules.xml").readText()
        assertTrue(backup.contains("<exclude domain=\"sharedpref\" />"))
        assertEquals(2, Regex("<exclude domain=\"sharedpref\" />").findAll(extraction).count())
        // Nothing re-includes anything.
        assertFalse(backup.contains("<include") || extraction.contains("<include"))
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("@xml/data_extraction_rules") && manifest.contains("@xml/backup_rules"))
    }
}
