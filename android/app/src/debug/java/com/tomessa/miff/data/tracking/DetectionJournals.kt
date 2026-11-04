package com.tomessa.miff.data.tracking

import android.content.Context
import com.tomessa.miff.domain.tracking.DetectionJournal
import com.tomessa.miff.domain.tracking.JournalKind
import com.tomessa.miff.domain.tracking.JournalLine
import java.io.File

/*
 * DEBUG BUILD ONLY (criterion VIE2). The release build has another file of the same name in src/release with a journal that does nothing: this
 * writer, and the marker below, are NOT in the published app (the CI looks for the marker in the release bundle and in releaseLike, and a unit
 * test checks the source). The journal holds the TYPE of an event, the identifier of a place, the time and the battery: no coordinate, no address,
 * no name (the only things that can be written are the values of JournalKind). It is in a private file of the app, excluded from the backups,
 * erased at sign-out, and kept short.
 */
object DetectionJournals {
    /** Looked for in the release bundle by the CI: it must never be there. */
    const val MARKER = "DETECTIONJOURNAL"

    fun create(context: Context): DetectionJournal =
        FileDetectionJournal(File(context.applicationContext.filesDir, "detection-journal.txt"))
}

class FileDetectionJournal(
    private val file: File,
    private val maxLines: Int = 400,
    private val clock: () -> Long = System::currentTimeMillis,
) : DetectionJournal {
    private val lock = Any()

    override fun record(
        kind: JournalKind,
        placeId: String?,
        batteryPct: Int?,
    ) {
        synchronized(lock) {
            try {
                val lines = (read() + format(clock(), kind, placeId, batteryPct)).takeLast(maxLines)
                file.writeText(lines.joinToString("\n", postfix = "\n"))
            } catch (e: Exception) {
                // The journal is a convenience: it never breaks the detection.
            }
        }
    }

    override fun lines(): List<JournalLine> =
        synchronized(lock) {
            read().mapNotNull { parse(it) }
        }

    override fun clear() {
        synchronized(lock) { file.delete() }
    }

    private fun read(): List<String> = if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()

    private fun format(
        at: Long,
        kind: JournalKind,
        placeId: String?,
        battery: Int?,
    ): String = listOf(at.toString(), kind.name, placeId.orEmpty(), battery?.toString().orEmpty()).joinToString(";")

    private fun parse(line: String): JournalLine? {
        val parts = line.split(";")
        if (parts.size != 4) return null
        val at = parts[0].toLongOrNull() ?: return null
        val kind = JournalKind.entries.firstOrNull { it.name == parts[1] } ?: return null
        return JournalLine(at, kind, parts[2].ifEmpty { null }, parts[3].toIntOrNull())
    }
}
