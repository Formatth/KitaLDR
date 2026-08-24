package com.formatth.kitaldr.data

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

/** Registers the current device's FCM token for the authenticated Firebase user. */
object PushTokenRegistrar {
    private const val TAG = "FCMTokenManager"

    fun ensureTokenRegistered() {
        Log.d(TAG, "=== FCM TOKEN REGISTRATION START ===")

        val app = try {
            FirebaseApp.getInstance()
        } catch (error: Exception) {
            Log.e(TAG, "FirebaseApp.getInstance() failed", error)
            return
        }

        Log.d(TAG, "FirebaseApp ready: projectId=${app.options.projectId}")

        val uid = try {
            FirebaseAuth.getInstance(app).currentUser?.uid
        } catch (error: Exception) {
            Log.e(TAG, "FirebaseAuth initialization failed", error)
            return
        }

        if (uid.isNullOrBlank()) {
            Log.w(TAG, "User belum login — skip register token")
            return
        }

        Log.d(TAG, "Authenticated uid=$uid")

        val messaging = try {
            // Use the FirebaseApp component API. This avoids the Kotlin overload
            // resolution problem encountered with FirebaseMessaging.getInstance().
            app.get(FirebaseMessaging::class.java)
        } catch (error: Exception) {
            Log.e(TAG, "FirebaseMessaging component initialization failed", error)
            return
        }

        if (messaging == null) {
            Log.e(TAG, "FirebaseMessaging component returned null")
            return
        }

        Log.d(TAG, "FirebaseMessaging component ready; requesting FCM token...")

        try {
            messaging.token
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.e(TAG, "Gagal ambil FCM token", task.exception)
                        return@addOnCompleteListener
                    }

                    val token = task.result
                    if (token.isNullOrBlank()) {
                        Log.w(TAG, "FCM token null/blank")
                        return@addOnCompleteListener
                    }

                    Log.d(TAG, "FCM token didapat: ${token.take(20)}...")

                    val tokenRef = FirebaseFirestore.getInstance(app)
                        .collection("deviceTokens")
                        .document(uid)

                    val data = mapOf(
                        "token" to token,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )

                    Log.d(TAG, "Saving token to deviceTokens/$uid")

                    tokenRef.set(data, SetOptions.merge())
                        .addOnSuccessListener {
                            Log.d(TAG, "=== TOKEN SAVED: deviceTokens/$uid ===")
                        }
                        .addOnFailureListener { error ->
                            Log.e(TAG, "Gagal simpan token ke Firestore", error)
                        }
                }
        } catch (error: Exception) {
            Log.e(TAG, "Exception while requesting FCM token", error)
        }
    }

    // Compatibility entry point for the existing repository flow.
    fun register() = ensureTokenRegistered()
}
