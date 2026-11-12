package com.tomessa.miff.domain.notification

/*
 * N9, "{Prénom} a donné des nouvelles" (ticket docs/tickets/notification-update-membres.md): the notification of a push sent by the SERVER when a member of a Miff
 * publishes an update. Pure rules (no Android class): the validation of the message that comes from the network, and the keys of the notification.
 *
 * PRIVACY: what travels in the message is a type, the identifier of the Miff, the identifier of the author, the first name of the author and a time. Never a photo, a
 * caption, a position, a place, an address, the name of the Miff or a family name. The first name is NOT trusted: it is cleaned and cut here (20 characters at most) even
 * though the server already did it, because this is the text that appears on a locked screen.
 */

/** A message of the server, once validated. Nothing else of the message is kept. */
class CircleNewsPayload(
    val miffId: String,
    /** The author, when the server gave it: the notification of one author replaces the previous one. Null: replaced per Miff. */
    val authorUid: String?,
    /** Cleaned and cut: at most [CircleNewsParser.MAX_FIRST_NAME_CHARS] characters, then "…" when it was longer. Never empty. */
    val authorFirstName: String,
    /** When the update was published (the server's clock, in milliseconds since 1970). */
    val sentAtMillis: Long,
) {
    /** Redacted: a name and identifiers are not for a log. */
    override fun toString(): String = "CircleNewsPayload(redacted)"
}

object CircleNewsParser {
    /** The only type of message understood in this version; any other is ignored (a later version of the server may send more). */
    const val TYPE = "n9_update"

    const val KEY_TYPE = "type"
    const val KEY_MIFF_ID = "miffId"
    const val KEY_AUTHOR_UID = "authorUid"
    const val KEY_FIRST_NAME = "authorFirstName"
    const val KEY_SENT_AT = "sentAt"

    /** The first name shown: 20 characters (code points) at most, then "…" (ticket section 4). */
    const val MAX_FIRST_NAME_CHARS = 20

    /** What the server may send for the first name (it cuts at 20 and adds "…"): beyond this the message is suspect and is ignored. */
    private const val MAX_RAW_FIRST_NAME_CHARS = 64

    private val identifier = Regex("^[A-Za-z0-9_-]{1,128}$")
    private val milliseconds = Regex("^[0-9]{12,13}$")

    /** 2020-01-01 and 2100-01-01 in milliseconds: anything outside is not a time of this century. */
    private const val EARLIEST_MILLIS = 1_577_836_800_000L
    private const val LATEST_MILLIS = 4_102_444_800_000L

    /**
     * The message, or null when it must be ignored: unknown type, a missing or malformed field, a first name that is empty once cleaned. Unknown keys (the ones FCM adds
     * itself) are ignored. STRICT on purpose: a message that does not look exactly right shows nothing.
     */
    fun parse(data: Map<String, String>): CircleNewsPayload? {
        if (data[KEY_TYPE] != TYPE) return null
        val miffId = data[KEY_MIFF_ID]?.takeIf { identifier.matches(it) } ?: return null
        val authorRaw = data[KEY_AUTHOR_UID]
        // Absent or empty: no author (replacement per Miff). Present but malformed: the whole message is ignored.
        val authorUid =
            if (authorRaw.isNullOrEmpty()) {
                null
            } else {
                authorRaw.takeIf { identifier.matches(it) } ?: return null
            }
        val rawName = data[KEY_FIRST_NAME] ?: return null
        if (rawName.length > MAX_RAW_FIRST_NAME_CHARS) return null
        val name = cleanFirstName(rawName) ?: return null
        val sentAt =
            data[KEY_SENT_AT]
                ?.takeIf { milliseconds.matches(it) }
                ?.toLongOrNull()
                ?.takeIf { it in EARLIEST_MILLIS..LATEST_MILLIS }
                ?: return null
        return CircleNewsPayload(miffId, authorUid, name, sentAt)
    }

    /**
     * The first name as it may appear on a locked screen: control, format (direction marks that could reorder the sentence), separator, private and unassigned
     * characters are removed, every run of spaces becomes one, then it is cut at [MAX_FIRST_NAME_CHARS] characters with "…". Null when nothing is left.
     * Idempotent: a name already cut by the server (20 characters and "…") comes out unchanged.
     */
    fun cleanFirstName(raw: String): String? {
        val kept = StringBuilder()
        var pendingSpace = false
        var index = 0
        while (index < raw.length) {
            val codePoint = raw.codePointAt(index)
            index += Character.charCount(codePoint)
            when (Character.getType(codePoint).toByte()) {
                Character.SPACE_SEPARATOR -> {
                    pendingSpace = kept.isNotEmpty()
                }

                Character.CONTROL -> {
                    // Tab, line feed and carriage return count as a space; every other control character is dropped.
                    if (codePoint == 0x09 || codePoint == 0x0A || codePoint == 0x0D) pendingSpace = kept.isNotEmpty()
                }

                Character.FORMAT,
                Character.LINE_SEPARATOR,
                Character.PARAGRAPH_SEPARATOR,
                Character.SURROGATE,
                Character.PRIVATE_USE,
                Character.UNASSIGNED,
                -> {
                    Unit
                }

                else -> {
                    if (pendingSpace) kept.append(' ')
                    pendingSpace = false
                    kept.appendCodePoint(codePoint)
                }
            }
        }
        val cleaned = kept.toString()
        if (cleaned.isEmpty()) return null
        val count = cleaned.codePointCount(0, cleaned.length)
        if (count <= MAX_FIRST_NAME_CHARS) return cleaned
        val end = cleaned.offsetByCodePoints(0, MAX_FIRST_NAME_CHARS)
        return cleaned.substring(0, end).trimEnd() + ELLIPSIS
    }

    /** What is cut at the end of a long first name. */
    const val ELLIPSIS = "…"
}

/** The identifiers of N9: one notification of the tray per author (a new one replaces the old), one line of the centre per message. */
object CircleNewsKeys {
    private const val PREFIX = "n9|"

    /** The tag of the notification in the tray: per author when known, else per Miff. */
    fun trayTag(news: CircleNewsPayload): String =
        if (news.authorUid != null) "${PREFIX}u|${news.authorUid}" else "${PREFIX}m|${news.miffId}"

    /** The key of the line of the centre, also the key of deduplication (a message delivered twice makes one line): the tag and the time of the message. */
    fun centerId(news: CircleNewsPayload): String = "${trayTag(news)}|${news.sentAtMillis}"

    fun isCircleNews(centerId: String): Boolean = centerId.startsWith(PREFIX)

    /** The tag of the tray that goes with a line of the centre (reading the line takes the notification out of the tray). */
    fun trayTagOf(centerId: String): String =
        if (isCircleNews(centerId)) centerId.substringBeforeLast('|') else centerId
}

/** An identifier of a Miff that comes from outside the app (a tap on a notification): checked before anything is done with it. */
object MiffIdShape {
    private val shape = Regex("^[A-Za-z0-9_-]{1,64}$")

    fun isValid(id: String?): Boolean = id != null && shape.matches(id)
}
