package com.membora.app.firebase

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.membora.app.MainActivity
import com.membora.app.R

object MemboraNotifications {
    const val CHANNEL_ID = "membora_updates"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Membora updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Membership, payment, check-in, and workspace updates"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun showAutomationSummary(context: Context, preparedCount: Int, submittedCount: Int = 0) {
        if (preparedCount <= 0 && submittedCount <= 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (submittedCount > 0) {
            "$submittedCount reminder${if (submittedCount == 1) "" else "s"} submitted"
        } else {
            "$preparedCount reminder${if (preparedCount == 1) "" else "s"} ready"
        }
        val detail = if (submittedCount > 0) {
            "WhatsApp/SMS provider accepted the automatic message${if (submittedCount == 1) "" else "s"}."
        } else {
            "Open More → Automations to review or complete provider setup."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(detail)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(4_210, notification)
    }
}

// Current Firebase uses FID callbacks; Android lint still expects the deprecated onNewToken API.
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class MemboraMessagingService : FirebaseMessagingService() {
    override fun onRegistered(installationId: String) {
        FirebaseServices.initialize(applicationContext)
        FirebaseServices.onMessagingRegistered(installationId)
    }

    override fun onUnregistered(installationId: String) {
        FirebaseServices.initialize(applicationContext)
        FirebaseServices.onMessagingUnregistered(installationId)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "Membora"
        val body = message.notification?.body ?: message.data["body"] ?: return
        FirebaseServices.logEvent("notification_received")
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        MemboraNotifications.createChannel(this)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, MemboraNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(this).notify((title + body).hashCode(), notification)
    }
}
