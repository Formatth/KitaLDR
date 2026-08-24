package com.formatth.kitaldr.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.UUID

/**
 * Lightweight realtime actions between the two members of an active couple.
 * Actions are ephemeral: the receiver consumes and deletes them after delivery.
 */
class RemoteActionService(context: Context) {
    private val app = FirebaseApp.getApps(context).firstOrNull()
    private val auth = app?.let { FirebaseAuth.getInstance(it) }
    private val db = app?.let { FirebaseFirestore.getInstance(it) }

    fun sendAction(coupleId: String, type: String, onResult: (Result<Unit>) -> Unit) {
        Log.d(TAG, "=== ACTION SEND START === type=$type coupleId=$coupleId")

        val uid = auth?.currentUser?.uid
        val firestore = db
        Log.d(TAG, "Sender uid=${uid ?: "NULL"}; FirebaseApp=${app?.options?.projectId ?: "NULL"}")

        if (uid == null || firestore == null) {
            Log.e(TAG, "ACTION SEND FAILED: Firebase auth/db not ready")
            onResult(Result.failure(IllegalStateException("Not signed in to Firebase.")))
            return
        }
        if (type != ACTION_POKE) {
            Log.e(TAG, "ACTION SEND FAILED: unsupported type=$type")
            onResult(Result.failure(IllegalArgumentException("Unsupported action.")))
            return
        }

        val coupleRef = firestore.collection("couples").document(coupleId)
        val actionId = UUID.randomUUID().toString()
        val actionRef = coupleRef.collection("actions").document(actionId)
        Log.d(TAG, "Reading couple: couples/$coupleId")

        coupleRef.get()
            .addOnSuccessListener { couple ->
                Log.d(TAG, "Couple read success: exists=${couple.exists()}")

                val memberA = couple.getString("memberA")
                val memberB = couple.getString("memberB")
                val status = couple.getString("status")
                Log.d(TAG, "Couple state: status=$status memberA=$memberA memberB=$memberB")

                if (status != "active" || (memberA != uid && memberB != uid)) {
                    Log.e(TAG, "ACTION SEND FAILED: couple inactive or sender is not a member")
                    onResult(Result.failure(IllegalStateException("This couple is no longer active.")))
                    return@addOnSuccessListener
                }

                val recipientUid = if (memberA == uid) memberB else memberA
                Log.d(TAG, "Resolved recipientUid=${recipientUid ?: "NULL"}")

                if (recipientUid.isNullOrBlank() || recipientUid == uid) {
                    Log.e(TAG, "ACTION SEND FAILED: invalid recipient")
                    onResult(Result.failure(IllegalStateException("Couple data is invalid.")))
                    return@addOnSuccessListener
                }

                val data = mapOf(
                    "type" to type,
                    "senderUid" to uid,
                    "recipientUid" to recipientUid,
                    "createdAt" to FieldValue.serverTimestamp(),
                )

                Log.d(TAG, "Writing action: couples/$coupleId/actions/$actionId")
                actionRef.set(data)
                    .addOnSuccessListener {
                        Log.d(TAG, "=== ACTION WRITE SUCCESS === recipient=$recipientUid actionId=$actionId")
                        onResult(Result.success(Unit))
                    }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "=== ACTION WRITE FAILED === ${error.message}", error)
                        onResult(Result.failure(error))
                    }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "=== COUPLE READ FAILED === ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    fun listenForActions(coupleId: String, onAction: (RemoteAction) -> Unit): ListenerRegistration? {
        val uid = auth?.currentUser?.uid
        val firestore = db

        Log.d(TAG, "=== ACTION LISTENER START === coupleId=$coupleId uid=${uid ?: "NULL"}")

        if (uid == null || firestore == null) {
            Log.e(TAG, "ACTION LISTENER FAILED TO START: auth/db not ready")
            return null
        }

        return firestore.collection("couples").document(coupleId)
            .collection("actions")
            .whereEqualTo("recipientUid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "=== ACTION LISTENER ERROR === ${error.message}", error)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    Log.e(TAG, "=== ACTION LISTENER ERROR === snapshot=null")
                    return@addSnapshotListener
                }

                Log.d(TAG, "ACTION SNAPSHOT: changes=${snapshot.documentChanges.size} total=${snapshot.documents.size}")

                for (change in snapshot.documentChanges) {
                    Log.d(TAG, "ACTION CHANGE: type=${change.type} id=${change.document.id}")
                    if (change.type != com.google.firebase.firestore.DocumentChange.Type.ADDED) continue

                    val type = change.document.getString("type") ?: continue
                    val senderUid = change.document.getString("senderUid") ?: continue
                    Log.d(TAG, "=== ACTION RECEIVED === type=$type sender=$senderUid id=${change.document.id}")

                    val action = RemoteAction(
                        id = change.document.id,
                        type = type,
                        senderUid = senderUid,
                    )
                    onAction(action)

                    change.document.reference.delete()
                        .addOnSuccessListener {
                            Log.d(TAG, "ACTION DELETED AFTER DELIVERY: ${change.document.id}")
                        }
                        .addOnFailureListener { deleteError ->
                            Log.e(TAG, "ACTION DELETE FAILED: ${deleteError.message}", deleteError)
                        }
                }
            }
    }

    companion object {
        private const val TAG = "RemoteActionService"
        const val ACTION_POKE = "POKE"
    }
}

data class RemoteAction(
    val id: String,
    val type: String,
    val senderUid: String,
)
