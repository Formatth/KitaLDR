package com.formatth.kitaldr.data

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

/** Stores the current FCM token so the backend can reach this device. */
object PushTokenRegistrar {
    private const val TAG = "KitaLDR-FCM"

    fun register(app: FirebaseApp?, uid: String) {
        if (app == null || uid.isBlank()) {
            Log.w(TAG, "Token registration skipped: Firebase app or uid missing")
            return
        }

        Log.d(TAG, "Starting FCM token registration for uid=$uid")

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                if (token.isNullOrBlank()) {
                    Log.w(TAG, "FCM returned an empty token for uid=$uid")
                    return@addOnSuccessListener
                }

                Log.d(TAG, "FCM token obtained for uid=$uid; saving to deviceTokens/$uid")

                FirebaseFirestore.getInstance(app)
                    .collection("deviceTokens")
                    .document(uid)
                    .set(
                        mapOf(
                            "token" to token,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    )
                    .addOnSuccessListener {
                        Log.d(TAG, "FCM token registered successfully for uid=$uid")
                    }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "Failed to save FCM token for uid=$uid", error)
                    }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to get FCM token for uid=$uid", error)
            }
    }
}
