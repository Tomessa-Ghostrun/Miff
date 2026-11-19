package com.tomessa.miff.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * CONS-4-A (docs/tickets/consentement-localisation-historique.md, section 10): the texts of the consent version 4 on SOURCE, in French and English, and
 * what must stay true about them (criteria CA-1 to CA-5) and about the code behind them (CA-8, CA-12). No text may say "no history" without "on our servers",
 * since the phone keeps a local journal; no text may say that the journal is erased when the sharing stops (it is not: decision D16).
 */
class ConsentV4TextsTest {
    private val main = File("src/main/java")

    private fun strings(path: String): Map<String, String> =
        Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(File(path).readText())
            .associate { it.groupValues[1] to it.groupValues[2].replace("\\'", "'").replace("\\\"", "\"") }

    private val fr = strings("src/main/res/values-fr/strings.xml")
    private val en = strings("src/main/res/values/strings.xml")

    private val newKeys =
        listOf(
            "miff_consent3_photo_title",
            "miff_consent3_photo_body",
            "miff_consent3_local_title",
            "miff_consent3_local_body",
            "permission_location_local_title",
            "permission_location_local_body",
            "settings_privacy_time_erase_note",
            "settings_privacy_clarify_body",
            "miff_clarify_title",
            "miff_clarify_body_one",
            "miff_clarify_body_unnamed",
            "miff_clarify_body_many",
            "miff_clarify_continue",
            "miff_clarify_accept",
            "miff_clarify_later",
            "miff_clarify_saving",
            "miff_clarify_error_extra",
        )

    private fun consentKeys(language: Map<String, String>) =
        language.keys.filter {
            it.startsWith(
                "permission_location_",
            ) || it.startsWith("miff_consent3_") || it.startsWith("miff_renewal_") ||
                it.startsWith("miff_clarify_") || it == "settings_privacy_location_body" ||
                it.startsWith("settings_privacy_time_")
        }

    // --- CA-1: the same keys in both languages -----------------------------------------------------------------------------

    @Test
    fun everyNewKeyExists_inFrenchAndEnglish_withTheSameInsertedValues() {
        newKeys.forEach { key ->
            assertTrue("$key missing in French", fr.containsKey(key))
            assertTrue("$key missing in English", en.containsKey(key))
            assertEquals(
                key,
                Regex("%\\d\\$[sd]").findAll(fr.getValue(key)).count(),
                Regex("%\\d\\$[sd]").findAll(en.getValue(key)).count(),
            )
        }
        // The name of the Miff is the only inserted value, and only where a name is spoken.
        listOf("miff_clarify_body_one", "miff_renewal_intro_one").forEach {
            assertTrue(it, fr.getValue(it).contains("%1\$s") && en.getValue(it).contains("%1\$s"))
        }
        listOf("miff_clarify_body_unnamed", "miff_clarify_body_many").forEach {
            assertFalse(it, fr.getValue(it).contains("%1\$s") || en.getValue(it).contains("%1\$s"))
        }
    }

    // --- CA-2: no "no history" without "on our servers" -------------------------------------------------------------------------

    private val timeHistoryLabels =
        setOf("settings_privacy_time_title", "settings_privacy_time_erase", "settings_privacy_time_confirm_title")

    @Test
    fun noTextSaysNoHistory_withoutSayingOnOurServers_andNoOldSentenceIsLeft() {
        fr.forEach { (key, text) ->
            if (key in timeHistoryLabels) return@forEach
            if (text.contains("historique", ignoreCase = true) || text.contains("trajets", ignoreCase = true)) {
                assertTrue("$key says history without \"nos serveurs\": $text", text.contains("nos serveurs"))
            }
            assertFalse("$key still has the old sentence", text.contains("Pas d'historique"))
            assertFalse("$key still has the old sentence", text.contains("Aucun historique de trajets n'est gardé."))
        }
        en.forEach { (key, text) ->
            if (key in timeHistoryLabels) return@forEach
            if (text.contains("history", ignoreCase = true) || text.contains("trips", ignoreCase = true)) {
                assertTrue("$key says history without \"our servers\": $text", text.contains("our servers"))
            }
            assertFalse("$key still has the old sentence", text.contains("No history of trips is kept."))
        }
    }

    // --- CA-3: the four precisions stay ---------------------------------------------------------------------------------------

    @Test
    fun theFourPrecisionsStay_inTheLocationAndConsentTexts() {
        val frLocation = fr.filterKeys { it.startsWith("permission_location_") }.values.joinToString(" ")
        listOf("dernière position connue", "heure", "historique", "arrière-plan").forEach {
            assertTrue("permission_location_* lacks \"$it\"", frLocation.contains(it))
        }
        val enLocation = en.filterKeys { it.startsWith("permission_location_") }.values.joinToString(" ")
        listOf("last known location", "time", "history", "background").forEach {
            assertTrue("permission_location_* lacks \"$it\" (EN)", enLocation.contains(it))
        }
        val frConsent = fr.filterKeys { it.startsWith("miff_consent3_") }.values.joinToString(" ")
        listOf("dernière position connue", "heure", "historique", "app est fermée").forEach {
            assertTrue("miff_consent3_* lacks \"$it\"", frConsent.contains(it))
        }
    }

    @Test
    fun theGoogleDisclosurePhrase_isUnchanged_andStillSaysLocationBackgroundAppClosedAndTheFeatures() {
        val frText = fr.getValue("permission_location_disclosure")
        assertEquals(
            "Miff collecte la localisation de ton téléphone, y compris en arrière-plan, " +
                "même quand l'app est fermée ou que tu ne l'utilises pas, " +
                "pour repérer quand tu arrives dans un lieu de confiance ou que tu le quittes " +
                "et pour que ta Miff voie ton statut et ta dernière position connue.",
            frText,
        )
        val enText = en.getValue("permission_location_disclosure")
        assertTrue(
            "location" in enText && "background" in enText && "app is closed" in enText &&
                "last known location" in enText,
        )
    }

