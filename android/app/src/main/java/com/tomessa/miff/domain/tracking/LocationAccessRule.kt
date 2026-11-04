package com.tomessa.miff.domain.tracking

import com.tomessa.miff.domain.miff.LocationAccess
import com.tomessa.miff.domain.permission.PermissionSnapshot

/** `locationAccess` of the status (ticket 6.7, four values only), from what the phone says. Pure. */
object LocationAccessRule {
    /**
     * `background` = "all the time" + precise; `foreground` = a permission that is not enough (while using, or the approximate position only);
     * `denied` = the location of the phone itself is off; `unknown` = nothing granted (refused, or never asked: the phone does not tell them apart).
     */
    fun of(
        snapshot: PermissionSnapshot,
        locationServicesOn: Boolean,
    ): LocationAccess =
        when {
            !locationServicesOn -> LocationAccess.DENIED
            snapshot.locationComplete -> LocationAccess.BACKGROUND
            snapshot.fineLocation || snapshot.coarseLocation -> LocationAccess.FOREGROUND
            else -> LocationAccess.UNKNOWN
        }
}
