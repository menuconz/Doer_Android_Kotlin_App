package nz.co.doer.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nz.co.doer.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PlacePrediction(
    @SerialName("description") val description: String = "",
    @SerialName("place_id") val placeId: String = ""
)

@Serializable
data class PlaceAutoCompleteResponse(
    @SerialName("predictions") val predictions: List<PlacePrediction> = emptyList(),
    @SerialName("status") val status: String = ""
)

@Serializable
data class PlaceLocation(
    @SerialName("lat") val lat: Double = 0.0,
    @SerialName("lng") val lng: Double = 0.0
)

@Serializable
data class PlaceGeometry(
    @SerialName("location") val location: PlaceLocation = PlaceLocation()
)

@Serializable
data class PlaceDetailResult(
    @SerialName("formatted_address") val formattedAddress: String = "",
    @SerialName("geometry") val geometry: PlaceGeometry = PlaceGeometry()
)

@Serializable
data class PlaceDetailsResponse(
    @SerialName("result") val result: PlaceDetailResult = PlaceDetailResult(),
    @SerialName("status") val status: String = ""
)

data class GooglePlace(
    val address: String,
    val latitude: Double,
    val longitude: Double
)

@Singleton
class GooglePlacesService @Inject constructor() {

    private val apiKey = BuildConfig.MAPS_API_KEY
    private val baseUrl = "https://maps.googleapis.com/maps/api/place"
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getPlacesByText(searchText: String): List<PlacePrediction> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/autocomplete/json?input=${searchText}&key=$apiKey"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext emptyList()
                    val result = json.decodeFromString<PlaceAutoCompleteResponse>(body)
                    result.predictions
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun getPlaceDetails(placeId: String): GooglePlace? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/details/json?placeid=$placeId&key=$apiKey"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    val result = json.decodeFromString<PlaceDetailsResponse>(body)
                    GooglePlace(
                        address = result.result.formattedAddress,
                        latitude = result.result.geometry.location.lat,
                        longitude = result.result.geometry.location.lng
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
