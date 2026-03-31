package nz.co.doer.data.remote

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nz.co.doer.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// ========== Response DTOs ==========

@Serializable
data class DirectionsResponse(
    @SerialName("routes") val routes: List<DirectionsRoute> = emptyList(),
    @SerialName("status") val status: String = ""
)

@Serializable
data class DirectionsRoute(
    @SerialName("legs") val legs: List<DirectionsLeg> = emptyList(),
    @SerialName("overview_polyline") val overviewPolyline: DirectionsPolyline = DirectionsPolyline()
)

@Serializable
data class DirectionsLeg(
    @SerialName("distance") val distance: DirectionsValue = DirectionsValue(),
    @SerialName("duration") val duration: DirectionsValue = DirectionsValue(),
    @SerialName("duration_in_traffic") val durationInTraffic: DirectionsValue? = null,
    @SerialName("start_address") val startAddress: String = "",
    @SerialName("end_address") val endAddress: String = "",
    @SerialName("steps") val steps: List<DirectionsStep> = emptyList()
)

@Serializable
data class DirectionsStep(
    @SerialName("distance") val distance: DirectionsValue = DirectionsValue(),
    @SerialName("duration") val duration: DirectionsValue = DirectionsValue(),
    @SerialName("html_instructions") val htmlInstructions: String = "",
    @SerialName("maneuver") val maneuver: String? = null,
    @SerialName("polyline") val polyline: DirectionsPolyline = DirectionsPolyline(),
    @SerialName("start_location") val startLocation: DirectionsLatLng? = null,
    @SerialName("end_location") val endLocation: DirectionsLatLng? = null
)

@Serializable
data class DirectionsLatLng(
    @SerialName("lat") val lat: Double = 0.0,
    @SerialName("lng") val lng: Double = 0.0
)

@Serializable
data class DirectionsValue(
    @SerialName("text") val text: String = "",
    @SerialName("value") val value: Int = 0
)

@Serializable
data class DirectionsPolyline(
    @SerialName("points") val points: String = ""
)

// ========== Parsed Result ==========

data class NavigationStep(
    val instruction: String,
    val distanceText: String,
    val distanceMeters: Int,
    val durationText: String,
    val maneuver: String?,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double
)

data class RouteInfo(
    val polylinePoints: List<LatLng>,
    val distanceText: String,
    val distanceMeters: Int,
    val durationText: String,
    val durationSeconds: Int,
    val startAddress: String,
    val endAddress: String,
    val steps: List<NavigationStep> = emptyList()
)

// ========== Service ==========

@Singleton
class GoogleDirectionsService @Inject constructor() {

    private val apiKey = BuildConfig.MAPS_API_KEY
    private val baseUrl = "https://maps.googleapis.com/maps/api/directions"
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetch route from origin to destination using Google Directions API.
     * Returns route polyline, ETA, and distance.
     *
     * @param originLat Origin latitude (Doer's current location)
     * @param originLng Origin longitude
     * @param destLat Destination latitude (site location)
     * @param destLng Destination longitude
     * @param mode Travel mode: "driving" (default), "walking", "bicycling"
     */
    suspend fun getRoute(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        mode: String = "driving"
    ): RouteInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/json" +
                        "?origin=$originLat,$originLng" +
                        "&destination=$destLat,$destLng" +
                        "&mode=$mode" +
                        "&departure_time=now" +
                        "&key=$apiKey"

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    val result = json.decodeFromString<DirectionsResponse>(body)

                    if (result.status != "OK" || result.routes.isEmpty()) {
                        Timber.w("Directions API: status=${result.status}")
                        return@withContext null
                    }

                    val route = result.routes.first()
                    val leg = route.legs.first()

                    // Use traffic duration if available, otherwise regular duration
                    val duration = leg.durationInTraffic ?: leg.duration

                    // Parse steps for turn-by-turn
                    val navSteps = leg.steps.map { step ->
                        NavigationStep(
                            instruction = step.htmlInstructions
                                .replace(Regex("<[^>]*>"), "") // Strip HTML tags
                                .replace("&nbsp;", " ")
                                .replace("&amp;", "&"),
                            distanceText = step.distance.text,
                            distanceMeters = step.distance.value,
                            durationText = step.duration.text,
                            maneuver = step.maneuver,
                            startLat = step.startLocation?.lat ?: 0.0,
                            startLng = step.startLocation?.lng ?: 0.0,
                            endLat = step.endLocation?.lat ?: 0.0,
                            endLng = step.endLocation?.lng ?: 0.0
                        )
                    }

                    RouteInfo(
                        polylinePoints = decodePolyline(route.overviewPolyline.points),
                        distanceText = leg.distance.text,
                        distanceMeters = leg.distance.value,
                        durationText = duration.text,
                        durationSeconds = duration.value,
                        startAddress = leg.startAddress,
                        endAddress = leg.endAddress,
                        steps = navSteps
                    )
                } else {
                    Timber.e("Directions API error: ${response.code}")
                    null
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch directions")
                null
            }
        }
    }

    /**
     * Decode an encoded polyline string into a list of LatLng points.
     * Google's polyline encoding algorithm.
     */
    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = mutableListOf<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            poly.add(LatLng(lat / 1E5, lng / 1E5))
        }
        return poly
    }
}
