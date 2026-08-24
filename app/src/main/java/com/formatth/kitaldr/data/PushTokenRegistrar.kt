package com.formatth.kitaldr.data

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

/** Stores the current FCM token so the backend can reach this device. */
object PushTokenRegistrar {
    private const val TAG = "KitaLDR-FCM"

    fun register(app: FirebaseApp?, uid: String) {
        if (app == null || uid.isBlank()) {
            Log.w(TAG, "FCM registration skipped: Firebase app or uid missing")
            return
        }

        val currentUid = FirebaseAuth.getInstance(app).currentUser?.uid
        if (currentUid != uid) {
            Log.w(TAG, "FCM registration skipped: auth uid mismatch (expected=$uid, actual=$currentUid)")
            return
        }

        Log.d(TAG, "FCM_REG_START uid=$uid")

        // Do not call FirebaseMessaging.getInstance() here. With the current
        // Firebase Messaging SDK/Kotlin compiler combination, Kotlin can
        // resolve the package-private getInstance(FirebaseApp) overload.
        // FirebaseApp.get(Class) is the supported way to obtain the component
        // belonging to this FirebaseApp instance.
        val messaging = app.get(FirebaseMessaging::class.java)

        messaging.token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.e(TAG, "FCM_REG_TOKEN_FAILED uid=$uid", task.exception)
                    return@addOnCompleteListener
                }

                val token = task.result
                if (token.isNullOrBlank()) {
                    Log.e(TAG, "FCM_REG_TOKEN_EMPTY uid=$uid")
                    return@addOnCompleteListener
                }

                Log.d(TAG, "FCM_REG_TOKEN_OK uid=$uid length=${token.length}")
                Log.d(TAG, "FCM_REG_FIRESTORE_WRITE_START path=deviceTokens/$uid")

                FirebaseFirestore.getInstance(app)
                    .collection("deviceTokens")
                    .document(uid)
                    .set(
                        mapOf(
                            "token" to token,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                        SetOptions.merge()
                    )
                    .addOnSuccessListener {
                        Log.d(TAG, "FCM_REG_SUCCESS path=deviceTokens/$uid")
                    }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "FCM_REG_FIRESTORE_FAILED path=deviceTokens/$uid", error)
                    }
            }
    }
}
