package com.tomessa.miff.domain.notification

/*
 * N9, "{Prénom} a donné des nouvelles" (ticket docs/tickets/notification-update-membres.md): the notification of a push sent by the SERVER when a member of a Miff
 * publishes an update. Pure rules (no Android class): the validation of the message that comes from the network, and the keys of the notification.
 *
 * PRIVACY: what travels in the message (built by the server, backend/functions/src/notifRegles.ts, `construireMessage`) is a type, the identifier of the Miff, the identifier of the author, the first name of the author and a flag "quiet" (the quiet hours of the recipient, computed by the server). The server sends NO time: the time of RECEPTION is used. Never a photo, a
 * caption, a position, a place, an address, the name of the Miff or a family name. The first name is NOT trusted: it is cleaned and cut here (20 characters at most) even
 * though the server already did it, because this is the text that appears on a locked screen.
 */

/** A message of the server, once validated. Nothing else of the message is kept. */
class CircleNewsPayload(
    val miffId: String,
    /** The author, when the server gave it: the notification of one author replaces the previous one. Null: replaced per Miff. */
    val authorUid: String?,
    /** Cleaned and cut: at most [CircleNewsParser.MAX_FIRST_NAME_CHARS] graphemes (the "…" included). Never empty. */
    val authorFirstName: String,
    /**
     * When this phone RECEIVED the message (milliseconds since 1970): the server sends no time. The rule "a message of more than 2 hours is only a silent line" is therefore
     * carried by the life of the message at FCM (the server gives it a time to live of 2 hours, `DUREE_DE_VIE_MS`): a message that arrives is at most 2 hours old.
     */
    val sentAtMillis: Long,
    /** The server says it is the quiet hours of the recipient (key `quiet` = "1"): the quiet channel, if "Nuit calme" is on. */
    val serverQuiet: Boolean = false,
    /** The identifier FCM gave the message, cleaned: the same message delivered twice makes one line. Null when there is none. */
    val messageId: String? = null,
) {
    /** Redacted: a name and identifiers are not for a log. */
    override fun toString(): String = "CircleNewsPayload(redacted)"
}

object CircleNewsParser {
    /** The only type of message understood in this version; any other is ignored (a later version of the server may send more). */
    const val TYPE = "circle_update"

    const val KEY_TYPE = "type"
    const val KEY_MIFF_ID = "miffId"
    const val KEY_AUTHOR_UID = "authorUid"
    const val KEY_FIRST_NAME = "firstName"
    const val KEY_QUIET = "quiet"

    /**
     * The first name shown: 20 GRAPHEMES at most, the final "…" included (what the server does: 19 and "…"; ticket section 4). A grapheme is what a person sees as one
     * character: an emoji of a family (several code points joined by U+200D) counts for ONE.
     */
    const val MAX_FIRST_NAME_CHARS = 20

    /**
     * What the server may send for the first name, in UTF-16 units: 20 graphemes can be long (a family emoji is 11 units, with skin tones about 25), so the limit is wide; the data of
     * a message is 4 KB at most anyway. Beyond this the message is suspect and is ignored.
     */
    private const val MAX_RAW_FIRST_NAME_CHARS = 512

    private val identifier = Regex("^[A-Za-z0-9_-]{1,128}$")
    private val messageIdShape = Regex("^[A-Za-z0-9_.:%-]{1,80}$")

    /**
     * The message, or null when it must be ignored: unknown type, a missing or malformed field, a first name that is empty once cleaned. Unknown keys (the ones FCM adds
     * itself) are ignored. STRICT on purpose: a message that does not look exactly right shows nothing.
     */
    fun parse(
        data: Map<String, String>,
        receivedAtMillis: Long,
        messageId: String? = null,
    ): CircleNewsPayload? {
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
        // "quiet" is "0" or "1" exactly (the server writes nothing else); absent is "not quiet"; any other value ignores the message.
        val quiet =
            when (data[KEY_QUIET]) {
                null, "0" -> false
                "1" -> true
                else -> return null
            }
        if (receivedAtMillis <= 0L) return null
        return CircleNewsPayload(
            miffId,
            authorUid,
            name,
            receivedAtMillis,
            quiet,
            messageId?.takeIf {
                messageIdShape.matches(it)
            },
        )
    }

    /**
     * The first name as it may appear on a locked screen: control, format (direction marks that could reorder the sentence, but not the joiner of emojis), separator, private and unassigned
     * characters are removed, every run of spaces becomes one, then it is cut at [MAX_FIRST_NAME_CHARS] graphemes with "…". Null when nothing is left.
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

                // U+200D (zero width joiner) is KEPT, like the server does: it joins the parts of an emoji of a family or of a person with a job (never alone at the start).
                Character.FORMAT -> {
                    if (codePoint == ZERO_WIDTH_JOINER && kept.isNotEmpty() && !pendingSpace) {
                        kept.appendCodePoint(codePoint)
                    }
                }

                Character.CONTROL -> {
                    // Tab, line feed and carriage return count as a space; every other control character is dropped.
                    if (codePoint == 0x09 || codePoint == 0x0A || codePoint == 0x0D) pendingSpace = kept.isNotEmpty()
                }

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
        val ends = graphemeEnds(cleaned)
        if (ends.size <= MAX_FIRST_NAME_CHARS) return cleaned
        // Like the server: the first 19 graphemes, then "…" (20 in all). A name already cut by the server (19 and "…") has 20 graphemes and comes out unchanged.
        return cleaned.substring(0, ends[MAX_FIRST_NAME_CHARS - 2]).trimEnd() + ELLIPSIS
    }

    /** The end offset of each grapheme of [text] (the system's rules of the characters seen as one: accents, emoji sequences, flags). */
    private fun graphemeEnds(text: String): List<Int> {
        val boundaries = java.text.BreakIterator.getCharacterInstance()
        boundaries.setText(text)
        val ends = mutableListOf<Int>()
        var end = boundaries.next()
        while (end != java.text.BreakIterator.DONE) {
            ends += end
            end = boundaries.next()
        }
        return ends
    }

    private const val ZERO_WIDTH_JOINER = 0x200D

    /** What is cut at the end of a long first name. */
    const val ELLIPSIS = "…"
}

/** The identifiers of N9: one notification of the tray per author (a new one replaces the old), one line of the centre per message. */
object CircleNewsKeys {
    private const val PREFIX = "n9|"

    /** The tag of the notification in the tray: per author when known, else per Miff. */
    fun trayTag(news: CircleNewsPayload): String =
        if (news.authorUid != null) "${PREFIX}u|${news.authorUid}" else "${PREFIX}m|${news.miffId}"

    /** The key of the line of the centre, also the key of deduplication (a message delivered twice makes one line): the tag and the identifier of the message (else the time of reception). */
    fun centerId(news: CircleNewsPayload): String = "${trayTag(news)}|${news.messageId ?: news.sentAtMillis}"

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
