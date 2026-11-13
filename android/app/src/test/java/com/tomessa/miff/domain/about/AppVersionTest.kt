package com.tomessa.miff.domain.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** AP3 and decision Q7: what the version line copies is the name and the build, and NOTHING else. */
class AppVersionTest {
    @Test
    fun theCopiedText_isTheNameOfTheAppThenTheVersionAndTheBuild() {
        assertEquals("Miff 0.39.0 (39000)", AppVersion.of("0.39.0", 39000).clipboardText)
    }

    @Test
    fun theBuildIsTheInternalNumber_asItIs() {
        val version = AppVersion.of("1.2.3", 1_002_003)
        assertEquals("1.2.3", version.name)
        assertEquals("1002003", version.build)
    }

    @Test
    fun aMissingName_isShownAsAQuestionMark_neverAsNullOrEmpty() {
        assertEquals("?", AppVersion.of(null, 5).name)
        assertEquals("?", AppVersion.of("  ", 5).name)
        assertEquals("Miff ? (5)", AppVersion.of(null, 5).clipboardText)
    }

    @Test
    fun theCopiedText_holdsNoTechnicalDataOfThePhoneOrTheAccount() {
        val text = AppVersion.of("0.39.0", 39000).clipboardText
        listOf("android", "sdk", "model", "device", "uid", "@").forEach {
            assertFalse("the copied version must not mention \"$it\"", text.contains(it, ignoreCase = true))
        }
    }
}
