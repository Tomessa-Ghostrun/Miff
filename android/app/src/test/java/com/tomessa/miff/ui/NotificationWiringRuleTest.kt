package com.tomessa.miff.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The bell of the Feed must lead to the centre of notifications. The wiring lives in graphs bound to the container, which the UI tests cannot build: these checks read the
 * sources, so that a merge or a refactor that drops one line fails here (the bell once did nothing because the FeedScreen call forgot `notifications`).
 */
class NotificationWiringRuleTest {
    private fun source(path: String): String {
        val file = File("src/main/java/com/tomessa/miff/$path")
        assertTrue("source not found: ${file.absolutePath}", file.exists())
        return file.readText()
    }

    @Test
    fun theFeedScreenIsGivenTheNotifications_andTheBellOpensTheCentre() {
        val shell = source("ui/main/MainShell.kt")
        val call = shell.substringAfter("FeedScreen(").substringBefore("\n                                )")
        assertTrue(
            "FeedScreen must get the notifications of the host",
            call.contains("notifications = feed.notifications"),
        )
        assertTrue(
            "the bell opens the centre",
            shell.contains("onOpenCenter = { navController.openNotificationCenter() }"),
        )
        assertTrue(
            "the routes of the notifications are in the graph of the tabs",
            shell.contains("notificationRoutes(container, navController, scopeKey)"),
        )
    }

    @Test
    fun theHeaderDrawsTheBell_wiredToTheCallback() {
        val header = source("ui/feed/FeedHeader.kt")
        assertTrue(header.contains("NotificationBell("))
        assertTrue(header.contains("onClick = notifications.onOpenCenter"))
        val feed = source("ui/feed/FeedScreen.kt")
        assertTrue("the header receives the notifications", feed.contains("notifications = notifications,"))
    }
}
