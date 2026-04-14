package nz.co.doer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import timber.log.Timber

/**
 * Receives geofence transition events from the OS.
 * Delegates to TrackingManager for state transitions.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent == null) {
            Timber.e("GeofencingEvent is null")
            return
        }

        if (geofencingEvent.hasError()) {
            Timber.e("Geofence error code: ${geofencingEvent.errorCode}")
            return
        }

        val transitionType = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return
        val location = geofencingEvent.triggeringLocation

        for (geofence in triggeringGeofences) {
            val shiftId = GeofenceManager.extractShiftId(geofence.requestId) ?: continue

            when (transitionType) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> {
                    Timber.d("Geofence ENTER for shift $shiftId")
                    // Forward to TrackingManager via service intent
                    val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
                        action = ACTION_GEOFENCE_ENTER
                        putExtra(EXTRA_SHIFT_ID, shiftId)
                        putExtra(EXTRA_LATITUDE, location?.latitude ?: 0.0)
                        putExtra(EXTRA_LONGITUDE, location?.longitude ?: 0.0)
                    }
                    context.startService(serviceIntent)
                }

                Geofence.GEOFENCE_TRANSITION_DWELL -> {
                    Timber.d("Geofence DWELL for shift $shiftId — confirmed on site")
                    val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
                        action = ACTION_GEOFENCE_DWELL
                        putExtra(EXTRA_SHIFT_ID, shiftId)
                        putExtra(EXTRA_LATITUDE, location?.latitude ?: 0.0)
                        putExtra(EXTRA_LONGITUDE, location?.longitude ?: 0.0)
                    }
                    context.startService(serviceIntent)
                }

                Geofence.GEOFENCE_TRANSITION_EXIT -> {
                    Timber.d("Geofence EXIT for shift $shiftId")
                    val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
                        action = ACTION_GEOFENCE_EXIT
                        putExtra(EXTRA_SHIFT_ID, shiftId)
                        putExtra(EXTRA_LATITUDE, location?.latitude ?: 0.0)
                        putExtra(EXTRA_LONGITUDE, location?.longitude ?: 0.0)
                    }
                    context.startService(serviceIntent)
                }

                else -> {
                    Timber.w("Unknown geofence transition: $transitionType")
                }
            }
        }
    }

    companion object {
        const val ACTION_GEOFENCE_ENTER = "nz.co.doer.GEOFENCE_ENTER"
        const val ACTION_GEOFENCE_DWELL = "nz.co.doer.GEOFENCE_DWELL"
        const val ACTION_GEOFENCE_EXIT = "nz.co.doer.GEOFENCE_EXIT"
        const val EXTRA_SHIFT_ID = "extra_shift_id"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
    }
}
