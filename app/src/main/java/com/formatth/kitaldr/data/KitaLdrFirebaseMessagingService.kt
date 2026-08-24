package com.formatth.kitaldr.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.formatth.kitaldr.MainActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Receives FCM data messages and shows a real Android notification. */
class KitaLdrFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val type = message.data["type"] ?: return
        val senderName = message.data["senderName"] ?: "My Love"
        if (type == RemoteActionService.ACTION_POKE) {
            showActionNotification(
                "$senderName poked you! ❤️",
                "Open KitaLDR to respond."
            )
        }
    }

    private fun saveToken(token: String) {
        val app = FirebaseApp.getApps(this).firstOrNull() ?: return
        val uid = FirebaseAuth.getInstance(app).currentUser?.uid ?: return
        FirebaseFirestore.getInstance(app)
            .collection("deviceTokens")
            .document(uid)
            .set(mapOf(
                "token" to token,
                "updatedAt" to FieldValue.serverTimestamp(),
            ))
    }

    private fun showActionNotification(title: String, body: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "KitaLDR actions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications from your KitaLDR partner"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 180, 100, 280)
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 180, 100, 280))
            .setContentIntent(pendingIntent)
            .build()

        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    companion object {
        const val CHANNEL_ID = "kitaldr_actions"
    }
}
