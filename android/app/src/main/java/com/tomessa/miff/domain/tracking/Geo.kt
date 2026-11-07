package com.tomessa.miff.domain.tracking

import kotlin.math.asin
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * PRIVACY, for this file: pure geometry on numbers, nothing is read from the phone, nothing is logged. A coordinate only ever lives in a
 * class whose toString() shows nothing (see TrackingModels.kt).
 */

object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    /** Distance in metres between two points (haversine: exact enough for zones of 50 m and more). */
    fun distanceMeters(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double,
    ): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLat = p2 - p1
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + kotlin.math.cos(p1) * kotlin.math.cos(p2) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    /** The coordinate as the rules and the data model want it: 5 decimals (about one metre). The only precision ever written. */
    fun round5(value: Double): Double = (value * 100_000.0).roundToLong() / 100_000.0

    /** Distance to the EDGE of a zone: the distance to its centre minus its radius, never below zero (0 = inside, or on the edge). */
    fun edgeDistanceMeters(
        distanceToCentre: Double,
        radiusM: Int,
    ): Double = max(0.0, distanceToCentre - radiusM)
}
