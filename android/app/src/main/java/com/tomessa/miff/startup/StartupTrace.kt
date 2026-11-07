package com.tomessa.miff.startup

import android.os.Process
import android.os.SystemClock
import android.os.Trace
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.domain.startup.FrameGapTracker
import com.tomessa.miff.domain.startup.StartupMark
import com.tomessa.miff.domain.startup.StartupReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.EnumMap

/**
 * Measures the launch, to answer "how long does the M stay alone?" with numbers instead of an impression.
 *
 * - [section] puts a named section in the system trace (Perfetto / Android Studio profiler, "System Trace"). Free when no
 *   trace is being recorded, so it stays in every build.
 * - [mark] / [onSplashDraw] keep the time of the key moments. They cost one clock read and are kept in every build.
 * - The on-screen readout ([StartupMetricsOverlay]) exists ONLY in the debug and "releaseLike" builds
 *   ([BuildConfig.STARTUP_METRICS]); release builds do not contain it (R8 removes it). There is NO log line: the app never
 *   logs (NoSensitiveLoggingTest), the readout replaces it.
 *
 * Nothing here records anything about the person: only durations, never a name, an e-mail, an identifier or a position.
 */
object StartupTrace {
    private val lock = Any()
    private val marks = EnumMap<StartupMark, Long>(StartupMark::class.java)
    private val gaps = FrameGapTracker()
    private var baseElapsedMs: Long = Process.getStartElapsedRealtime()
    private var cold = true
    private var activityCreations = 0
    private var heartSeen = false

    private val completed = MutableStateFlow<StartupReport?>(null)

    /** The report of this launch, published once the animation has finished (null before, and in release builds' UI). */
    val completedReport: StateFlow<StartupReport?> = completed.asStateFlow()

    /** Runs [block] inside a named section of the system trace. */
    inline fun <T> section(
        name: String,
        block: () -> T,
    ): T {
        Trace.beginSection(name)
        try {
            return block()
        } finally {
            Trace.endSection()
        }
    }

    /** Records the moment, the first time only. */
    fun mark(mark: StartupMark) {
        synchronized(lock) {
            if (mark !in marks) marks[mark] = SystemClock.elapsedRealtime()
        }
    }

    /**
     * Called by MainActivity.onCreate. A first creation in the process is a cold start (times counted from the creation of
     * the process). A later creation of a new screen in a living process starts the measure again from this moment.
     * [recreation] is true when the system rebuilds the screen (rotation, language): nothing is measured again.
     */
    fun onActivityCreate(recreation: Boolean) {
        synchronized(lock) {
            activityCreations++
            if (activityCreations > 1) {
                if (recreation) return
                cold = false
                baseElapsedMs = SystemClock.elapsedRealtime()
                marks.clear()
                gaps.reset()
                heartSeen = false
                completed.value = null
            }
            marks[StartupMark.ACTIVITY_CREATE_START] = SystemClock.elapsedRealtime()
        }
    }

    /**
     * Called from the DRAW phase of the animation at every image: the first call is "the first image", the first call with
     * the heart showing is "the heart is visible". Cheap on purpose (one clock read, no allocation).
     */
    fun onSplashDraw(heartVisible: Boolean) {
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            if (StartupMark.SPLASH_DONE in marks) return
            gaps.onFrame(now)
            if (StartupMark.FIRST_FRAME !in marks) marks[StartupMark.FIRST_FRAME] = now
            if (heartVisible && !heartSeen) {
                heartSeen = true
                marks[StartupMark.HEART_VISIBLE] = now
            }
        }
    }

    /** The animation is over: the report is complete (published for the readout in internal builds). */
    fun onSplashDone() {
        val report: StartupReport
        synchronized(lock) {
            if (StartupMark.SPLASH_DONE in marks) return
            marks[StartupMark.SPLASH_DONE] = SystemClock.elapsedRealtime()
            report = snapshotLocked()
        }
        // Published only in the internal builds, where the readout exists.
        if (BuildConfig.STARTUP_METRICS) completed.value = report
    }

    fun snapshot(): StartupReport = synchronized(lock) { snapshotLocked() }

    /** Tests only: forgets everything, as in a fresh process. */
    internal fun resetForTest() {
        synchronized(lock) {
            marks.clear()
            gaps.reset()
            baseElapsedMs = 0L
            cold = true
            activityCreations = 0
            heartSeen = false
            completed.value = null
        }
    }

    private fun snapshotLocked() =
        StartupReport(
            cold = cold,
            baseElapsedMs = baseElapsedMs,
            marks = HashMap(marks),
            maxFrameGapMs = gaps.maxGapMs,
            frames = gaps.frames,
        )
}
