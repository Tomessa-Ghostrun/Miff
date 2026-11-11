@file:Suppress("ktlint:standard:max-line-length") // long literals: the strings of the workflows and of the build file are checked as written

package com.tomessa.miff.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards of the release preparation (docs/release/checklist-android.md), checked on the SOURCE so that a later change cannot weaken them unnoticed:
 * - the version is never frozen (versionName from the changelog, versionCode derived or given), and the changelog keeps the heading the build reads;
 * - the signature of a release comes from environment variables only, never from a file or a literal of the repository, and never from the debug key;
 * - the release workflow is manual only, runs from main only, says "not configured" without the secrets, never prints a secret;
 * - the checks of the release bundle (debug-only code, permissions, 16 KB pages) are the same in both workflows.
 *
 * The existing guards of the release bundle (no debug App Check provider, no diagnostic screen, no review window, permissions) stay in android.yml and in
 * their own tests (ReviewPromptReleaseTest, TrackingPrivacyTest, PermissionPrivacyTest).
 */
class ReleaseSigningGuardTest {
    private fun normalised(file: File): String = file.readText().replace(Regex("\\s+"), " ")

    private val gradle = normalised(File("build.gradle.kts"))
    private val androidWorkflow = File("../../.github/workflows/android.yml").readText()
    private val releaseWorkflow = File("../../.github/workflows/android-release.yml").readText()
    private val bundleScript = File("../ci/check-release-bundle.sh").readText()

