package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationCompat

class AntivirusService : NotificationListenerService() {

    companion object {
        const val ACTION_STOP = "STOP_ANTIVIRUS"
        var isRunning = false
            private set
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            when (intent?.action) {
                ACTION_STOP -> {
                    stopWithNotification()
                    START_NOT_STICKY
                }
                else -> {
                    startForegroundService()
                    START_STICKY
                }
            }
        } catch (e: SecurityException) {
            stopSelf()
            START_NOT_STICKY
        }
    }

    private fun startForegroundService() {
        isRunning = true
        sendServiceStatusUpdate()
        createNotificationChannel()
        val notification = buildNotification(true)
        startForeground(1, notification)
    }

    private fun stopWithNotification() {
        isRunning = false
        sendServiceStatusUpdate()
        showStoppedNotification()
        stopSelf()
    }

    private fun sendServiceStatusUpdate() {
        val intent = Intent("com.example.myapplication.ANTIVIRUS_STATUS")
        intent.putExtra("status", isRunning)
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "antivirus_channel",
                "Антивирус",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Канал для уведомлений антивируса"
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(isRunning: Boolean): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent, PendingIntent.FLAG_IMMUTABLE // Добавляем флаг FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AntivirusService::class.java).apply {
            action = ACTION_STOP
        }

        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE // Добавляем флаг FLAG_IMMUTABLE
        )


        return NotificationCompat.Builder(this, "antivirus_channel")
            .setContentTitle("Антивирус APK")
            .setContentText(if (isRunning) "Защита активна" else "Защита отключена")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentPendingIntent)
            .apply {
                if (isRunning) {
                    addAction(android.R.drawable.ic_media_pause, "Отключить", stopPendingIntent)
                }
            }
            .setOngoing(isRunning)
            .build()
    }

    private fun showStoppedNotification() {
        val notification = buildNotification(false)
            .apply { flags = flags and Notification.FLAG_ONGOING_EVENT.inv() }

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(2, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
