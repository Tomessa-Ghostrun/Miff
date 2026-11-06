package com.tomessa.miff.data.update

import android.content.Context

/*
 * PRIVACY, for this file: what the phone remembers about the Update, and nothing else: counts and moments (never a photo, a place, a name or a Miff),
 * for the guard-rail of the publications (20 a day, 30 s apart), the explanation of the camera shown once, the tip of the double tap shown three times,
 * and the moment of the last photo sent (to remove it from the database when it expires). A private file, excluded from the backups
 * (res/xml/backup_rules.xml and data_extraction_rules.xml exclude every shared preference).
 *
 * The moment of the photo is tied to the ACCOUNT (its uid) and is NOT erased at sign-out: the photo stays in the database after a sign-out (only the stop of the
 * sharing, the leaving or the deletion of the account remove it), so the same account signing in again must still be able to clean it up (review C1).
 * The counts and the rest are erased at sign-out.
 */
interface PublishLedger {
    /** The day (key of the phone's day) and the number of publications counted for it. */
    fun countedDay(): String?

    fun countToday(): Int

    fun lastPublishMillis(): Long?

    /** Counts one publication made at [nowMillis] on the day [dayKey] ([count] after it). */
    fun recordPublication(
        nowMillis: Long,
        dayKey: String,
        count: Int,
    )

    /**
     * The moment the photo of [uid] now in the database was SENT (a note of another account is ignored), or null. It is written BEFORE the sending starts
     * ([notePhotoSent]): a sending that leaves after the screen gave up (a delay) is still cleaned up 24 h later (review C2).
     */
    fun photoSentMillis(uid: String): Long?

    fun notePhotoSent(
        uid: String,
        millis: Long,
    )

    /** Forgets the note (the photo was removed, or the sending is known not to have left). */
    fun clearPhotoSent()

    /** The explanation before the first request of the camera was shown. */
    fun cameraExplained(): Boolean

    fun markCameraExplained()

    /** How many times the viewfinder was opened (for the tip of the double tap: the first three times). */
    fun viewfinderOpenings(): Int

    fun countViewfinderOpening()

    /** Sign-out: everything but the note of the photo (see above). */
    fun clear()
}

class SharedPreferencesPublishLedger(
    context: Context,
) : PublishLedger {
    private val prefs = context.applicationContext.getSharedPreferences("miff_update", Context.MODE_PRIVATE)

    override fun countedDay(): String? = prefs.getString(KEY_DAY, null)

    override fun countToday(): Int = prefs.getInt(KEY_COUNT, 0)

    override fun lastPublishMillis(): Long? = prefs.getLong(KEY_LAST, NONE).takeIf { it != NONE }

    // apply(), not commit(): these are called from the main thread (the view model), a write to the disk must not block it (review C8).
    override fun recordPublication(
        nowMillis: Long,
        dayKey: String,
        count: Int,
    ) {
        prefs
            .edit()
            .putString(KEY_DAY, dayKey)
            .putInt(KEY_COUNT, count)
            .putLong(KEY_LAST, nowMillis)
            .apply()
    }

    override fun photoSentMillis(uid: String): Long? =
        if (prefs.getString(KEY_PHOTO_UID, null) == uid) prefs.getLong(KEY_PHOTO, NONE).takeIf { it != NONE } else null

    override fun notePhotoSent(
        uid: String,
        millis: Long,
    ) {
        prefs
            .edit()
            .putString(KEY_PHOTO_UID, uid)
            .putLong(KEY_PHOTO, millis)
            .apply()
    }

    override fun clearPhotoSent() {
        prefs
            .edit()
            .remove(KEY_PHOTO)
            .remove(KEY_PHOTO_UID)
            .apply()
    }

    override fun cameraExplained(): Boolean = prefs.getBoolean(KEY_EXPLAINED, false)

    override fun markCameraExplained() {
        prefs.edit().putBoolean(KEY_EXPLAINED, true).apply()
    }

    override fun viewfinderOpenings(): Int = prefs.getInt(KEY_OPENINGS, 0)

    override fun countViewfinderOpening() {
        prefs.edit().putInt(KEY_OPENINGS, prefs.getInt(KEY_OPENINGS, 0) + 1).apply()
    }

    override fun clear() {
        prefs
            .edit()
            .remove(KEY_DAY)
            .remove(KEY_COUNT)
            .remove(KEY_LAST)
            .remove(KEY_EXPLAINED)
            .remove(KEY_OPENINGS)
            .apply()
    }

    private companion object {
        const val KEY_DAY = "count_day"
        const val KEY_COUNT = "count_today"
        const val KEY_LAST = "last_publish"
        const val KEY_PHOTO = "photo_sent"
        const val KEY_PHOTO_UID = "photo_sent_uid"
        const val KEY_EXPLAINED = "camera_explained"
        const val KEY_OPENINGS = "viewfinder_openings"
        const val NONE = Long.MIN_VALUE
    }
}
