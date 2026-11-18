package com.hopa.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.hopa.app.HopaApplication
import com.hopa.app.MainActivity
import com.hopa.app.R

/**
 * A bounded run in the foreground: one ongoing notification, a Stop action and
 * a 120 s cap enforced by the caller.
 */
class RunService : Service() {
    private val watchdog = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            (application as HopaApplication).container.runGate.cancel()
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, RunService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("HOPA is working")
            .setContentText("The run stops on its own after two minutes.")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Stop", stop)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        // A shortService that is still running after three minutes is a system
        // error ("did not stop within a timeout"). The run is capped at 120 s;
        // this is the belt-and-braces stop for a turn that leaked its finally.
        watchdog.removeCallbacksAndMessages(null)
        watchdog.postDelayed({ stopSelf() }, WATCHDOG_MS)
        val busy = (application as HopaApplication).container.runGate.state.value.busy
        if (!busy) {
            // Started for a turn that already finished (or never began).
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        watchdog.removeCallbacksAndMessages(null)
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
            NotificationChannel(CHANNEL, "Runs", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while HOPA is working on a task."
                setShowBadge(false)
            },
        )
    }

    private companion object {
        const val CHANNEL = "hopa_runs"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.hopa.app.STOP_RUN"
        /** Short of the three-minute shortService limit, longer than the run cap. */
        const val WATCHDOG_MS = 150_000L
    }
}