    // --- CA-4: words that are never used ----------------------------------------------------------------------------------------

    @Test
    fun noWordOfSurveillance_noLegalReviewClaim_inTheseTexts() {
        val keys = consentKeys(fr)
        assertTrue(keys.size >= 40)
        keys.forEach { key ->
            // "suivi" only in the negation "Ce n'est pas un suivi en direct".
            val withoutNegation = fr.getValue(key).replace("Ce n'est pas un suivi en direct", "")
            listOf("suivi", "surveillance", "habitude", "juriste", "conforme", "relu").forEach {
                assertFalse("$key contains \"$it\"", withoutNegation.contains(it, ignoreCase = true))
            }
        }
        consentKeys(en).forEach { key ->
            val withoutNegation = en.getValue(key).replace("This is not live tracking", "")
            listOf("tracking", "surveillance", "habit", "lawyer", "legal review", "compliant", "GDPR").forEach {
                assertFalse("$key contains \"$it\" (EN)", withoutNegation.contains(it, ignoreCase = true))
            }
        }
    }

    // --- CA-5: nothing false about the journal ---------------------------------------------------------------------------------

    @Test
    fun theLocalJournal_isNeverSaidErasedWhenTheSharingStops_andIsSaidNeverSent() {
        listOf(
            "miff_consent3_local_body",
            "permission_location_local_body",
            "miff_clarify_body_one",
            "settings_privacy_clarify_body",
        ).forEach {
            assertFalse(
                "$it must not say the journal goes when sharing stops",
                fr.getValue(it).contains("arrêtes de partager"),
            )
            assertFalse(it, en.getValue(it).contains("stop sharing"))
        }
        assertTrue(fr.getValue("settings_privacy_time_body").contains("Arrêter de partager ne l'efface pas"))
        assertTrue(en.getValue("settings_privacy_time_body").contains("Stopping sharing does not erase it"))
        listOf("miff_consent3_local_body", "permission_location_local_body", "settings_privacy_time_body").forEach {
            assertTrue(it, fr.getValue(it).contains("Rien n'est envoyé"))
            assertTrue(it, en.getValue(it).contains("Nothing is sent"))
        }
        // 35 days, no position, no address: the same numbers everywhere the journal is described.
        listOf("miff_consent3_local_body", "miff_clarify_body_one", "settings_privacy_time_body").forEach {
            assertTrue(it, fr.getValue(it).contains("35 jours"))
            assertTrue(it, en.getValue(it).contains("35 days"))
        }
    }

    @Test
    fun theErasureSentence_saysThePhotoAndItsPlaceAreErasedToo() {
        assertTrue(
            fr.getValue("miff_consent3_erase_body").contains("ta position et ta photo avec son endroit sont effacées"),
        )
        assertTrue(
            en.getValue("miff_consent3_erase_body").contains("your location and your photo with its place are erased"),
        )
    }

    @Test
    fun theCalmCard_saysEverythingContinues_onlyBecauseNothingIsPaused() {
        assertEquals("Tout continue comme avant.", fr.getValue("miff_clarify_continue"))
        assertEquals("Everything goes on as before.", en.getValue("miff_clarify_continue"))
        // Calm: no pause, no waiting, no obligation, no urgency.
        listOf(
            "miff_clarify_title",
            "miff_clarify_body_one",
            "miff_clarify_continue",
            "miff_clarify_accept",
            "miff_clarify_later",
            "settings_privacy_clarify_body",
        ).forEach {
            val text = fr.getValue(it).lowercase()
            listOf("pause", "en attente", "obligatoire", "urgent", "bloqu").forEach { word ->
                assertFalse("$it contains \"$word\"", text.contains(word))
            }
        }
        assertEquals("Une précision sur ce que tu partages", fr.getValue("miff_clarify_title"))
    }

    // --- CA-8 and CA-12: the thresholds did not move, the photo is not held back ---------------------------------------------------

    @Test
    fun thePhotoAndThePositionAreStillGatedByCoversPosition_neverByTheCurrentVersion() {
        listOf("data/update/PhotoStore.kt", "data/tracking/TrackingEngine.kt").forEach { path ->
            val source = File(main, "com/tomessa/miff/$path").readText()
            assertTrue("$path must keep using coversPosition", source.contains("coversPosition"))
            assertFalse(
                "$path must not require the current version (decision Q1 of the PM: a version 3 account still publishes a photo)",
                source.contains("MiffSharingConsent.VERSION") || source.contains("isRenewalDue") ||
                    source.contains("pausesSharing"),
            )
        }
        // The family of versions accepted by the rules did not move: 03 to 99.
        val models = File(main, "com/tomessa/miff/domain/miff/MiffModels.kt").readText()
        assertTrue(models.contains("""v(0[3-9]|[1-9][0-9])${'$'}"""))
    }

    @Test
    fun theCounterOfTheCalmCard_staysOnThePhone_noNetwork_noEvent_noLog() {
        val source = File(main, "com/tomessa/miff/data/miff/ClarifyAskStore.kt").readText()
        val code =
            source.lines().filter {
                !it.trimStart().startsWith("*") && !it.trimStart().startsWith("//") &&
                    !it.trimStart().startsWith("/*")
            }
        val joined = code.joinToString("\n")
        listOf("firebase", "Firestore", "Analytics", "Log.", "Crashlytics", "println").forEach {
            assertFalse("the counter of the calm card must not use \"$it\"", joined.contains(it, ignoreCase = true))
        }
    }
}
