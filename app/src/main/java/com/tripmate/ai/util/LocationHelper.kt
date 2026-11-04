package com.tripmate.ai.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class LatLngValue(val lat: Double, val lng: Double, val accuracyMeters: Float?)

/** Foreground, on-demand location only. Never tracks in the background. */
@Singleton
class LocationHelper @Inject constructor(@ApplicationContext private val context: Context) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): LatLngValue? {
        if (!hasPermission()) return null
        return runCatching {
            withTimeoutOrNull(15_000) {
                val cts = CancellationTokenSource()
                LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
                    ?.let { LatLngValue(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy else null) }
            }
        }.getOrNull()
    }
}
