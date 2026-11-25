package com.tomessa.miff.domain.billing

import com.tomessa.miff.domain.subscription.SubscriptionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * What the offer shows is what Google Play gives (ticket premium-achat-reel.md section 6 point 1, criteria AN1, AN3, AN4): the prices and the texts of the prices come from the
 * store, the 7-day trial exists only when Google offers it to this account, and a plan that is not the one the screen promises is not offered.
 */
class MiffPlusOffersTest {
    private fun phase(
        micros: Long,
        text: String,
        period: String,
        recurrence: PhaseRecurrence,
        currency: String = "EUR",
    ) = StorePhase(micros, currency, text, period, recurrence)

    private val annualBase =
        StoreOffer("annuel", null, "tok-annual", listOf(phase(59_880_000, "59,88 €", "P1Y", PhaseRecurrence.INFINITE)))
    private val annualTrial =
        StoreOffer(
            "annuel",
            "essai-7j",
            "tok-trial",
            listOf(
                phase(0, "Gratuit", "P1W", PhaseRecurrence.FINITE),
                phase(59_880_000, "59,88 €", "P1Y", PhaseRecurrence.INFINITE),
            ),
        )
    private val monthlyBase =
        StoreOffer("mensuel", null, "tok-monthly", listOf(phase(6_990_000, "6,99 €", "P1M", PhaseRecurrence.INFINITE)))

    @Test
    fun anEligibleAccount_getsTheTrialOnTheAnnualPlan_andBothPlans_withTheStoresTexts() {
        val offers = chooseOffers(listOf(annualBase, annualTrial, monthlyBase))
        assertNotNull(offers)
        offers!!
        assertEquals("tok-trial", offers.annualTrial?.offerToken)
        assertEquals("tok-annual", offers.annual.offerToken)
        assertEquals("tok-monthly", offers.monthly.offerToken)
        // The text of the price is the store's, character for character.
        assertEquals("59,88 €", offers.annual.price.formatted)
        assertEquals("59,88 €", offers.annualTrial?.price?.formatted)
        assertEquals("6,99 €", offers.monthly.price.formatted)
        assertEquals(59_880_000L, offers.annual.price.micros)
    }

    @Test
    fun aSevenDayTrialWrittenAsSevenDays_isTheTrialToo() {
        val sevenDays =
            annualTrial.copy(
                phases =
                    listOf(phase(0, "Gratuit", "P7D", PhaseRecurrence.FINITE)) + annualTrial.phases.drop(1),
            )
        assertEquals("tok-trial", chooseOffers(listOf(annualBase, sevenDays, monthlyBase))?.annualTrial?.offerToken)
    }

    @Test
    fun anAccountThatAlreadyHadTheTrial_isOfferedNoTrial_andNoError() {
        // Google leaves the trial out of the list for an account that is not eligible (criterion AN3).
        val offers = chooseOffers(listOf(annualBase, monthlyBase))
        assertNotNull(offers)
        assertNull(offers!!.annualTrial)
        assertEquals("tok-annual", offers.annual.offerToken)
    }

    @Test
    fun aTrialThatIsNotSevenDays_isNotShown_theTextsWouldBeWrong() {
        val threeDays =
            annualTrial.copy(
                phases =
                    listOf(phase(0, "Gratuit", "P3D", PhaseRecurrence.FINITE)) + annualTrial.phases.drop(1),
            )
        assertNull(chooseOffers(listOf(annualBase, threeDays, monthlyBase))!!.annualTrial)
    }

    @Test
    fun theMonthlyPlanNeverGetsATrial() {
        val monthlyTrial =
            StoreOffer(
                "mensuel",
                "essai",
                "tok-monthly-trial",
                listOf(
                    phase(0, "Gratuit", "P1W", PhaseRecurrence.FINITE),
                    phase(6_990_000, "6,99 €", "P1M", PhaseRecurrence.INFINITE),
                ),
            )
        val offers = chooseOffers(listOf(annualBase, monthlyBase, monthlyTrial))!!
        assertNull(offers.annualTrial)
        assertEquals("tok-monthly", offers.monthly.offerToken)
    }

    @Test
    fun theBaseOffer_isTheOneWithoutAnOfferId_whateverTheOrder() {
        val promo =
            StoreOffer(
                "annuel",
                "promo",
                "tok-promo",
                listOf(phase(49_990_000, "49,99 €", "P1Y", PhaseRecurrence.INFINITE)),
            )
        assertEquals("tok-annual", chooseOffers(listOf(promo, annualBase, monthlyBase))!!.annual.offerToken)
        assertEquals("tok-annual", chooseOffers(listOf(monthlyBase, annualBase, promo))!!.annual.offerToken)
    }

    @Test
    fun aMissingPlan_meansNotAvailable() {
        assertNull(chooseOffers(emptyList()))
        assertNull(chooseOffers(listOf(annualBase)))
        assertNull(chooseOffers(listOf(monthlyBase)))
        assertNull(chooseOffers(listOf(annualTrial, monthlyBase)))
    }

    @Test
    fun aPlanThatIsNotTheOnePromised_isNotOffered() {
        val sixMonths = annualBase.copy(phases = listOf(phase(59_880_000, "59,88 €", "P6M", PhaseRecurrence.INFINITE)))
        assertNull(chooseOffers(listOf(sixMonths, monthlyBase)))
        val yearlyAsMonthly =
            monthlyBase.copy(
                phases = listOf(phase(6_990_000, "6,99 €", "P1Y", PhaseRecurrence.INFINITE)),
            )
        assertNull(chooseOffers(listOf(annualBase, yearlyAsMonthly)))
    }

