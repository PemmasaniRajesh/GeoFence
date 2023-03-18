package ajmera.geofence

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent

class GeoFenceBroadCastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Constants.ACTION_GEOFENCE_EVENT) {
            val geofencingEvent = GeofencingEvent.fromIntent(intent)

            if (geofencingEvent?.hasError() == true) {
                val errorMessage = errorMessage(context, geofencingEvent.errorCode)
                Toast.makeText(context,errorMessage,Toast.LENGTH_SHORT).show()
                return
            }

            val geoFenceApplication: GeoFenceApplication = context.applicationContext as GeoFenceApplication

            if (geofencingEvent?.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER
                || geofencingEvent?.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
                val fenceId = when {
                    geofencingEvent.triggeringGeofences!!.isNotEmpty() ->
                        geofencingEvent.triggeringGeofences!![0].requestId
                    else -> {
                        return
                    }
                }
                val gpxLoc = geoFenceApplication.getGpxLocList().singleOrNull() { it.id==fenceId }
                println("GeoFence${getEventMessage(geofencingEvent).plus(" ").plus(gpxLoc?.title)}")

                val notificationManager = ContextCompat.getSystemService(
                    context,
                    NotificationManager::class.java
                ) as NotificationManager

                notificationManager.sendGeofenceEventNotification(
                    context,getEventMessage(geofencingEvent).plus(" ").plus(gpxLoc?.title)
                )
            }
        }
    }

    private fun getEventMessage(geofencingEvent:GeofencingEvent):String{
        return if(geofencingEvent.geofenceTransition==Geofence.GEOFENCE_TRANSITION_ENTER){
            "Enter"
        }else{
            "Exit"
        }
    }

    private fun errorMessage(context: Context, errorCode: Int): String {
        val resources = context.resources
        return when (errorCode) {
            GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> resources.getString(
                R.string.geofence_not_available
            )
            GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES -> resources.getString(
                R.string.geofence_too_many_geofences
            )
            GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS -> resources.getString(
                R.string.geofence_too_many_pending_intents
            )
            else -> resources.getString(R.string.unknown_geofence_error)
        }
    }
}