package nz.co.doer.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofenceManager @Inject constructor(
    private val context: Context
) {
    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            GEOFENCE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    /**
     * Register a geofence around the site location.
     * Triggers on both ENTER and EXIT transitions.
     * Uses DWELL with 2-minute loiter delay to confirm arrival (not just passing by).
     */
    fun registerSiteGeofence(
        shiftId: Int,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float = DEFAULT_GEOFENCE_RADIUS
    ) {
        if (!hasLocationPermission()) {
            Timber.w("Cannot register geofence: location permission not granted")
            return
        }

        val geofenceId = buildGeofenceId(shiftId)

        val geofence = Geofence.Builder()
            .setRequestId(geofenceId)
            .setCircularRegion(latitude, longitude, radiusMeters)
            .setExpirationDuration(GEOFENCE_EXPIRATION_MS)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or
                Geofence.GEOFENCE_TRANSITION_EXIT or
                Geofence.GEOFENCE_TRANSITION_DWELL
            )
            .setLoiteringDelay(DWELL_DELAY_MS) // 2 minutes to confirm not just passing by
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_DWELL)
            .addGeofence(geofence)
            .build()

        try {
            geofencingClient.addGeofences(request, geofencePendingIntent)
                .addOnSuccessListener {
                    Timber.d("Geofence registered for shift $shiftId at ($latitude, $longitude) radius=${radiusMeters}m")
                }
                .addOnFailureListener { e ->
                    Timber.e(e, "Failed to register geofence for shift $shiftId")
                }
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException registering geofence")
        }
    }

    /**
     * Remove geofence for a specific shift.
     */
    fun removeGeofence(shiftId: Int) {
        val geofenceId = buildGeofenceId(shiftId)
        geofencingClient.removeGeofences(listOf(geofenceId))
            .addOnSuccessListener {
                Timber.d("Geofence removed for shift $shiftId")
            }
            .addOnFailureListener { e ->
                Timber.e(e, "Failed to remove geofence for shift $shiftId")
            }
    }

    /**
     * Remove all active geofences.
     */
    fun removeAllGeofences() {
        geofencingClient.removeGeofences(geofencePendingIntent)
            .addOnSuccessListener {
                Timber.d("All geofences removed")
            }
            .addOnFailureListener { e ->
                Timber.e(e, "Failed to remove all geofences")
            }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun buildGeofenceId(shiftId: Int): String = "doer_site_$shiftId"

    companion object {
        const val GEOFENCE_REQUEST_CODE = 2001
        const val DEFAULT_GEOFENCE_RADIUS = 100f // 100 meters
        const val GEOFENCE_EXPIRATION_MS = 12 * 60 * 60 * 1000L // 12 hours
        const val DWELL_DELAY_MS = 2 * 60 * 1000 // 2 minutes - confirm Doer is actually at site

        /** Extract shiftId from geofence requestId */
        fun extractShiftId(geofenceId: String): Int? {
            return geofenceId.removePrefix("doer_site_").toIntOrNull()
        }
    }
}
