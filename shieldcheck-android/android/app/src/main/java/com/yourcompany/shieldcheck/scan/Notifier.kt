package com.yourcompany.shieldcheck.scan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Shared by the install-time receiver and the periodic worker so a
 * flagged app is announced the same way regardless of which one caught it. */
object Notifier {
    private const val CHANNEL_ID = "shieldcheck_alerts"

    fun notify(context: Context, finding: ScanFinding) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Security alerts", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val text = "${finding.appName}: ${finding.reason}"
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("ShieldCheck flagged an app")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build()
        // No-ops safely if POST_NOTIFICATIONS isn't granted on API 33+,
        // rather than crashing — but see MainActivity for requesting it.
        NotificationManagerCompat.from(context).notify(finding.packageName.hashCode(), notification)
    }
}
