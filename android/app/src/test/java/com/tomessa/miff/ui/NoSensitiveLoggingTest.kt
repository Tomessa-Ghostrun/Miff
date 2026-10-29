package com.tomessa.miff.ui

import com.tomessa.miff.data.session.SessionManager
import com.tomessa.miff.domain.auth.PendingConsent
import com.tomessa.miff.ui.auth.ConsentPanelUiState
import com.tomessa.miff.ui.auth.ConsentPanelViewModel
import com.tomessa.miff.ui.auth.SignInUiState
import com.tomessa.miff.ui.auth.SignInViewModel
import com.tomessa.miff.ui.auth.SignUpUiState
import com.tomessa.miff.ui.auth.SignUpViewModel
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Criteria X5 and X6 (code part): no password, e-mail or token can reach a log because the app code does not log at all.
 * If a log is ever needed, it goes through a reviewed wrapper and this test is updated with it.
 */
class NoSensitiveLoggingTest {
    private val forbidden =
        listOf(
            Regex("""\bandroid\.util\.Log\b"""),
            Regex("""\bLog\.[dviwe]\("""),
            Regex("""\bprintln\("""),
            Regex("""\bprintStackTrace\("""),
            Regex("""\bTimber\b"""),
            Regex("""\.(setCustomKey|setUserId)\("""),
        )

    private fun sourceRoots() =
        listOf("src/main/java", "src/debug/java", "src/release/java")
            .map {
                File(it)
            }.filter { it.exists() }

    private fun isCode(line: String): Boolean {
        val trimmed = line.trimStart()
        return !trimmed.startsWith("*") && !trimmed.startsWith("//") && !trimmed.startsWith("/*")
    }

    @Test
    fun theAppCodeNeverLogs() {
        val roots = sourceRoots()
        assertTrue("source folders not found from ${File(".").absolutePath}", roots.isNotEmpty())
        val offenders = mutableListOf<String>()
        roots.forEach { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    if (isCode(line) &&
                        forbidden.any { it.containsMatchIn(line) }
                    ) {
                        offenders += "${file.name}:${index + 1}"
                    }
                }
            }
        }
        assertTrue("logging found: $offenders", offenders.isEmpty())
    }

    @Test
    fun noAuthClassKeepsAPassword() {
        listOf(
            SignUpUiState::class.java,
            SignInUiState::class.java,
            ConsentPanelUiState::class.java,
            SignUpViewModel::class.java,
            SignInViewModel::class.java,
            ConsentPanelViewModel::class.java,
            SessionManager::class.java,
            PendingConsent::class.java,
        ).forEach { type ->
            val names = type.declaredFields.map { it.name.lowercase() }
            assertTrue(
                "${type.simpleName} has a password-like field: $names",
                names.none {
                    "password" in it ||
                        "secret" in it
                },
            )
        }
    }
}
