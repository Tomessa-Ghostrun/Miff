package com.tomessa.miff.domain.startup

/**
 * The moments of a launch that the PM can be given as durations (internal and debug builds only show them).
 * Order of a cold start: the process is created, [APP_CREATE_START]..[APP_CREATE_END] (Application.onCreate), the activity,
 * [FIRST_FRAME] (the first image of the animation is drawn: blue and "M + i" alone), [HEART_VISIBLE] (the first image
 * where the heart can be seen), [CONTENT_MOUNTED] (the app is composed under the animation), [SPLASH_DONE].
 */
enum class StartupMark {
    APP_CREATE_START,
    APP_CREATE_END,
    ACTIVITY_CREATE_START,
    ACTIVITY_CONTENT_SET,
    FIRST_FRAME,
    HEART_VISIBLE,
    CONTENT_MOUNTED,
    SPLASH_DONE,
}

/**
 * What was measured during one launch. Times are in milliseconds since [baseElapsedMs] (the creation of the process for a
 * cold start, the creation of the activity otherwise). No personal data of any kind: only durations and a count.
 *
 * @property marks absolute times ([android.os.SystemClock.elapsedRealtime]) of the moments reached so far.
 * @property maxFrameGapMs the longest time between two images of the animation: a "freeze" of the main thread shows up here.
 */
data class StartupReport(
    val cold: Boolean,
    val baseElapsedMs: Long,
    val marks: Map<StartupMark, Long>,
    val maxFrameGapMs: Long,
    val frames: Int,
) {
    private fun since(mark: StartupMark): Long? = marks[mark]?.let { (it - baseElapsedMs).coerceAtLeast(0L) }

    private fun between(
        from: StartupMark,
        to: StartupMark,
    ): Long? {
        val a = marks[from] ?: return null
        val b = marks[to] ?: return null
        return (b - a).coerceAtLeast(0L)
    }

    /** Process creation (or activity creation if not cold) to the first image of the animation: the "M alone" of the system. */
    val toFirstFrameMs: Long? get() = since(StartupMark.FIRST_FRAME)

    /** First image to first image with the heart: the "M alone" of the animation itself (the HOLD). */
    val firstFrameToHeartMs: Long? get() = between(StartupMark.FIRST_FRAME, StartupMark.HEART_VISIBLE)

    /** The whole time the "M" stays alone: process creation to the heart. */
    val toHeartMs: Long? get() = since(StartupMark.HEART_VISIBLE)

    /** Application.onCreate (everything that runs before the first activity can even start). */
    val applicationCreateMs: Long? get() = between(StartupMark.APP_CREATE_START, StartupMark.APP_CREATE_END)

    /** Application.onCreate finished to the first image: activity creation, first composition, first draw. */
    val appToFirstFrameMs: Long? get() = between(StartupMark.APP_CREATE_END, StartupMark.FIRST_FRAME)

    /** Process creation to Application.onCreate starting: runtime, content providers (Firebase), class loading. */
    val processToApplicationMs: Long? get() = since(StartupMark.APP_CREATE_START)

    val toContentMountedMs: Long? get() = since(StartupMark.CONTENT_MOUNTED)

    val isComplete: Boolean get() = StartupMark.SPLASH_DONE in marks
}

/** Collects the time of the draws of the animation, to find the longest gap between two images. Pure, so it can be tested. */
class FrameGapTracker {
    var frames: Int = 0
        private set
    var maxGapMs: Long = 0L
        private set
    private var lastMs: Long = -1L

    fun onFrame(nowMs: Long) {
        if (lastMs >= 0L) maxGapMs = maxOf(maxGapMs, nowMs - lastMs)
        lastMs = nowMs
        frames++
    }

    fun reset() {
        frames = 0
        maxGapMs = 0L
        lastMs = -1L
    }
}
