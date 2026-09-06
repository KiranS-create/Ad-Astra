package org.sih.itantra.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import org.sih.itantra.presentation.MainActivity

/**
 * BroadcastReceiver for BOOT_COMPLETED.
 *
 * ## Android 12+ Foreground Service Start Restriction
 *
 * Android 12+ (API 31+) prohibits starting a foreground service from a
 * BroadcastReceiver in most cases. The `dataSync` foreground service type
 * does NOT grant the BOOT_COMPLETED exemption.
 *
 * Correct policy:
 * - On boot, read the user's saved MANET Node Mode preference.
 * - If the preference is ON (user enabled it before reboot), post a
 *   non-foreground "tap to re-enable" notification.
 * - Do NOT attempt startForegroundService() here — it will throw
 *   ForegroundServiceStartNotAllowedException on API 31+.
 * - The user re-opens iTantra and re-enables Node Mode from the UI.
 *   That explicit user action from a foreground Activity is the only
 *   compliant way to start the service after reboot.
 *
 * This is a deliberate, documented limitation, not a bug.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val BOOT_CHANNEL_ID = "manet_boot"
        private const val BOOT_NOTIFICATION_ID = 1002
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "BOOT_COMPLETED received")

        val nodeModeWasEnabled = ManetNodePreference.isNodeModeEnabled(context)
        if (!nodeModeWasEnabled) {
            Log.d(TAG, "MANET Node Mode was OFF before reboot — no action needed")
            return
        }

        Log.i(TAG, "MANET Node Mode was ON before reboot — posting nudge notification")
        postNudgeNotification(context)
    }

    private fun postNudgeNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel if it doesn't exist yet
        val channel = NotificationChannel(
            BOOT_CHANNEL_ID,
            "MANET Boot Recovery",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies operator to re-enable MANET after reboot"
        }
        nm.createNotificationChannel(channel)

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, BOOT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("iTantra MANET — Reboot Detected")
            .setContentText("MANET Node Mode was active before reboot. Tap to re-enable.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "MANET Node Mode was active before the device rebooted.\n" +
                        "Open iTantra and re-enable MANET Node Mode to resume offline relay operation."
                    )
            )
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        nm.notify(BOOT_NOTIFICATION_ID, notification)
    }
}
