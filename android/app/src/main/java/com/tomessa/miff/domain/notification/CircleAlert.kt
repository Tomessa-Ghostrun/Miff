package com.tomessa.miff.domain.notification

/*
 * ALERTE « Je suis en danger » (docs/tickets/alerte-danger.md 11, backend/data-model.md 2.19, function `notifierAlerte`, NOT DEPLOYED): the notification of a push sent by the SERVER when a
 * member of a Miff declares an alert (event `start`) or ends it by hand (event `end`). Pure rules (no Android class): the validation of the message that comes from the network, and the
 * keys of the notification.
 *
 * PRIVACY: what travels in the message is a type, an event, the identifier of the Miff, the identifier of the author and the first name of the author. NEVER a position, a place, the name
 * of the Miff, a family name or a photo (the name of the Miff could reveal a relation to somebody who reads the lock screen). The first name is NOT trusted: it is cleaned and cut here
 * (20 characters at most) like the one of N9, because it is the text that appears on a locked screen.
 *
 * The notification IGNORES the quiet hours, the daily cap and the switch "Quand un proche donne des nouvelles" (decision of the PM, D12 and D18: a comfort setting must not silence an
 * alert); only the general switch, the permission of the phone and the settings of the channel in the phone decide.
 */

enum class CircleAlertEvent { START, END }

/** A message of the server, once validated. Nothing else of the message is kept. */
class CircleAlertPayload(
    val event: CircleAlertEvent,
    val miffId: String,
    val authorUid: String,
    /** Cleaned and cut: at most [CircleNewsParser.MAX_FIRST_NAME_CHARS] graphemes. Never empty. */
    val authorFirstName: String,
    /** When this phone RECEIVED the message: the server sends no time (the message lives 2 hours at FCM). */
    val receivedAtMillis: Long,
    /** The identifier FCM gave the message, cleaned: the same message delivered twice makes one notification. Null when there is none. */
    val messageId: String? = null,
) {
    /** Redacted: a name and identifiers are not for a log. */
    override fun toString(): String = "CircleAlertPayload(redacted)"
}

object CircleAlertParser {
    /** The only type of this family; anything else is left to the other parsers. */
    const val TYPE = "circle_alert"

    const val KEY_TYPE = "type"
    const val KEY_EVENT = "event"
    const val KEY_MIFF_ID = "miffId"
    const val KEY_AUTHOR_UID = "authorUid"
    const val KEY_FIRST_NAME = "firstName"

    private const val MAX_RAW_FIRST_NAME_CHARS = 512
    private val identifier = Regex("^[A-Za-z0-9_-]{1,128}$")
    private val messageIdShape = Regex("^[A-Za-z0-9_.:%-]{1,80}$")

    /** The message, or null when it must be ignored: another type, an unknown event, a missing or malformed field, a first name that is empty once cleaned. STRICT on purpose. */
    fun parse(
        data: Map<String, String>,
        receivedAtMillis: Long,
        messageId: String? = null,
    ): CircleAlertPayload? {
        if (data[KEY_TYPE] != TYPE) return null
        val event =
            when (data[KEY_EVENT]) {
                "start" -> CircleAlertEvent.START
                "end" -> CircleAlertEvent.END
                else -> return null
            }
        val miffId = data[KEY_MIFF_ID]?.takeIf { identifier.matches(it) } ?: return null
        val authorUid = data[KEY_AUTHOR_UID]?.takeIf { identifier.matches(it) } ?: return null
        val rawName = data[KEY_FIRST_NAME] ?: return null
        if (rawName.length > MAX_RAW_FIRST_NAME_CHARS) return null
        val name = CircleNewsParser.cleanFirstName(rawName) ?: return null
        if (receivedAtMillis <= 0L) return null
        return CircleAlertPayload(
            event,
            miffId,
            authorUid,
            name,
            receivedAtMillis,
            messageId?.takeIf {
                messageIdShape.matches(it)
            },
        )
    }
}

/** The identifiers of the alert notification: ONE notification of the tray per author (the end REPLACES the start), one deduplication key per message. */
object CircleAlertKeys {
    private const val PREFIX = "al|"

    /** The tag of the notification in the tray: per author, so that the end of an alert replaces its start (the server uses the same collapse key). */
    fun trayTag(alert: CircleAlertPayload): String = "${PREFIX}u|${alert.authorUid}"

    /** The key of deduplication: a message delivered twice makes one notification. */
    fun centerId(alert: CircleAlertPayload): String =
        "${trayTag(alert)}|${alert.event.name.lowercase()}|${alert.messageId ?: alert.receivedAtMillis}"

    fun isCircleAlert(key: String): Boolean = key.startsWith(PREFIX)
}
