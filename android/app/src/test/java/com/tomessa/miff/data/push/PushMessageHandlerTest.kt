package com.tomessa.miff.data.push

import com.tomessa.miff.domain.notification.CircleNewsPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What happens to a DATA message of the server: understood and handed on, or ignored without a trace. */
class PushMessageHandlerTest {
    private var uid: String? = "me"
    private val received = mutableListOf<Pair<String, CircleNewsPayload>>()
    private val handler = PushMessageHandler(currentUid = { uid }, onCircleNews = { u, n -> received += u to n })

    private val valid =
        mapOf(
            "type" to "n9_update",
            "miffId" to "miffAAAA1234",
            "authorUid" to "author1",
            "authorFirstName" to "Amandine",
            "sentAt" to "1800000000000",
        )

    @Test
    fun aValidMessageIsHandedToTheNotificationsWithTheAccountInUse() {
        assertTrue(handler.handle(valid))
        assertEquals("me", received.single().first)
        assertEquals("Amandine", received.single().second.authorFirstName)
    }

    @Test
    fun anUnknownTypeIsIgnored() {
        assertFalse(handler.handle(valid + ("type" to "n10_other")))
        assertFalse(handler.handle(emptyMap()))
        assertTrue(received.isEmpty())
    }

    @Test
    fun aMalformedMessageIsIgnored() {
        assertFalse(handler.handle(valid + ("miffId" to "../x")))
        assertFalse(handler.handle(valid - "sentAt"))
        assertFalse(handler.handle(valid + ("authorFirstName" to "‮")))
        assertTrue(received.isEmpty())
    }

    @Test
    fun nobodySignedIn_theMessageIsIgnored() {
        uid = null
        assertFalse(handler.handle(valid))
        assertTrue(received.isEmpty())
    }

    @Test
    fun aMessageWithManyKeysIsNotOurs() {
        val many = valid + (1..30).associate { "k$it" to "v" }
        assertFalse(handler.handle(many))
        assertTrue(received.isEmpty())
    }
}
