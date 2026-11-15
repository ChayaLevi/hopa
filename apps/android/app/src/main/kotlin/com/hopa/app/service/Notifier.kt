package com.hopa.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hopa.app.MainActivity

/** The small "can I show a notification" helper used by Settings. */
object Notifier {
    private const val CHANNEL = "hopa_test"
    private const val ID = 2001
    private const val ANSWER_CHANNEL = "hopa_answers"
    private const val ANSWER_ID = 2002

    fun permissionGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** "Your answer is ready", with the answer itself as the text. */
    fun postAnswer(context: Context, text: String): Boolean {
        if (!permissionGranted(context) || text.isBlank()) {
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.getNotificationChannel(ANSWER_CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        ANSWER_CHANNEL,
                        "Answers",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply { description = "Shown when a run finishes in another app" },
                )
            }
        }
        val notification = NotificationCompat.Builder(context, ANSWER_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("HOPA has an answer")
            .setContentText(text.replace('\n', ' ').take(120))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(text.take(600)),
            )
            .setAutoCancel(true)
            .setContentIntent(
                android.app.PendingIntent.getActivity(
                    context,
                    3,
                    android.content.Intent(context, MainActivity::class.java)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        manager.notify(ANSWER_ID, notification)
        return true
    }

    fun postTest(context: Context): Boolean {
        if (!permissionGranted(context)) {
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL,
                        "Tests",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ),
                )
            }
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("HOPA test notification")
            .setContentText("Notifications work. This one is not a run.")
            .setAutoCancel(true)
            .setContentIntent(
                android.app.PendingIntent.getActivity(
                    context,
                    2,
                    android.content.Intent(context, MainActivity::class.java),
                    android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        manager.notify(ID, notification)
        return true
    }
}
