package com.tomessa.miff.data.timeshare

import com.tomessa.miff.data.permission.DevicePermissions
import com.tomessa.miff.data.tracking.TrackingLocalStore
import com.tomessa.miff.domain.permission.LocationFlow
import com.tomessa.miff.domain.timeshare.TimeShareHealth
import com.tomessa.miff.domain.timeshare.TimeShareTracking
import com.tomessa.miff.domain.tracking.ZoneSelection

/*
 * What the card of "Répartition de ton temps" says about the tracking of one Miff. It only reads the PRIVATE local copy of the tracking (no Firestore reading, no position),
 * the state of the permissions (never a position) and the trip in progress. Nothing is logged.
 */
fun interface TimeShareTrackingSource {
    fun of(miffId: String): TimeShareTracking
}

class EngineTimeShareTracking(
    private val local: TrackingLocalStore,
    private val currentUid: () -> String?,
    private val permissions: DevicePermissions,
    private val travelOn: () -> Boolean,
    /** The Miffs that wait for the person's agreement (the consent has to be renewed): their tracking is not "stopped by the person". */
    private val waitingAgreement: () -> Set<String>,
) : TimeShareTrackingSource {
    override fun of(miffId: String): TimeShareTracking {
        val snapshot = permissions.snapshot()
        val state = currentUid()?.let { uid -> local.load()?.takeIf { it.uid == uid } }
        val zones =
            state
                ?.miffs
                ?.flatMap { (id, miff) ->
                    miff.places.mapNotNull { place -> place.toFact()?.let { ZoneSelection.MiffPlace(id, it) } }
                }.orEmpty()
        val stopped =
            TimeShareHealth.sharingStopped(
                checkedOnce = state != null && state.checkedAtMillis != 0L,
                watched = state?.miffs?.containsKey(miffId) == true,
                waitingAgreement = miffId in waitingAgreement(),
            )
        return TimeShareTracking(
            health = TimeShareHealth.resolve(travelOn(), stopped, snapshot.locationComplete),
            overLimit = ZoneSelection.merge(zones).size > ZoneSelection.MAX_WATCHED,
            backgroundRequestable = LocationFlow.canRequestBackground(snapshot),
        )
    }
}
