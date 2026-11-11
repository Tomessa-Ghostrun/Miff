package com.tomessa.miff.data.tracking

import com.tomessa.miff.domain.travel.TravelPeriod
import kotlinx.coroutines.flow.StateFlow

/**
 * MODE VOYAGE (VOY-2A): what the Profile asks of the tracking, as a small interface (the screen is tested with a double). A trip is open to EVERY account:
 * nothing here reads or checks a plan.
 */
interface TravelControl {
    /** The trip in progress on this phone (null: none). */
    val travel: StateFlow<TravelPeriod?>

    /** Reads the local copy again (the Profile opens, the account changed). */
    fun reload()

    /** Begins a trip, or moves its end when one is on. */
    suspend fun set(period: TravelPeriod): TravelResult

    /** Ends the trip now. False when none was on. */
    suspend fun end(): Boolean
}

/** [TravelControl] backed by the detection engine. */
class EngineTravelControl(
    private val engine: TrackingEngine,
) : TravelControl {
    override val travel: StateFlow<TravelPeriod?> get() = engine.travel

    override fun reload() = engine.reloadTravel()

    override suspend fun set(period: TravelPeriod): TravelResult = engine.setTravel(period)

    override suspend fun end(): Boolean = engine.endTravel()
}
