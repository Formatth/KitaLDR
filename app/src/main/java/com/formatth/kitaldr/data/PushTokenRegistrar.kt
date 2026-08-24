package com.formatth.kitaldr.data

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

/** Stores the current FCM token so the backend can reach this device. */
object PushTokenRegistrar {
    fun register(app: FirebaseApp?, uid: String) {
        if (app == null || uid.isBlank()) return
        FirebaseMessaging.getInstance(app).token
            .addOnSuccessListener { token ->
                if (token.isNullOrBlank()) return@addOnSuccessListener
                FirebaseFirestore.getInstance(app)
                    .collection("deviceTokens")
                    .document(uid)
                    .set(mapOf(
                        "token" to token,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ))
            }
    }
}
