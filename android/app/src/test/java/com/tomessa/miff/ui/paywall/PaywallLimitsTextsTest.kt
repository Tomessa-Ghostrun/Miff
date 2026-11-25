package com.tomessa.miff.ui.paywall

import com.tomessa.miff.domain.place.PlaceLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The limits of the free plan written in the cards of the offer (decision of the PM, 2026-11-25: "Limite de 3 lieux sinon", "Limite d'une seule Miff sinon") must stay true to the numbers of
 * the app: the number of places is never written in a translation (it is the argument `%1$d`, given [PlaceLimits.FREE_PLACES]); the single Miff is a fixed sentence in every language, so this test
 * pins the number it speaks of ([PlaceLimits.FREE_MIFFS]) and breaks, on purpose, if someone changes it without rereading the six texts.
 */
class PaywallLimitsTextsTest {
    private val res = File("src/main/res")
    private val languages = listOf("values", "values-fr", "values-es", "values-it", "values-pt", "values-de")

    private fun xml(dir: String) = File(res, "$dir/strings.xml").readText()

    @Test
    fun theNumbersOfTheFreePlan_areThoseTheTextsSpeakOf() {
        assertEquals(
            "the sentences say \"3 places\" through their argument; re-read them if this changes",
            3,
            PlaceLimits.FREE_PLACES,
        )
        assertEquals(
            "the sentences say \"a single Miff\" in six languages; re-read them if this changes",
            1,
            PlaceLimits.FREE_MIFFS,
        )
    }

    @Test
    fun thePlacesLimit_takesItsNumberAsArgument_inEveryLanguage_neverWrittenInTheText() {
        for (dir in languages) {
            val block =
                Regex("""<plurals name="paywall_feature_places_limit">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
                    .find(xml(dir))
                    ?.groupValues
                    ?.get(1)
            assertTrue("$dir has the plural", block != null)
            val items = Regex("""<item quantity="(\w+)">([^<]*)</item>""").findAll(block!!).toList()
            assertTrue("$dir has its quantities", items.size >= 2)
            items.forEach {
                assertTrue("$dir ${it.groupValues[1]} takes the number as an argument", "%1\$d" in it.groupValues[2])
                assertFalse(
                    "$dir ${it.groupValues[1]} never writes the number itself",
                    Regex("""\b[0-9]+\b""").containsMatchIn(it.groupValues[2].replace("%1\$d", "")),
                )
            }
        }
    }

    @Test
    fun theSingleMiffSentence_andTheTrialLink_existInEveryLanguage_withoutADigit() {
        for (dir in languages) {
            for (key in listOf("paywall_feature_miffs_limit", "paywall_free_trial_link")) {
                val value = Regex("""<string name="$key">([^<]*)</string>""").find(xml(dir))?.groupValues?.get(1)
                assertTrue("$dir $key", !value.isNullOrBlank())
                assertFalse(
                    "$dir $key has no digit (the number of Miffs is words)",
                    Regex("[0-9]").containsMatchIn(value!!),
                )
            }
        }
    }
}
