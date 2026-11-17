package com.hopa.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.hopa.app.HopaApplication
import com.hopa.app.MainActivity
import com.hopa.app.feature.runningapps.WirelessSetup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps HOPA alive while Android's pairing dialog is open. A cached app is
 * frozen by the system, so waiting in a view model loses the dialog; a
 * shortService does not. The service reads the code, pairs, and puts the
 * result where the Act page shows it.
 */
class PairingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val watchdog = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as HopaApplication).container
        container.pairingStatus.value = null
        ensureChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Pairing with Android")
            .setContentText("Keep the pairing dialog open; HOPA reads it from here.")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        // A shortService is expected to stop within three minutes.
        watchdog.removeCallbacksAndMessages(null)
        watchdog.postDelayed({ stopSelf() }, WATCHDOG_MS)

        val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_WAIT
        scope.launch {
            val message = runCatching {
                val setup = WirelessSetup(container)
                if (mode == MODE_AUTO) setup.run() else setup.waitForDialog()
            }.getOrElse { "Pairing failed: ${it.message}" }
            container.pairingStatus.value = message
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        watchdog.removeCallbacksAndMessages(null)
        scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) {
            return
        }
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Pairing", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while HOPA reads Android's pairing dialog."
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val MODE_AUTO = "auto"
        const val MODE_WAIT = "wait"

        private const val EXTRA_MODE = "mode"
        private const val CHANNEL = "hopa_pairing"
        private const val NOTIFICATION_ID = 1002

        /** Short of the three-minute shortService limit, past the two-minute wait. */
        private const val WATCHDOG_MS = 170_000L

        /** The app is in front when this is called, so a foreground start is allowed. */
        fun start(context: Context, mode: String) {
            val intent = Intent(context, PairingService::class.java)
                .putExtra(EXTRA_MODE, mode)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
