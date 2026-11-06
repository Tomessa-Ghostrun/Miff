package com.tomessa.miff.data.update

import android.content.Context

/*
 * PRIVACY, for this file: what the phone remembers about the Update, and nothing else: counts and moments (never a photo, a place, a name or a Miff),
 * for the guard-rail of the publications (20 a day, 30 s apart), the explanation of the camera shown once, the tip of the double tap shown three times,
 * and the moment of the last photo published (to remove it from the database when it expires). A private file, excluded from the backups
 * (res/xml/backup_rules.xml and data_extraction_rules.xml exclude every shared preference), erased at sign-out.
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

    /** The moment the photo now in the database was published (the clean-up removes it 24 h later), or null. */
    fun photoPublishedMillis(): Long?

    fun clearPhotoPublished()

    /** The explanation before the first request of the camera was shown. */
    fun cameraExplained(): Boolean

    fun markCameraExplained()

    /** How many times the viewfinder was opened (for the tip of the double tap: the first three times). */
    fun viewfinderOpenings(): Int

    fun countViewfinderOpening()

    fun clear()
}

class SharedPreferencesPublishLedger(
    context: Context,
) : PublishLedger {
    private val prefs = context.applicationContext.getSharedPreferences("miff_update", Context.MODE_PRIVATE)

    override fun countedDay(): String? = prefs.getString(KEY_DAY, null)

    override fun countToday(): Int = prefs.getInt(KEY_COUNT, 0)

    override fun lastPublishMillis(): Long? = prefs.getLong(KEY_LAST, NONE).takeIf { it != NONE }

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
            .putLong(KEY_PHOTO, nowMillis)
            .commit()
    }

    override fun photoPublishedMillis(): Long? = prefs.getLong(KEY_PHOTO, NONE).takeIf { it != NONE }

    override fun clearPhotoPublished() {
        prefs.edit().remove(KEY_PHOTO).commit()
    }

    override fun cameraExplained(): Boolean = prefs.getBoolean(KEY_EXPLAINED, false)

    override fun markCameraExplained() {
        prefs.edit().putBoolean(KEY_EXPLAINED, true).commit()
    }

    override fun viewfinderOpenings(): Int = prefs.getInt(KEY_OPENINGS, 0)

    override fun countViewfinderOpening() {
        prefs.edit().putInt(KEY_OPENINGS, prefs.getInt(KEY_OPENINGS, 0) + 1).apply()
    }

    override fun clear() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val KEY_DAY = "count_day"
        const val KEY_COUNT = "count_today"
        const val KEY_LAST = "last_publish"
        const val KEY_PHOTO = "photo_published"
        const val KEY_EXPLAINED = "camera_explained"
        const val KEY_OPENINGS = "viewfinder_openings"
        const val NONE = Long.MIN_VALUE
    }
}
