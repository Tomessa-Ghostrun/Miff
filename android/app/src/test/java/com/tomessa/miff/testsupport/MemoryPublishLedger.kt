package com.tomessa.miff.testsupport

import com.tomessa.miff.data.update.PublishLedger

/**
 * What the phone remembers about the Update, in memory, for the tests. The note of the photo sent is tied to an account: [photoUid] null means "any account"
 * (a test that only sets [photoAt]); [notePhotoSent] sets it, and [photoSentMillis] answers only for that account, as the real one does.
 */
class MemoryPublishLedger : PublishLedger {
    var day: String? = null
    var count = 0
    var last: Long? = null
    var photoAt: Long? = null
    var photoUid: String? = null
    var explained = false
    var doubleTap = false

    /** Every note written, in order: ("uid", millis) or ("clear", 0). */
    val notes = mutableListOf<Pair<String, Long>>()

    override fun countedDay() = day

    override fun countToday() = count

    override fun lastPublishMillis() = last

    override fun recordPublication(
        nowMillis: Long,
        dayKey: String,
        count: Int,
    ) {
        day = dayKey
        this.count = count
        last = nowMillis
    }

    override fun photoSentMillis(uid: String): Long? = if (photoUid == null || photoUid == uid) photoAt else null

    override fun notePhotoSent(
        uid: String,
        millis: Long,
    ) {
        photoUid = uid
        photoAt = millis
        notes += uid to millis
    }

    override fun clearPhotoSent() {
        photoUid = null
        photoAt = null
        notes += "clear" to 0L
    }

    override fun cameraExplained() = explained

    override fun markCameraExplained() {
        explained = true
    }

    override fun doubleTapUsed() = doubleTap

    override fun markDoubleTapUsed() {
        doubleTap = true
    }

    /** Sign-out: everything but the note of the photo, as the real one. */
    override fun clear() {
        day = null
        count = 0
        last = null
        explained = false
        // the double tap is about the installation: kept
    }
}
