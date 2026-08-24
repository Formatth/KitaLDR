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
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            Log.w(TAG, "User belum login — skip register token")
            return
        }

        Log.d(TAG, "Starting FCM token registration for uid=$uid")

        // Avoid FirebaseMessaging.getInstance() here because the current
        // Kotlin/Firebase Messaging combination can resolve the package-
        // private FirebaseApp overload. Obtain the component from the
        // default FirebaseApp instead.
        val messaging = FirebaseApp.getInstance().get(FirebaseMessaging::class.java)

        messaging.token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.e(TAG, "Gagal ambil FCM token", task.exception)
                    return@addOnCompleteListener
                }

                val token = task.result
                if (token.isNullOrBlank()) {
                    Log.w(TAG, "Token null/blank")
                    return@addOnCompleteListener
                }

                Log.d(TAG, "FCM token didapat: ${token.take(20)}...")

                val tokenRef = FirebaseFirestore.getInstance()
                    .collection("deviceTokens")
                    .document(uid)

                val data = mapOf(
                    "token" to token,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )

                Log.d(TAG, "Saving token to deviceTokens/$uid")

                tokenRef.set(data, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "Token berhasil disimpan ke deviceTokens/$uid")
                    }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "Gagal simpan token ke Firestore", error)
                    }
            }
    }

    // Compatibility entry point for the existing repository flow.
    fun register() = ensureTokenRegistered()
}