    // --- versions ----------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun theVersionIsNeverFrozen_itComesFromTheChangelogOrFromTheBuildLine() {
        assertFalse("versionCode must not be a literal", Regex("""versionCode = \d""").containsMatchIn(gradle))
        assertFalse("versionName must not be a literal", Regex("""versionName = """").containsMatchIn(gradle))
        assertTrue(gradle.contains("versionCode = appVersionCode"))
        assertTrue(gradle.contains("versionName = appVersionName"))
        assertTrue(gradle.contains("""providers.gradleProperty("miff.versionCode")"""))
        assertTrue(gradle.contains("""providers.gradleProperty("miff.versionName")"""))
    }

    @Test
    fun theChangelogKeepsTheHeadingTheVersionNameIsReadFrom() {
        val changelog = File("../CHANGELOG.md").readLines()
        val first = changelog.firstOrNull { it.startsWith("## [") }
        assertTrue("android/CHANGELOG.md must start its entries with \"## [x.y.z]\"", first != null)
        assertTrue(Regex("""^## \[\d+\.\d+\.\d+]""").containsMatchIn(first!!))
        // The same pattern in the build and in the release workflow.
        assertTrue(gradle.contains("""(?m)^## \[(\d+\.\d+\.\d+)]"""))
        assertTrue(releaseWorkflow.contains("^## \\[\\([0-9]"))
    }

    @Test
    fun aSignedReleaseMustBeGivenItsVersionCodeOnPurpose_andTheWorkflowGivesIt() {
        assertTrue(gradle.contains("A signed release needs an explicit version code"))
        assertTrue(gradle.contains("uploadSigningConfigured && explicitVersionCode == null"))
        assertTrue(releaseWorkflow.contains("""-Pmiff.versionCode="${'$'}VERSION_CODE""""))
        // The number comes from the run number of the workflow: always growing (Google Play refuses a number it has already seen).
        assertTrue(releaseWorkflow.contains("code=${'$'}(( RUN_NUMBER + OFFSET ))"))
    }

    // --- signature ---------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun theSignatureComesFromTheEnvironmentOnly_neverFromAFileOrALiteral() {
        listOf(
            "MIFF_UPLOAD_STORE_FILE",
            "MIFF_UPLOAD_STORE_PASSWORD",
            "MIFF_UPLOAD_KEY_ALIAS",
            "MIFF_UPLOAD_KEY_PASSWORD",
        ).forEach {
            assertTrue("$it must be read from the environment", gradle.contains("\"$it\""))
        }
        assertTrue(gradle.contains("environmentVariable(name)"))
        // No literal password or alias, no properties file of keys, no Gradle property carrying a secret.
        assertFalse(Regex("""(storePassword|keyPassword|keyAlias) = "[^"]""").containsMatchIn(gradle))
        assertFalse(
            gradle.contains("keystore.properties") || gradle.contains("key.properties") ||
                gradle.contains("Properties()"),
        )
        assertFalse(Regex("""gradleProperty\("[^"]*(?i:password|keystore|storeFile)[^"]*"\)""").containsMatchIn(gradle))
        // Half configured = build fails; nothing configured = unsigned release (what every pull request builds).
        assertTrue(gradle.contains("The upload signature is half configured"))
        assertTrue(gradle.contains("""if (uploadSigningConfigured) { create("release")"""))
        assertTrue(
            gradle.contains("""if (uploadSigningConfigured) signingConfig = signingConfigs.getByName("release")"""),
        )
    }

    @Test
    fun theReleaseIsNeverSignedWithTheDebugKey_andReleaseLikeKeepsItsOwn() {
        val releaseBlock =
            gradle
                .substringAfter(
                    "release { isMinifyEnabled",
                ).substringBefore("""create("releaseLike")""")
        assertTrue("could not find the release build type", releaseBlock.contains("isShrinkResources = true"))
        assertFalse(releaseBlock.contains("""getByName("debug")"""))
        // releaseLike (test APK signed with the debug key) is set AFTER its copy of release, so it never inherits the upload key.
        val like = gradle.substringAfter("""create("releaseLike") {""").substringBefore("compileOptions")
        assertTrue(like.indexOf("initWith(") < like.indexOf("""signingConfig = signingConfigs.getByName("debug")"""))
    }

    @Test
    fun noKeyFileOrPasswordIsInTheRepository() {
        val root = File("../..")
        val forbidden =
            root
                .walkTopDown()
                .onEnter {
                    it.name != ".git" && it.name != "node_modules" && it.name != "build" && it.name != ".gradle"
                }.filter { it.isFile && Regex("""(?i).*\.(jks|keystore|p12|pfx|aab)$""").matches(it.name) }
                .map { it.path }
                .toList()
        assertEquals(emptyList<String>(), forbidden)
        val ignore = File("../../.gitignore").readText()
        listOf("*.jks", "*.keystore", "key.properties", "keystore.properties", "*.aab").forEach {
            assertTrue("$it must stay ignored", ignore.contains(it))
        }
    }

    // --- the release workflow ----------------------------------------------------------------------------------------------------------------------

    @Test
    fun theReleaseWorkflowIsManualOnly() {
        val triggers = releaseWorkflow.substringAfter("\non:\n").substringBefore("\npermissions:")
        assertTrue(triggers.contains("workflow_dispatch:"))
        listOf(
            "pull_request",
            "pull_request_target",
            "push:",
            "schedule:",
            "workflow_run",
            "release:",
            "repository_dispatch",
        ).forEach {
            assertFalse("the release workflow must not be triggered by $it", triggers.contains(it))
        }
        // Not even a mention of the dangerous trigger anywhere in the file's code (comments excluded).
        val code = releaseWorkflow.lines().filter { !it.trimStart().startsWith("#") }.joinToString("\n")
        assertFalse(code.contains("pull_request_target"))
        // It refuses to run from any branch but main, and says so.
        assertTrue(code.contains("refs/heads/main"))
        assertTrue(code.contains("Branche refusée"))
    }

    @Test
    fun theReleaseWorkflowSaysNotConfiguredWithoutTheSecrets_andBuildsNothingThen() {
        assertTrue(releaseWorkflow.contains("Release Android non configurée"))
        assertTrue(releaseWorkflow.contains("MIFF_RELEASE_CONFIGURED=false"))
        val secrets =
            listOf(
                "GOOGLE_SERVICES_JSON_B64",
                "ANDROID_UPLOAD_KEYSTORE_B64",
                "ANDROID_UPLOAD_STORE_PASSWORD",
                "ANDROID_UPLOAD_KEY_ALIAS",
                "ANDROID_UPLOAD_KEY_PASSWORD",
            )
        secrets.forEach {
            assertTrue(
                "$it must be checked",
                releaseWorkflow.contains("""[ -n "${'$'}$it" ] || missing="${'$'}missing $it""""),
            )
        }
        // Every step that does something with the code or the key waits for the gate.
        val steps = releaseWorkflow.substringAfter("    steps:\n").split(Regex("(?m)^      - name: ")).drop(1)
        val ungated =
            steps.filter {
                !it.contains("if: env.MIFF_RELEASE_CONFIGURED == 'true'") &&
                    !it.contains("if: always()")
            }
        // The only step that is not behind the gate (or a cleanup that always runs) is the gate itself.
        assertEquals(1, ungated.size)
        assertTrue(ungated.single().startsWith("Contrôler le déclenchement"))
    }

    @Test
    fun theReleaseWorkflowNeverPrintsASecret_andNeverBuildsFromTheCache() {
        val code = releaseWorkflow.lines().filter { !it.trimStart().startsWith("#") }
        // A secret is only ever given through `env:` (NAME: ${{ secrets.X }}), never inside a command.
        val envAssignment = Regex("""^\s+[A-Z0-9_]+: \$\{\{ secrets\.[A-Z0-9_]+ }}\s*$""")
        val inCommand = code.filter { it.contains("{{ secrets.") && !envAssignment.matches(it) }
        assertEquals(emptyList<String>(), inCommand)
        assertFalse(
            code.any {
                it.contains("set -x") || it.contains("set -o xtrace") || it.contains("--debug") ||
                    it.contains("--info")
            },
        )
        val printing = Regex("""\b(cat|echo|printf)\b.*""")
        val secretNames = Regex("""\$\{?(ANDROID_UPLOAD|MIFF_UPLOAD_STORE_PASSWORD|MIFF_UPLOAD_KEY)""")
        assertFalse(
            code.any {
                printing.containsMatchIn(it) && secretNames.containsMatchIn(it) &&
                    !it.contains("base64 -d")
            },
        )
        val uploaded = releaseWorkflow.substringAfter("name: miff-release-aab").substringBefore("- name: Résumé")
        assertFalse("the keystore must never be uploaded", uploaded.contains(".jks"))
        // Passwords are given to keytool through the environment, never on the command line.
        assertTrue(releaseWorkflow.contains("-storepass:env MIFF_UPLOAD_STORE_PASSWORD"))
        assertFalse(Regex("""-(storepass|keypass) [^:]""").containsMatchIn(releaseWorkflow))
        // Passwords never reach a configuration cache on disk, a signed bundle never reaches a build cache.
        assertTrue(releaseWorkflow.contains("--no-configuration-cache --no-build-cache"))
        // The decoded files disappear at the end, whatever happens.
        val cleanup = releaseWorkflow.substringAfter("Supprimer la configuration Firebase et la clé d'envoi")
        assertTrue(
            cleanup.contains("if: always()") && cleanup.contains("rm -f android/app/google-services.json") &&
                cleanup.contains("miff-upload.jks"),
        )
        // The artifact is the bundle alone, kept briefly (a public repository: any signed-in GitHub account can download an artifact).
        assertTrue(releaseWorkflow.contains("path: android/app/build/outputs/bundle/release/app-release.aab"))
        assertTrue(releaseWorkflow.contains("retention-days: 14"))
    }

    @Test
    fun theSignedBundleIsCheckedBeforeItIsHandedOver() {
        assertTrue(releaseWorkflow.contains("jarsigner -verify"))
        assertTrue(releaseWorkflow.contains("Android Debug"))
        assertTrue(
            releaseWorkflow.contains(
                "bash ci/check-release-bundle.sh app/build/outputs/bundle/release/app-release.aab",
            ),
        )
        assertTrue(releaseWorkflow.contains("com.tomessa.miff"))
        assertTrue(releaseWorkflow.contains("miff-3d283"))
    }

    // --- the checks of the bundle stay the same in both workflows ----------------------------------------------------------------------------------

    @Test
    fun theBundleScriptLooksForTheSameDebugMarkersAndAllowsTheSamePermissionsAsTheBuildOfEveryPullRequest() {
        listOf(
            "DIAGNOSTICPROBE",
            "DebugAppCheck",
            "DETECTIONJOURNAL",
            "REVIEWPROMPTPROBE",
            "STARTUPMETRICSOVERLAY",
            "PlayCoreDialogWrapperActivity",
            "default_web_client_id",
            "USE_BIOMETRIC",
        ).forEach {
            assertTrue("android.yml no longer mentions $it", androidWorkflow.contains(it))
            assertTrue("check-release-bundle.sh does not look for $it", bundleScript.contains(it))
        }
        val allowed = Regex("""allowed='([^']+)'""")
        val inAndroid = allowed.find(androidWorkflow)?.groupValues?.get(1)
        val inScript = allowed.find(bundleScript)?.groupValues?.get(1)
        assertTrue("allowed permissions not found", inAndroid != null && inScript != null)
        assertEquals(
            "the permissions allowed in android.yml and in check-release-bundle.sh must be the same list",
            inAndroid,
            inScript,
        )
        val detect = Regex("""grep -a -o -E '(android\[\.]permission\[\.]\([^']+)'""")
        assertEquals(detect.find(androidWorkflow)?.groupValues?.get(1), detect.find(bundleScript)?.groupValues?.get(1))
    }

    @Test
    fun the16KbCheckAndTheVersionCheckRunOnEveryPullRequest() {
        assertTrue(androidWorkflow.contains("ci/check-elf-16kb.mjs"))
        assertTrue(androidWorkflow.contains("node --test ci/check-elf-16kb.test.mjs"))
        assertTrue(bundleScript.contains("check-elf-16kb.mjs"))
        assertTrue(androidWorkflow.contains("aapt2"))
        assertTrue(androidWorkflow.contains("versionCode"))
    }
}
