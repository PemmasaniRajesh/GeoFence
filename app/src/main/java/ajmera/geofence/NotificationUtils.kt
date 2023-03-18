package ajmera.geofence

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat


private const val GEOFENCE_CHANNEL_ID = "GeofenceChannelEnter"

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationChannel = NotificationChannel(
            GEOFENCE_CHANNEL_ID,
            context.getString(R.string.channel_name),

            NotificationManager.IMPORTANCE_HIGH
        )
            .apply {
                setShowBadge(false)
            }

        notificationChannel.enableLights(true)
        notificationChannel.lightColor = Color.RED
        notificationChannel.enableVibration(true)
        notificationChannel.description = context.getString(R.string.notification_channel_description)

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(notificationChannel)
    }
}

//create two notification channels only for the purpose of showing all entry/exit events....
//when i created only one channel, i'm facing one issue of not getting all notifications...
fun NotificationManager.sendGeofenceEventNotification(context: Context, title:String) {
    val mapImage = BitmapFactory.decodeResource(
        context.resources,
        R.drawable.ic_launcher_foreground
    )

    // We use the name resource ID from the LANDMARK_DATA along with content_text to create
    // a custom message when a Geofence triggers.
    val builder = NotificationCompat.Builder(context, GEOFENCE_CHANNEL_ID)
        .setContentTitle(context.getString(R.string.app_name).plus(" ").plus("Enter Channel"))
        .setContentText(title)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(false)
        .setLargeIcon(mapImage)

    notify(SystemClock.uptimeMillis().toInt(), builder.build())
}