    @Test
    fun noPrice_noCurrency_noText_orNoToken_isNotOffered() {
        fun annualWith(
            p: StorePhase = annualBase.phases.single(),
            token: String = "tok-annual",
        ) = annualBase.copy(offerToken = token, phases = listOf(p))
        assertNull(chooseOffers(listOf(annualWith(phase(0, "59,88 €", "P1Y", PhaseRecurrence.INFINITE)), monthlyBase)))
        assertNull(
            chooseOffers(
                listOf(
                    annualWith(phase(59_880_000, "59,88 €", "P1Y", PhaseRecurrence.INFINITE, currency = "")),
                    monthlyBase,
                ),
            ),
        )
        assertNull(
            chooseOffers(listOf(annualWith(phase(59_880_000, " ", "P1Y", PhaseRecurrence.INFINITE)), monthlyBase)),
        )
        assertNull(chooseOffers(listOf(annualWith(token = ""), monthlyBase)))
        // An endless phase is needed: a plan with only a finite phase has no price that comes back.
        assertNull(
            chooseOffers(listOf(annualWith(phase(59_880_000, "59,88 €", "P1Y", PhaseRecurrence.FINITE)), monthlyBase)),
        )
    }

    @Test
    fun theMonthlyEquivalent_isComputedFromTheStoresAnnualPrice_notWrittenAnywhere() {
        val annual = chooseOffers(listOf(annualBase, monthlyBase))!!.annual.price
        assertTrue(annual.perMonth(Locale.FRANCE)!!.contains("4,99"))
        // The same code in another currency: nothing is hard-coded to euros.
        val dollars =
            annualBase.copy(
                phases = listOf(phase(71_880_000, "$71.88", "P1Y", PhaseRecurrence.INFINITE, currency = "USD")),
            )
        assertTrue(
            chooseOffers(listOf(dollars, monthlyBase))!!
                .annual.price
                .perMonth(Locale.US)!!
                .contains("5.99"),
        )
    }

    @Test
    fun choiceByButton() {
        val offers = chooseOffers(listOf(annualBase, annualTrial, monthlyBase))!!
        assertEquals("tok-trial", offers.choice(PlanKind.ANNUAL_TRIAL)?.offerToken)
        assertEquals("tok-annual", offers.choice(PlanKind.ANNUAL)?.offerToken)
        assertEquals("tok-monthly", offers.choice(PlanKind.MONTHLY)?.offerToken)
        assertNull(chooseOffers(listOf(annualBase, monthlyBase))!!.choice(PlanKind.ANNUAL_TRIAL))
    }

    @Test
    fun theTokenIsNeverPrinted() {
        assertFalse(annualBase.toString().contains("tok-annual"))
        assertFalse(chooseOffers(listOf(annualBase, monthlyBase))!!.annual.toString().contains("tok-annual"))
    }

    // ---- where to manage -------------------------------------------------------------------------------------------------------------------

    @Test
    fun theManagementPageIsTheOneOfTheProductInGooglePlay() {
        assertEquals(
            "https://play.google.com/store/account/subscriptions?sku=miff_plus&package=com.tomessa.miff",
            MiffPlusProducts.manageUrl("com.tomessa.miff"),
        )
        assertEquals(
            MiffPlusProducts.manageUrl("p"),
            MiffPlusProducts.manageUrlFor(SubscriptionSource.GOOGLE_PLAY, "p"),
        )
        assertEquals(
            MiffPlusProducts.APP_STORE_MANAGE_URL,
            MiffPlusProducts.manageUrlFor(SubscriptionSource.APP_STORE, "p"),
        )
        assertNull(MiffPlusProducts.manageUrlFor(SubscriptionSource.MANUAL, "p"))
        assertNull(MiffPlusProducts.manageUrlFor(SubscriptionSource.OTHER, "p"))
        assertTrue(MiffPlusProducts.APP_STORE_MANAGE_URL.startsWith("https://"))
    }

    // ---- no price written by hand (criterion AN1) --------------------------------------------------------------------------------------------

    @Test
    fun noPriceIsWrittenInTheAppSources_norInAnyTranslation() {
        val roots = listOf("src/main/java", "src/release/java").map(::File)
        // A price as people write it: an amount with two decimals followed by (or preceded by) a currency sign or code.
        val price = Regex("""(\d+[.,]\d{2}\s?(€|\$|£|EUR|USD|GBP))|((€|\$|£)\s?\d+[.,]\d{2})""")
        val offenders =
            roots
                .flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" }.toList() }
                .filter { file ->
                    file.readLines().any { line ->
                        val code = line.trimStart()
                        !code.startsWith("*") && !code.startsWith("//") && !code.startsWith("/*") &&
                            price.containsMatchIn(code)
                    }
                }.map { it.name }
        assertEquals("a price written by hand in: $offenders", emptyList<String>(), offenders)
        val translations = File("src/main/res").walkTopDown().filter { it.isFile && it.name == "strings.xml" }.toList()
        assertTrue(translations.size >= 6)
        translations.forEach { file ->
            val text = file.readText()
            // The only percentage ever written is the decided "presque 30 %" (paywall_annual_savings); no amount with a currency.
            val withoutComments = text.replace(Regex("<!--[\\s\\S]*?-->"), "")
            assertFalse("a price in ${file.parentFile.name}", price.containsMatchIn(withoutComments))
        }
    }
}
