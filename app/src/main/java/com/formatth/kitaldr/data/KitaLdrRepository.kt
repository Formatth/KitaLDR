package com.formatth.kitaldr.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Timestamp
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.random.Random

/** Firebase-backed pairing layer. */
class KitaLdrRepository(context: Context) {
    private val app = FirebaseApp.getApps(context).firstOrNull()
    private val auth = app?.let { FirebaseAuth.getInstance(it) }
    private val db = app?.let { FirebaseFirestore.getInstance(it) }

    val isConfigured: Boolean get() = auth != null && db != null
    val firebaseProjectId: String? get() = app?.options?.projectId
    val firebaseApplicationId: String? get() = app?.options?.applicationId
    val firebaseApiKeyConfigured: Boolean get() = !app?.options?.apiKey.isNullOrBlank()
    val firebaseApiKeySuffix: String get() = app?.options?.apiKey?.takeLast(6)?.let { "...$it" } ?: "missing"

    fun currentUid(): String? = auth?.currentUser?.uid

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
                val uid = result.user?.uid
                if (uid == null) onResult(Result.failure(IllegalStateException("Firebase sign-in returned no user.")))
                else ensureUserDocument(uid, firestore, onResult)
            }
            .addOnFailureListener { error ->
                diagnoseAuthEndpoint { diagnosticResult ->
                    val sdkDetail = generateErrorDetail(error)
                    diagnosticResult.onSuccess { diagnostic ->
                        onResult(Result.failure(IllegalStateException(
                            "$sdkDetail | REST auth probe: HTTP ${diagnostic.httpStatus}; ${diagnostic.response}"
                        )))
                    }.onFailure { probeError ->
                        onResult(Result.failure(IllegalStateException(
                            "$sdkDetail | REST auth probe failed: ${probeError.message ?: "unknown"}"
                        )))
                    }
                }
            }
    }

    fun diagnoseAuthEndpoint(onResult: (Result<AuthEndpointDiagnostic>) -> Unit) {
        val firebaseApp = app
        if (firebaseApp == null) {
            onResult(Result.failure(IllegalStateException("Firebase app is not initialized.")))
            return
        }
        val apiKey = firebaseApp.options.apiKey
        if (apiKey.isNullOrBlank()) {
            onResult(Result.failure(IllegalStateException("Firebase API key is missing.")))
            return
        }
        Thread {
            try {
                val endpoint = URL("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey")
                val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 15000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                }
                connection.outputStream.use { it.write("{\"returnSecureToken\":true}".toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                onResult(Result.success(AuthEndpointDiagnostic(status, sanitizeAuthResponse(body))))
            } catch (error: Exception) {
                onResult(Result.failure(IllegalStateException("${error::class.java.simpleName}: ${error.message ?: "unknown"}", error)))
            }
        }.start()
    }

    private fun sanitizeAuthResponse(body: String): String = if (body.isBlank()) "empty response" else body
        .replace(Regex("\\\"idToken\\\"\\s*:\\s*\\\"[^\\\"]*\\\""), "\"idToken\":\"hidden\"")
        .replace(Regex("\\\"refreshToken\\\"\\s*:\\s*\\\"[^\\\"]*\\\""), "\"refreshToken\":\"hidden\"")
        .replace(Regex("\\\"localId\\\"\\s*:\\s*\\\"[^\\\"]*\\\""), "\"localId\":\"hidden\"")
        .take(600)

    private fun generateErrorDetail(error: Throwable): String {
        val parts = mutableListOf<String>()
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 5) {
            val type = current::class.java.name
            val code = (current as? FirebaseAuthException)?.errorCode
            val msg = current.message ?: "unknown"
            parts += if (code != null) "type=$type; code=$code; message=$msg" else "type=$type; message=$msg"
            current = current.cause
            depth++
        }
        return "Firebase sign-in failed: ${parts.joinToString(" | ")}"
    }

    private fun ensureUserDocument(uid: String, firestore: FirebaseFirestore, onResult: (Result<String>) -> Unit) {
        val ref = firestore.collection("users").document(uid)
        ref.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                PushTokenRegistrar.ensureTokenRegistered()
                onResult(Result.success(uid))
            } else {
                ref.set(mapOf(
                    "displayName" to "My Love",
                    "pairId" to null,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )).addOnSuccessListener {
                    PushTokenRegistrar.ensureTokenRegistered()
                    onResult(Result.success(uid))
                }.addOnFailureListener { onResult(Result.failure(it)) }
            }
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun loadCurrentDisplayName(onResult: (Result<String>) -> Unit) {
        val uid = currentUid()
        val firestore = db
        if (uid == null || firestore == null) {
            onResult(Result.failure(IllegalStateException("Not signed in to Firebase.")))
            return
        }
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                onResult(Result.success(snapshot.getString("displayName").orEmpty().trim()))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun createPairingCode(onResult: (Result<String>) -> Unit) {
        val uid = currentUid()
        val firestore = db
        if (uid == null || firestore == null) {
            onResult(Result.failure(IllegalStateException("Not signed in to Firebase.")))
            return
        }
        val userRef = firestore.collection("users").document(uid)
        val code = generatePairCode()
        val codeRef = firestore.collection("pairingCodes").document(code)
        firestore.runTransaction { transaction ->
            val user = transaction.get(userRef)
            val existingPairId = user.getString("pairId")
            if (existingPairId != null) {
                val coupleRef = firestore.collection("couples").document(existingPairId)
                val couple = transaction.get(coupleRef)
                if (couple.getString("status") != "disconnected") throw IllegalStateException("This device already has a partner.")
                transaction.update(userRef, mapOf("pairId" to null, "updatedAt" to FieldValue.serverTimestamp()))
            }
            transaction.set(codeRef, mapOf(
                "creatorUid" to uid,
                "status" to "pending",
                "expiresAt" to Timestamp(System.currentTimeMillis() / 1000 + 600, 0),
                "createdAt" to FieldValue.serverTimestamp(),
            ))
            code
        }.addOnSuccessListener { onResult(Result.success(it)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
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
            val expiresAt = codeSnapshot.getTimestamp("expiresAt") ?: throw IllegalStateException("Pairing code is invalid.")
            if (expiresAt.toDate().time <= System.currentTimeMillis()) throw IllegalStateException("Pairing code has expired.")
            if (userSnapshot.getString("pairId") != null) throw IllegalStateException("This device already has a partner.")
            val creatorUid = codeSnapshot.getString("creatorUid") ?: throw IllegalStateException("Pairing code is invalid.")
            if (creatorUid == uid) throw IllegalStateException("You cannot pair with your own code.")
            transaction.set(coupleRef, mapOf(
                "memberA" to creatorUid,
                "memberB" to uid,
                "pairingCode" to code,
                "status" to "active",
                "createdAt" to FieldValue.serverTimestamp(),
            ))
            transaction.update(userRef, mapOf("pairId" to coupleId, "updatedAt" to FieldValue.serverTimestamp()))
            transaction.update(codeRef, mapOf("status" to "accepted", "acceptedBy" to uid, "coupleId" to coupleId))
            coupleId
        }.addOnSuccessListener { onResult(Result.success(it)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun listenForPairingAcceptance(code: String, onAccepted: (String) -> Unit): ListenerRegistration? {
        val firestore = db ?: return null
        val uid = currentUid() ?: return null
        return firestore.collection("pairingCodes").document(code).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
            if (snapshot.getString("creatorUid") != uid || snapshot.getString("status") != "accepted") return@addSnapshotListener
            val coupleId = snapshot.getString("coupleId") ?: return@addSnapshotListener
            firestore.collection("users").document(uid).update(mapOf(
                "pairId" to coupleId,
                "updatedAt" to FieldValue.serverTimestamp(),
            )).addOnSuccessListener { onAccepted(coupleId) }
        }
    }

    fun listenForCoupleStatus(coupleId: String, onDisconnected: () -> Unit): ListenerRegistration? {
        val firestore = db ?: return null
        val uid = currentUid() ?: return null
        return firestore.collection("couples").document(coupleId).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
            val memberA = snapshot.getString("memberA")
            val memberB = snapshot.getString("memberB")
            if (memberA != uid && memberB != uid) return@addSnapshotListener
            if (snapshot.getString("status") == "disconnected") onDisconnected()
        }
    }

    fun disconnect(onResult: (Result<Unit>) -> Unit) {
        val uid = currentUid()
        val firestore = db
        if (uid == null || firestore == null) {
            onResult(Result.failure(IllegalStateException("Not signed in to Firebase.")))
            return
        }
        val userRef = firestore.collection("users").document(uid)
        firestore.runTransaction { transaction ->
            val userSnapshot = transaction.get(userRef)
            val coupleId = userSnapshot.getString("pairId") ?: return@runTransaction Unit
            val coupleRef = firestore.collection("couples").document(coupleId)
            val coupleSnapshot = transaction.get(coupleRef)
            if (!coupleSnapshot.exists()) {
                transaction.update(userRef, mapOf("pairId" to null, "updatedAt" to FieldValue.serverTimestamp()))
                return@runTransaction Unit
            }
            val memberA = coupleSnapshot.getString("memberA")
            val memberB = coupleSnapshot.getString("memberB")
            if (memberA != uid && memberB != uid) throw IllegalStateException("You are not a member of this couple.")
            val partnerUid = if (memberA == uid) memberB else memberA
            if (partnerUid == null || partnerUid == uid) throw IllegalStateException("Couple data is invalid.")
            val partnerRef = firestore.collection("users").document(partnerUid)
            transaction.get(partnerRef)
            transaction.update(userRef, mapOf("pairId" to null, "updatedAt" to FieldValue.serverTimestamp()))
            transaction.update(partnerRef, mapOf("pairId" to null, "updatedAt" to FieldValue.serverTimestamp()))
            if (coupleSnapshot.getString("status") == "active") transaction.update(coupleRef, "status", "disconnected")
            Unit
        }.addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun loadCurrentPair(onResult: (Result<PairInfo?>) -> Unit) {
        val uid = currentUid()
        val firestore = db
        if (uid == null || firestore == null) {
            onResult(Result.failure(IllegalStateException("Not signed in to Firebase.")))
            return
        }
        val userRef = firestore.collection("users").document(uid)
        userRef.get().addOnSuccessListener { user ->
            val pairId = user.getString("pairId")
            if (pairId == null) {
                onResult(Result.success(null))
                return@addOnSuccessListener
            }
            firestore.collection("couples").document(pairId).get().addOnSuccessListener { couple ->
                if (!couple.exists()) {
                    userRef.update(mapOf("pairId" to null, "updatedAt" to FieldValue.serverTimestamp()))
                        .addOnSuccessListener { onResult(Result.success(null)) }
                        .addOnFailureListener { onResult(Result.failure(it)) }
                    return@addOnSuccessListener
                }
                val status = couple.getString("status") ?: "active"
                if (status != "active") {
                    userRef.update(mapOf("pairId" to null, "updatedAt" to FieldValue.serverTimestamp()))
                        .addOnSuccessListener { onResult(Result.success(null)) }
                        .addOnFailureListener { onResult(Result.failure(it)) }
                    return@addOnSuccessListener
                }
                val memberA = couple.getString("memberA")
                val memberB = couple.getString("memberB")
                val partnerUid = if (memberA == uid) memberB else memberA
                if (partnerUid == null) {
                    onResult(Result.failure(IllegalStateException("Couple data is invalid.")))
                    return@addOnSuccessListener
                }
                firestore.collection("users").document(partnerUid).get().addOnSuccessListener { partner ->
                    onResult(Result.success(PairInfo(
                        coupleId = pairId,
                        selfName = user.getString("displayName") ?: "",
                        partnerUid = partnerUid,
                        partnerName = partner.getString("displayName") ?: "My Love",
                        status = status,
                    )))
                }.addOnFailureListener { onResult(Result.failure(it)) }
            }.addOnFailureListener { onResult(Result.failure(it)) }
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun setDisplayName(name: String, onResult: (Result<Unit>) -> Unit) {
        val uid = currentUid()
        val firestore = db
        if (uid == null || firestore == null) {
            onResult(Result.failure(IllegalStateException("Not signed in to Firebase.")))
            return
        }
        val cleanName = name.trim().take(30)
        if (cleanName.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Name cannot be empty.")))
            return
        }
        firestore.collection("users").document(uid).update(mapOf(
            "displayName" to cleanName,
            "updatedAt" to FieldValue.serverTimestamp(),
        )).addOnSuccessListener { onResult(Result.success(Unit)) }
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
    val selfName: String,
    val partnerUid: String,
    val partnerName: String,
    val status: String,
)

data class AuthEndpointDiagnostic(
    val httpStatus: Int,
    val response: String,
)
