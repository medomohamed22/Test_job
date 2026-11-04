package com.tripmate.ai.data.remote

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.tripmate.ai.BuildConfig
import com.tripmate.ai.domain.model.TransportMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class PlaceResult(val id: String, val name: String, val address: String?, val lat: Double?, val lng: Double?, val rating: Double?, val mapsUri: String?)
data class RouteResult(val durationMinutes: Int, val distanceMeters: Int?)

/**
 * Thin client for Google Places API (New) Text Search and Routes API computeRoutes.
 * Used ONLY when a Maps key is configured; otherwise the UI shows curated places and labels
 * travel times as offline estimates. Returned names/coordinates are treated as verified;
 * opening hours and prices are not requested and stay unverified.
 */
@Singleton
class PlacesRoutesClient @Inject constructor(@ApplicationContext private val context: Context) {
    val isConfigured: Boolean get() = BuildConfig.MAPS_API_KEY.isNotBlank()

    private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** Headers required when the key is restricted to this Android app (package + SHA-1). */
    private fun Request.Builder.androidHeaders(fieldMask: String) = apply {
        header("X-Goog-Api-Key", BuildConfig.MAPS_API_KEY)
        header("X-Goog-FieldMask", fieldMask)
        header("X-Android-Package", context.packageName)
        signingSha1()?.let { header("X-Android-Cert", it) }
    }

    @Suppress("DEPRECATION")
    private fun signingSha1(): String? = runCatching {
        val pm = context.packageManager
        val sig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()
        } else pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        sig?.toByteArray()?.let { MessageDigest.getInstance("SHA-1").digest(it).joinToString("") { b -> "%02X".format(b) } }
    }.getOrNull()

    @Serializable private data class LatLngJ(val latitude: Double? = null, val longitude: Double? = null)
    @Serializable private data class DisplayName(val text: String? = null)
    @Serializable private data class PlaceJ(val id: String? = null, val displayName: DisplayName? = null, val formattedAddress: String? = null, val location: LatLngJ? = null, val rating: Double? = null, val googleMapsUri: String? = null)
    @Serializable private data class SearchResponse(val places: List<PlaceJ> = emptyList())
    @Serializable private data class RouteJ(val duration: String? = null, val distanceMeters: Int? = null)
    @Serializable private data class RoutesResponse(val routes: List<RouteJ> = emptyList())

    suspend fun searchText(query: String, languageCode: String, biasLat: Double?, biasLng: Double?): Result<List<PlaceResult>> = withContext(Dispatchers.IO) {
        runCatching {
            check(isConfigured) { "Maps key not configured" }
            val body: JsonObject = buildJsonObject {
                put("textQuery", query.take(200))
                put("languageCode", languageCode)
                put("maxResultCount", 10)
                if (biasLat != null && biasLng != null) putJsonObject("locationBias") {
                    putJsonObject("circle") {
                        putJsonObject("center") { put("latitude", biasLat); put("longitude", biasLng) }
                        put("radius", 30000.0)
                    }
                }
            }
            val req = Request.Builder().url("https://places.googleapis.com/v1/places:searchText")
                .androidHeaders("places.id,places.displayName,places.formattedAddress,places.location,places.rating,places.googleMapsUri")
                .post(body.toString().toRequestBody(jsonType)).build()
            http.newCall(req).execute().use { resp ->
                check(resp.isSuccessful) { "Places HTTP ${resp.code}" }
                json.decodeFromString(SearchResponse.serializer(), resp.body?.string().orEmpty()).places.mapNotNull { p ->
                    val name = p.displayName?.text ?: return@mapNotNull null
                    PlaceResult(p.id ?: name, name, p.formattedAddress, p.location?.latitude, p.location?.longitude, p.rating, p.googleMapsUri)
                }
            }
        }
    }

    suspend fun computeRoute(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, mode: TransportMode): Result<RouteResult> = withContext(Dispatchers.IO) {
        runCatching {
            check(isConfigured) { "Maps key not configured" }
            val travelMode = when (mode) { TransportMode.WALK -> "WALK"; TransportMode.TRANSIT -> "TRANSIT"; else -> "DRIVE" }
            val body = buildJsonObject {
                putJsonObject("origin") { putJsonObject("location") { putJsonObject("latLng") { put("latitude", fromLat); put("longitude", fromLng) } } }
                putJsonObject("destination") { putJsonObject("location") { putJsonObject("latLng") { put("latitude", toLat); put("longitude", toLng) } } }
                put("travelMode", travelMode)
                if (travelMode == "DRIVE") put("routingPreference", "TRAFFIC_AWARE")
            }
            val req = Request.Builder().url("https://routes.googleapis.com/directions/v2:computeRoutes")
                .androidHeaders("routes.duration,routes.distanceMeters")
                .post(body.toString().toRequestBody(jsonType)).build()
            http.newCall(req).execute().use { resp ->
                check(resp.isSuccessful) { "Routes HTTP ${resp.code}" }
                val route = json.decodeFromString(RoutesResponse.serializer(), resp.body?.string().orEmpty()).routes.firstOrNull()
                    ?: error("No route")
                val seconds = route.duration?.removeSuffix("s")?.toDoubleOrNull() ?: error("No duration")
                RouteResult(((seconds / 60.0) + 0.5).toInt().coerceAtLeast(1), route.distanceMeters)
            }
        }
    }
}
