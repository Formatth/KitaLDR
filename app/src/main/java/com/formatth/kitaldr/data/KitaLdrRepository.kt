package com.formatth.kitaldr.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.UUID
import kotlin.random.Random

/**
 * Firebase-backed pairing layer.
 *
 * The app deliberately uses anonymous Firebase Auth for the first milestone:
 * no email, password, phone number, or public account is required.
 */
class KitaLdrRepository(context: Context) {
    private val app = FirebaseApp.getApps(context).firstOrNull()
    private val auth = app?.let { FirebaseAuth.getInstance(it) }
    private val db = app?.let { FirebaseFirestore.getInstance(it) }

    val isConfigured: Boolean
        get() = auth != null && db != null

    fun signIn(onResult: (Result<String>) -> Unit) {
        val firebaseAuth = auth
        val firestore = db
        if (firebaseAuth == null || firestore == null) {
            onResult(Result.failure(IllegalStateException("Firebase is not configured yet.")))
            return
        }

        val current = firebaseAuth.currentUser
        if (current != null) {
            ensureUserDocument(current.uid, firestore, onResult)
            return
        }

        firebaseAuth.signInAnonymously()
            .addOnSuccessListener { result ->
                ensureUserDocument(result.user!!.uid, firestore, onResult)
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(formatAuthError(error)))
            }
    }

    private fun formatAuthError(error: Exception): IllegalStateException {
        val detail = if (error is FirebaseAuthException) {
            "FirebaseAuthException code=${error.errorCode}; message=${error.message ?: "unknown"}"
        } else {
            "${error::class.java.simpleName}; message=${error.message ?: "unknown"}"
        }
        return IllegalStateException(detail, error)
    }

    private fun ensureUserDocument(
        uid: String,
        firestore: FirebaseFirestore,
        onResult: (Result<String>) -> Unit,
    ) {
        val ref = firestore.collection("users").document(uid)
        ref.get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    onResult(Result.success(uid))
                } else {
                    ref.set(
                        mapOf(
                            "displayName" to "My Love",
                            "pairId" to null,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    )
                        .addOnSuccessListener { onResult(Result.success(uid)) }
                        .addOnFailureListener { onResult(Result.failure(it)) }
                }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun currentUid(): String? = auth?.currentUser?.uid

    fun createPairingCode(onResult: (Result<String>) -> Unit) {
        val uid = currentUid()
        val firestore = db
        if (uid == null || firestore == null) {
            onResult(Result.failure(IllegalStateException("Not signed in to Firebase.")))
            return
        }

        val userRef = firestore.collection("users").document(uid)
        userRef.get()
            .addOnSuccessListener { user ->
                if (user.getString("pairId") != null) {
                    onResult(Result.failure(IllegalStateException("This device already has a partner.")))
                    return@addOnSuccessListener
                }

                val code = generatePairCode()
                val codeRef = firestore.collection("pairingCodes").document(code)
                codeRef.set(
                    mapOf(
                        "creatorUid" to uid,
                        "status" to "pending",
                        "expiresAt" to com.google.firebase.Timestamp(System.currentTimeMillis() / 1000 + 600, 0),
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                )
                    .addOnSuccessListener { onResult(Result.success(code)) }
                    .addOnFailureListener { onResult(Result.failure(it)) }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun listenForPairingAcceptance(code: String, onAccepted: (String) -> Unit): ListenerRegistration? {
        val firestore = db ?: return null
        val uid = currentUid() ?: return null

        return firestore.collection("pairingCodes").document(code)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                if (snapshot.getString("creatorUid") != uid) return@addSnapshotListener
                if (snapshot.getString("status") != "accepted") return@addSnapshotListener

                val coupleId = snapshot.getString("coupleId") ?: return@addSnapshotListener
                firestore.collection("users").document(uid).update(
                    mapOf(
                        "pairId" to coupleId,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).addOnSuccessListener { onAccepted(coupleId) }
            }
    }

    fun joinPairingCode(codeInput: String, onResult: (Result<String>) -> Unit) {
        val uid = currentUid()
        val firestore = db
        val code = normalizeCode(codeInput)

        if (uid == null || firestore == null) {
            onResult(Result.failure(IllegalStateException("Not signed in to Firebase.")))
            return
        }
        if (!PAIR_CODE_REGEX.matches(code)) {
            onResult(Result.failure(IllegalArgumentException("Invalid pairing code.")))
            return
        }

        val codeRef = firestore.collection("pairingCodes").document(code)
        val userRef = firestore.collection("users").document(uid)
        val coupleId = UUID.randomUUID().toString()
        val coupleRef = firestore.collection("couples").document(coupleId)

        firestore.runTransaction { transaction ->
            val codeSnapshot = transaction.get(codeRef)
            val userSnapshot = transaction.get(userRef)

            if (!codeSnapshot.exists()) throw IllegalStateException("Pairing code not found.")
            if (codeSnapshot.getString("status") != "pending") throw IllegalStateException("Pairing code has already been used.")

            val expiresAt = codeSnapshot.getTimestamp("expiresAt")
                ?: throw IllegalStateException("Pairing code is invalid.")
            if (expiresAt.toDate().time <= System.currentTimeMillis()) throw IllegalStateException("Pairing code has expired.")

            if (userSnapshot.getString("pairId") != null) throw IllegalStateException("This device already has a partner.")

            val creatorUid = codeSnapshot.getString("creatorUid")
                ?: throw IllegalStateException("Pairing code is invalid.")
            if (creatorUid == uid) throw IllegalStateException("You cannot pair with your own code.")

            transaction.set(
                coupleRef,
                mapOf(
                    "memberA" to creatorUid,
                    "memberB" to uid,
                    "pairingCode" to code,
                    "status" to "active",
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            )
            transaction.update(userRef, mapOf("pairId" to coupleId, "updatedAt" to FieldValue.serverTimestamp()))
            transaction.update(codeRef, mapOf("status" to "accepted", "acceptedBy" to uid, "coupleId" to coupleId))
            coupleId
        }
            .addOnSuccessListener { onResult(Result.success(it)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun disconnect(onResult: (Result<Unit>) -> Unit) {
        val uid = currentUid()
        val firestore = db
        if (uid == null || firestore == null) {
            onResult(Result.failure(IllegalStateException("Not signed in to Firebase.")))
            return
        }

        val userRef = firestore.collection("users").document(uid)
        userRef.get()
            .addOnSuccessListener { snapshot ->
                val coupleId = snapshot.getString("pairId")
                if (coupleId == null) {
                    onResult(Result.success(Unit))
                    return@addOnSuccessListener
                }

                firestore.runTransaction { transaction ->
                    transaction.update(userRef, mapOf("pairId" to null, "updatedAt" to FieldValue.serverTimestamp()))
                    val coupleRef = firestore.collection("couples").document(coupleId)
                    val coupleSnapshot = transaction.get(coupleRef)
                    if (coupleSnapshot.exists() && coupleSnapshot.getString("status") == "active") {
                        transaction.update(coupleRef, "status", "disconnected")
                    }
                    null
                }
                    .addOnSuccessListener { onResult(Result.success(Unit)) }
                    .addOnFailureListener { onResult(Result.failure(it)) }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun loadCurrentPair(onResult: (Result<PairInfo?>) -> Unit) {
        val uid = currentUid()
        val firestore = db
        if (uid == null || firestore == null) {
            onResult(Result.failure(IllegalStateException("Not signed in to Firebase.")))
            return
        }

        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { user ->
                val pairId = user.getString("pairId")
                if (pairId == null) {
                    onResult(Result.success(null))
                    return@addOnSuccessListener
                }

                firestore.collection("couples").document(pairId).get()
                    .addOnSuccessListener { couple ->
                        val memberA = couple.getString("memberA")
                        val memberB = couple.getString("memberB")
                        val partnerUid = if (memberA == uid) memberB else memberA
                        if (partnerUid == null) {
                            onResult(Result.failure(IllegalStateException("Couple data is invalid.")))
                            return@addOnSuccessListener
                        }

                        firestore.collection("users").document(partnerUid).get()
                            .addOnSuccessListener { partner ->
                                onResult(
                                    Result.success(
                                        PairInfo(
                                            coupleId = pairId,
                                            partnerUid = partnerUid,
                                            partnerName = partner.getString("displayName") ?: "My Love",
                                            status = couple.getString("status") ?: "active",
                                        )
                                    )
                                )
                            }
                            .addOnFailureListener { onResult(Result.failure(it)) }
                    }
                    .addOnFailureListener { onResult(Result.failure(it)) }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun setDisplayName(name: String, onResult: (Result<Unit>) -> Unit) {
        val uid = currentUid()
        val firestore = db
        if (uid == null || firestore == null) {
            onResult(Result.failure(IllegalStateException("Not signed in to Firebase.")))
            return
        }
        firestore.collection("users").document(uid).update(
            mapOf(
                "displayName" to name.trim().take(30).ifBlank { "My Love" },
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    private fun generatePairCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val raw = buildString { repeat(8) { append(alphabet[Random.nextInt(alphabet.length)]) } }
        return "${raw.substring(0, 4)}-${raw.substring(4)}"
    }

    companion object {
        private val PAIR_CODE_REGEX = Regex("^[A-Z2-9]{4}-[A-Z2-9]{4}$")
        fun normalizeCode(value: String): String = value.trim().uppercase().filter { it.isLetterOrDigit() || it == '-' }
    }
}

data class PairInfo(
    val coupleId: String,
    val partnerUid: String,
    val partnerName: String,
    val status: String,
)
