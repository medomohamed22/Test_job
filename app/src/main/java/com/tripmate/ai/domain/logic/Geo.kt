package com.tripmate.ai.domain.logic

import com.tripmate.ai.domain.model.TransportMode
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_KM = 6371.0

    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Offline travel-time *estimate*: straight-line distance x 1.35 detour factor at an
     * average speed for the mode. Always labelled as an estimate in the UI — never live traffic.
     */
    fun estimateTravelMinutes(lat1: Double?, lng1: Double?, lat2: Double?, lng2: Double?, mode: TransportMode): Int {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) return 20
        val km = distanceKm(lat1, lng1, lat2, lng2) * 1.35
        if (km < 0.3) return 5
        val minutes = km / mode.kmPerHour * 60.0 + mode.overheadMinutes
        return (minutes / 5.0).roundToInt().coerceAtLeast(1) * 5
    }
}
