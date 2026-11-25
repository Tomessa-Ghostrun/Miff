package com.tomessa.miff.data.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The purchase of Miff+, guarded on the SOURCES (ticket premium-achat-reel.md sections 5, 6, 11; criteria AN1, AN12, AN15): the switch of the purchase is OFF in every build that can be
 * distributed (the offer stays "Miff+ arrive bientôt"), the FAKE store exists in the debug build only, the app holds no key and no secret, nothing personal is given to the store, no
 * token is logged or sent in an event, and the release workflow only turns the purchase on when the PM asks for it.
 */
class PremiumPurchaseGuardTest {
    private fun normalised(file: File): String = file.readText().replace(Regex("\\s+"), " ")

    private val gradle = normalised(File("build.gradle.kts"))
    private val main = File("src/main/java/com/tomessa/miff")

    private fun kotlinFiles(dir: File) = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun code(file: File) =
        file
            .readLines()
            .filter {
                val line = it.trimStart()
                !line.startsWith("*") && !line.startsWith("//") && !line.startsWith("/*")
            }.joinToString("\n")

    private val billingFiles = kotlinFiles(File(main, "data/billing")) + kotlinFiles(File(main, "domain/billing"))

    // ---- the switch -----------------------------------------------------------------------------------------------------------------------

    @Test
    fun theFlagIsFalseByDefault_trueInDebugOnly_andARelease_refusesTrueUnlessAskedOnPurpose() {
        assertTrue(
            gradle.contains("""buildConfigField("boolean", "PREMIUM_PURCHASE", premiumPurchaseProperty ?: "false")"""),
        )
        val debugBlock = gradle.substringAfter("debug {").substringBefore("release {")
        assertTrue(debugBlock.contains("""premiumPurchaseProperty ?: "true""""))
        val restOfBuildTypes = gradle.substringAfter("release {").substringBefore("compileOptions")
        assertFalse(restOfBuildTypes.contains("""premiumPurchaseProperty ?: "true""""))
        val guard = gradle.substringAfter("val purchaseOnInRelease")
        assertTrue(guard.contains("miff.allowPremiumPurchaseInRelease"))
        assertTrue(
            guard.contains("preReleaseBuild") && guard.contains("preReleaseLikeBuild") &&
                guard.contains("GradleException"),
        )
    }

    @Test
    fun theFakeStoreFlagIsFalseEverywhereButTheDebugBuild() {
        assertTrue(gradle.contains("""buildConfigField("boolean", "BILLING_FAKE", "false")"""))
        val debugBlock = gradle.substringAfter("debug {").substringBefore("release {")
        assertTrue(debugBlock.contains("""billingFakeProperty ?: "true""""))
        val restOfBuildTypes = gradle.substringAfter("release {").substringBefore("compileOptions")
        assertFalse(restOfBuildTypes.contains("billingFakeProperty"))
    }

    @Test
    fun everythingIsBuiltBehindTheFlag_inTheContainer() {
        val container = normalised(File(main, "AppContainer.kt"))
        assertTrue(
            "the coordinator exists only when the purchase is on",
            container.contains("val premium: PremiumCoordinator? by lazy { if (BuildConfig.PREMIUM_PURCHASE) {"),
        )
        assertTrue(
            "the stack is built on first use",
            container.contains("private val billingStack: BillingStack by lazy { BillingStacks.create(context) }"),
        )
        assertTrue(
            container.contains(
                "if (BuildConfig.PREMIUM_PURCHASE) billingStack.entitlements else FirestoreSubscriptionStore()",
            ),
        )
        assertTrue(
            "the purge at the deletion of the account only when the purchase is on",
            container.contains(
                "subscriptionData = if (BuildConfig.PREMIUM_PURCHASE) billingStack.backend::purgeSubscriptionData else null",
            ),
        )
        // Nothing else builds a gateway or a backend than the factories of the stack (one in src/debug, one in src/release).
        val builders =
            (kotlinFiles(main) + kotlinFiles(File("src/debug/java")) + kotlinFiles(File("src/release/java")))
                .filter {
                    Regex(
                        """(?<!class )\b(PlayBillingGateway|FirebasePurchaseBackend)\(""",
                    ).containsMatchIn(code(it))
                }.map { it.name }
        assertEquals(listOf("BillingStacks.kt", "BillingStacks.kt"), builders)
    }

    @Test
    fun theOfferOpensWithPricesOnlyThroughTheCoordinator_whichIsNullWhileThePurchaseIsOff() {
        val navigation = normalised(File(main, "ui/place/PlaceNavigation.kt"))
        assertTrue(
            "the coordinator of the container goes to the single offer route",
            navigation.contains(
                "paywallRoute(container.analyticsTracker, container.paywallClock, navController, container.premium)",
            ),
        )
        assertTrue(
            "without a coordinator the offer is the one without purchase",
            navigation.contains("if (premium == null) {"),
        )
    }

    // ---- the fake store lives in the debug build only ----------------------------------------------------------------------------------------

    @Test
    fun theFakeStoreIsInTheDebugSourcesOnly_andCarriesTheMarkerOfTheCi() {
        val fake = File("src/debug/java/com/tomessa/miff/data/billing/FakeBilling.kt")
        assertTrue(fake.exists())
        assertTrue(fake.readText().contains("FAKEBILLINGPROBE"))
        val leaks =
            (kotlinFiles(main) + kotlinFiles(File("src/release/java"))).filter {
                val text = it.readText()
                text.contains("FakeBilling") || text.contains("FAKEBILLINGPROBE")
            }
        assertEquals(emptyList<File>(), leaks)
        // The release factory builds the REAL stack, whatever the flags.
        val release = File("src/release/java/com/tomessa/miff/data/billing/BillingStacks.kt").readText()
        assertTrue(release.contains("PlayBillingGateway(context)") && release.contains("FirebasePurchaseBackend()"))
        assertFalse(release.contains("BuildConfig"))
    }

    @Test
    fun theCiLooksForTheMarkerInTheReleaseBundleAndInTheReleaseLikeApk() {
        val workflow = File("../../.github/workflows/android.yml").readText()
        val script = File("../ci/check-release-bundle.sh").readText()
        assertEquals(
            "android.yml: the bundle and the releaseLike APK",
            2,
            Regex("""grep -a -c -E '[^']*FAKEBILLINGPROBE'""").findAll(workflow).count(),
        )
        assertEquals(
            "check-release-bundle.sh",
            1,
            Regex("""grep -a -c -E '[^']*FAKEBILLINGPROBE'""").findAll(script).count(),
        )
    }

    // ---- the release workflow ------------------------------------------------------------------------------------------------------------------

    @Test
    fun theReleaseWorkflowTurnsThePurchaseOnOnlyWhenAsked_withBothProperties() {
        val workflow = File("../../.github/workflows/android-release.yml").readText()
        assertTrue(workflow.contains("premium_purchase:"))
        // false by default, a menu with two values, validated before use.
        val input = workflow.substringAfter("premium_purchase:").substringBefore("permissions:")
        assertTrue(input.contains("type: choice") && input.contains("default: \"false\""))
        assertTrue(workflow.contains("""[[ "${'$'}PREMIUM_PURCHASE" =~ ^(true|false)${'$'} ]]"""))
        assertTrue(workflow.contains("-Pmiff.premiumPurchase=true -Pmiff.allowPremiumPurchaseInRelease=true"))
        // Never built from the raw input inside a command: the value goes through the environment.
        assertFalse(Regex("""run:[\s\S]{0,300}\$\{\{\s*inputs\.premium_purchase""").containsMatchIn(workflow))
    }

    // ---- security and privacy ----------------------------------------------------------------------------------------------------------------------

    @Test
    fun noKeyNoSecret_noAccountIdentifierGivenToTheStore() {
        val gateway = code(File(main, "data/billing/PlayBillingGateway.kt"))
        // The ONLY identifier given to Google Play is the random purchase id of the server.
        assertTrue(gateway.contains(".setObfuscatedAccountId(purchaseId)"))
        assertFalse("no profile id", gateway.contains("setObfuscatedProfileId"))
        listOf("uid", "email", "Email", "currentUser", "displayName", "phone").forEach {
            assertFalse("the gateway must not know '$it'", Regex("""\b$it\b""").containsMatchIn(gateway))
        }
        // No credential in the package: no service account, no API key, no secret.
        billingFiles.forEach { file ->
            val text = code(file)
            assertFalse(
                file.name,
                Regex(
                    """(?i)(api[_ ]?key|secret|private[_ ]?key|service[_ ]?account|\.p8|BEGIN [A-Z ]*KEY)""",
                ).containsMatchIn(text),
            )
        }
        // The purchase id is never derived from anything of the person: it only comes from the server and must have the shape of a UUID.
        assertTrue(code(File(main, "data/billing/PurchaseBackend.kt")).contains("isPurchaseId(id)"))
    }

    @Test
    fun nothingIsLogged_nothingIsSentAsAnAnalyticsEvent_inTheBillingPackages() {
        billingFiles.forEach { file ->
            val text = code(file)
            assertFalse(
                "${file.name} logs",
                Regex(
                    """\b(Log\.|println\(|Timber|Logger|printStackTrace|recordException|FirebaseCrashlytics)""",
                ).containsMatchIn(text),
            )
            assertFalse(
                "${file.name} sends an event",
                Regex("""AnalyticsEvent|AnalyticsTracker|logEvent|\.track\(""").containsMatchIn(text),
            )
        }
    }

    @Test
    fun theTokenIsNeverInAToString_orAnExceptionMessage() {
        val purchase = code(File(main, "data/billing/BillingGateway.kt"))
        assertTrue(
            purchase.contains(
                "override fun toString(): String = \"OwnedPurchase(\$state, acknowledged=\$acknowledged)\"",
            ),
        )
        billingFiles.forEach { file ->
            assertFalse(file.name, Regex("""throw \w+\([^)]*(token|Token)""").containsMatchIn(code(file)))
        }
    }

    @Test
    fun theFunctionsAreCalledOverTheRegionOfTheProject_andNothingIsPlainHttp() {
        val backend = code(File(main, "data/billing/PurchaseBackend.kt"))
        assertTrue(backend.contains("""const val REGION = "europe-west9""""))
        billingFiles.forEach { assertFalse(it.name, code(it).contains("http://")) }
        // The names of the three callable functions are the ones of the server (backend/functions/src).
        assertTrue(
            backend.contains(""""preparerAchat"""") && backend.contains(""""synchroniserAchat"""") &&
                backend.contains(""""supprimerDonneesAbonnement""""),
        )
    }
}
