package com.example.shortcutdemo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    private const val CHANNEL_ID = "screen_record_channel"
    private const val CHANNEL_NAME = "Screen Recording"
    private const val CHANNEL_DESCRIPTION = "Notifications for screen recording with 30s intervals"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun createNotification(context: Context): Notification {
        // Create intent to stop recording
        val stopIntent = Intent(context, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.STOP_RECORDING
        }
        
        val stopPendingIntent = PendingIntent.getService(
            context,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create intent to open floating window
        val openIntent = Intent(context, FloatingWindowActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val openPendingIntent = PendingIntent.getActivity(
            context,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Screen Recording Active")
            .setContentText("Recording in 30-second segments...")
            .setSmallIcon(android.R.drawable.ic_media_ff) // You can replace with your own icon
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop Recording",
                stopPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_view,
                "Open Controls",
                openPendingIntent
            )
            .setContentIntent(openPendingIntent)
            .setAutoCancel(false)
            .build()
    }

    fun updateNotificationWithSegment(context: Context, segmentNumber: Int): Notification {
        // Create intent to stop recording
        val stopIntent = Intent(context, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.STOP_RECORDING
        }
        
        val stopPendingIntent = PendingIntent.getService(
            context,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create intent to open floating window
        val openIntent = Intent(context, FloatingWindowActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val openPendingIntent = PendingIntent.getActivity(
            context,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Screen Recording Active")
            .setContentText("Recording segment $segmentNumber (30s intervals)")
            .setSmallIcon(android.R.drawable.ic_media_ff)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop Recording",
                stopPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_view,
                "Open Controls",
                openPendingIntent
            )
            .setContentIntent(openPendingIntent)
            .setAutoCancel(false)
            .build()
    }
}