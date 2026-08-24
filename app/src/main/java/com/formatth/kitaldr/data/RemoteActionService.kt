package com.formatth.kitaldr.data

import android.content.Context
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
        val uid = auth?.currentUser?.uid
        val firestore = db
        if (uid == null || firestore == null) {
            onResult(Result.failure(IllegalStateException("Not signed in to Firebase.")))
            return
        }
        if (type != ACTION_POKE) {
            onResult(Result.failure(IllegalArgumentException("Unsupported action.")))
            return
        }

        val coupleRef = firestore.collection("couples").document(coupleId)
        val actionRef = coupleRef.collection("actions").document(UUID.randomUUID().toString())

        coupleRef.get()
            .addOnSuccessListener { couple ->
                val memberA = couple.getString("memberA")
                val memberB = couple.getString("memberB")
                val status = couple.getString("status")
                if (status != "active" || (memberA != uid && memberB != uid)) {
                    onResult(Result.failure(IllegalStateException("This couple is no longer active.")))
                    return@addOnSuccessListener
                }
                val recipientUid = if (memberA == uid) memberB else memberA
                if (recipientUid.isNullOrBlank() || recipientUid == uid) {
                    onResult(Result.failure(IllegalStateException("Couple data is invalid.")))
                    return@addOnSuccessListener
                }

                actionRef.set(mapOf(
                    "type" to type,
                    "senderUid" to uid,
                    "recipientUid" to recipientUid,
                    "createdAt" to FieldValue.serverTimestamp(),
                )).addOnSuccessListener { onResult(Result.success(Unit)) }
                    .addOnFailureListener { onResult(Result.failure(it)) }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun listenForActions(coupleId: String, onAction: (RemoteAction) -> Unit): ListenerRegistration? {
        val uid = auth?.currentUser?.uid ?: return null
        val firestore = db ?: return null
        return firestore.collection("couples").document(coupleId)
            .collection("actions")
            .whereEqualTo("recipientUid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                for (change in snapshot.documentChanges) {
                    if (change.type != com.google.firebase.firestore.DocumentChange.Type.ADDED) continue
                    val type = change.document.getString("type") ?: continue
                    val senderUid = change.document.getString("senderUid") ?: continue
                    val action = RemoteAction(
                        id = change.document.id,
                        type = type,
                        senderUid = senderUid,
                    )
                    onAction(action)
                    change.document.reference.delete()
                }
            }
    }

    companion object {
        const val ACTION_POKE = "POKE"
    }
}

data class RemoteAction(
    val id: String,
    val type: String,
    val senderUid: String,
)
