package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date

@IgnoreExtraProperties
data class CloudOmnisRecord(
    val id: String = "",
    val userId: String = "",
    val query: String = "",
    val response: String = "",
    val domain: String? = null,
    val confidenceScore: Double? = null,
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null
)

@IgnoreExtraProperties
data class CloudOmnisGoal(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val status: String = "active",
    val progress: Double? = null,
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null
)

class FirebaseFirestoreRepository(
    private val db: FirebaseFirestore
) {
    companion object {
        private const val TAG = "FirebaseFirestoreRepo"

        @Volatile
        private var instance: FirebaseFirestoreRepository? = null

        fun getInstance(context: Context): FirebaseFirestoreRepository {
            return instance ?: synchronized(this) {
                instance ?: FirebaseFirestoreRepository(
                    FirebaseFirestore.getInstance(
                        context.applicationContext.getString(R.string.firestore_database_id)
                    )
                ).also { instance = it }
            }
        }
    }

    private val auth = Firebase.auth

    private fun getUserId(): String? {
        return auth.currentUser?.uid
    }


    /**
     * Reálné sledování záznamů uživatele v reálném čase s bezpečnostním limitem pro ochranu nákladů.
     */
    fun observeUserRecords(limitCount: Long = 50L): Flow<List<CloudOmnisRecord>> = flow {
        val uid = getUserId() ?: return@flow
        val query = db.collection("users").document(uid).collection("records")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(limitCount)

        emitAll(
            query.snapshots()
                .map { snapshot -> snapshot.toObjects<CloudOmnisRecord>() }
                .catch { error ->
                    Log.e(TAG, "Chyba při čtení záznamů z Firestore: ${error.message}", error)
                    throw error
                }
        )
    }

    private suspend fun <T> kotlinx.coroutines.flow.FlowCollector<T>.emitAll(source: Flow<T>) {
        source.collect { value -> emit(value) }
    }

    /**
     * Uložení nového kognitivního záznamu do Firestore.
     */
    suspend fun saveRecord(
        id: String,
        query: String,
        response: String,
        domain: String? = null,
        confidenceScore: Double? = null
    ): Result<Unit> {
        return try {
            val uid = getUserId() ?: return Result.failure(Exception("Uživatel není přihlášen"))
            val docRef = db.collection("users").document(uid).collection("records").document(id)
            val payload = hashMapOf<String, Any>(
                "id" to id,
                "userId" to uid,
                "query" to query,
                "response" to response,
                "createdAt" to FieldValue.serverTimestamp()
            )
            domain?.let { payload["domain"] = it }
            confidenceScore?.let { payload["confidenceScore"] = it }

            docRef.set(payload).await()
            Log.d(TAG, "Záznam $id úspěšně uložen do cloudu")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Selhalo uložení záznamu do Firestore: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Uložení cíle do cloudu.
     */
    suspend fun saveGoal(
        id: String,
        title: String,
        status: String = "active",
        progress: Double = 0.0
    ): Result<Unit> {
        return try {
            val uid = getUserId() ?: return Result.failure(Exception("Uživatel není přihlášen"))
            val docRef = db.collection("users").document(uid).collection("goals").document(id)
            val payload = hashMapOf<String, Any>(
                "id" to id,
                "userId" to uid,
                "title" to title,
                "status" to status,
                "progress" to progress,
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Selhalo uložení cíle do Firestore: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun logAuditEntry(
        logId: String,
        actionType: String,
        commitmentHash: String,
        prevCommitmentHash: String = "GENESIS",
        overrideActive: Boolean = false
    ): Result<Unit> {
        return try {
            val uid = getUserId() ?: return Result.failure(Exception("Uživatel není přihlášen"))
            val payload = hashMapOf<String, Any>(
                "userId" to uid,
                "actionType" to actionType,
                "commitmentHash" to commitmentHash,
                "prevCommitmentHash" to prevCommitmentHash,
                "commitmentAlgorithm" to "HMAC-SHA256-HASH-CHAIN-v4.5",
                "timestamp" to System.currentTimeMillis(),
                "overrideActive" to overrideActive
            )
            db.collection("audit_logs").document(logId).set(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Selhal zápis do audit_logs: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun saveSession(
        sessionId: String,
        status: String = "ACTIVE",
        focusedDomain: String = "SYSTEMS_INTELLIGENCE"
    ): Result<Unit> {
        return try {
            val uid = getUserId() ?: return Result.failure(Exception("Uživatel není přihlášen"))
            val payload = hashMapOf<String, Any>(
                "sessionId" to sessionId,
                "userId" to uid,
                "status" to status,
                "focusedDomain" to focusedDomain,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("omnis_sessions").document(sessionId).set(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Selhalo uložení omnis_sessions: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun logProofOfNecessity(
        ponId: String,
        triggerVector: String,
        exclusionProof: String,
        minimizationCheck: String,
        systemTensionSigma: Double = 0.15
    ): Result<Unit> {
        return try {
            val uid = getUserId() ?: return Result.failure(Exception("Uživatel není přihlášen"))
            val payload = hashMapOf<String, Any>(
                "userId" to uid,
                "trigger_vector" to triggerVector,
                "exclusion_proof" to exclusionProof,
                "minimization_check" to minimizationCheck,
                "timestamp" to System.currentTimeMillis(),
                "system_tension_sigma" to systemTensionSigma.coerceIn(0.0, 1.0)
            )
            db.collection("proof_of_necessity").document(ponId).set(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Selhal zápis PoN záznamu: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun logOverrideEvent(event: OverrideHistoryEvent): Result<Unit> {
        return try {
            val uid = getUserId() ?: return Result.failure(Exception("Uživatel není přihlášen"))
            val payload = hashMapOf<String, Any>(
                "id" to event.id,
                "userId" to uid,
                "timestamp" to event.timestamp,
                "prevState" to event.prevState,
                "postState" to event.postState,
                "reason" to event.reason,
                "auditCommitmentSeal" to (event.auditCommitmentSeal.ifBlank { event.zkSnarkSeal }),
                "zkSnarkSeal" to (event.zkSnarkSeal.ifBlank { event.auditCommitmentSeal }),
                "prevCommitmentHash" to event.prevCommitmentHash,
                "adminUser" to event.adminUser
            )
            db.collection("users").document(uid).collection("override_history").document(event.id)
                .set(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOverrideHistory(limitCount: Long = 50L): Result<List<OverrideHistoryEvent>> {
        return try {
            val uid = getUserId() ?: return Result.failure(Exception("Uživatel není přihlášen"))
            val snapshot = db.collection("users").document(uid).collection("override_history")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limitCount)
                .get().await()
            Result.success(snapshot.toObjects(OverrideHistoryEvent::class.java))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class OverrideHistoryEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val prevState: Map<String, Any> = emptyMap(),
    val postState: Map<String, Any> = emptyMap(),
    val reason: String = "",
    val zkSnarkSeal: String = "",
    val auditCommitmentSeal: String = zkSnarkSeal,
    val prevCommitmentHash: String = "GENESIS",
    val adminUser: String = ""
)

