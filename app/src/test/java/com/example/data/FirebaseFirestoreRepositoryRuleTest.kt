package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class FirebaseFirestoreRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveRecord_authenticatedOwner_succeedsAndEmitsRealtimeUpdate() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = FirebaseFirestoreRepository(firestore)
        val recordId = "rec_${UUID.randomUUID()}"

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveRecord(
                id = recordId,
                query = "Analyzuj dopad NIS2 na cloudovou infrastrukturu",
                response = "Konzistence matice AKGE-8D ověřena",
                domain = "SEC",
                confidenceScore = 0.94
            )
        }
        assertTrue("Uložení záznamu vlastníkem musí uspět: ${saveResult.exceptionOrNull()}", saveResult.isSuccess)

        val records = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserRecords().first { list -> list.any { it.id == recordId } }
        }
        assertTrue(records.any { it.id == recordId && it.domain == "SEC" })
    }

    @Test
    fun saveGoal_and_overrideHistory_authenticatedOwner_succeed() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = FirebaseFirestoreRepository(firestore)
        val goalId = "goal_${UUID.randomUUID()}"
        val overrideId = "ov_${UUID.randomUUID()}"

        val goalRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveGoal(
                id = goalId,
                title = "Cloud Run Zero-Trust Deployment",
                status = "active",
                progress = 100.0
            )
        }
        assertTrue("Uložení cíle vlastníkem musí uspět: ${goalRes.exceptionOrNull()}", goalRes.isSuccess)

        val ovRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.logOverrideEvent(
                OverrideHistoryEvent(
                    id = overrideId,
                    timestamp = System.currentTimeMillis(),
                    reason = "Verifikace immutability pravidel",
                    zkSnarkSeal = "a54e10894014d3b99c28dd23255ea82a89d49de005ab779f41998cf94988a4c8",
                    adminUser = ALICE_EMAIL
                )
            )
        }
        assertTrue("Zápis override_history musí uspět: ${ovRes.exceptionOrNull()}", ovRes.isSuccess)
    }

    @Test
    fun crossUserRead_isDeniedWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FirebaseFirestoreRepository(firestore)
        val recordId = "rec_${UUID.randomUUID()}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveRecord(
                id = recordId,
                query = "Tajný záznam Alice",
                response = "Důvěrná syntéza",
                domain = "SYS",
                confidenceScore = 0.91
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users")
                    .document(aliceUid)
                    .collection("records")
                    .document(recordId)
                    .get()
                    .await()
            }
            fail("Očekávána výjimka PERMISSION_DENIED při čtení cizího záznamu")
        } catch (ex: Exception) {
            val fsEx = ex.unwrapFirestoreException()
            assertNotNull("Chyba musí obsahovat FirebaseFirestoreException: $ex", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }

    @Test
    fun auditLog_session_and_pon_succeedForOwner_andFailCrossUser() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = FirebaseFirestoreRepository(firestore)

        val sessionRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveSession("sess_${UUID.randomUUID().toString().replace("-", "")}", "ACTIVE", "SEC")
        }
        assertTrue("Uložení session vlastníkem musí uspět: ${sessionRes.exceptionOrNull()}", sessionRes.isSuccess)

        val auditRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.logAuditEntry(
                logId = "audit_${UUID.randomUUID().toString().replace("-", "")}",
                actionType = "AKGE_EVAL",
                commitmentHash = "a54e10894014d3b99c28dd23255ea82a89d49de005ab779f41998cf94988a4c8",
                prevCommitmentHash = "GENESIS"
            )
        }
        assertTrue("Zápis do audit_logs musí uspět: ${auditRes.exceptionOrNull()}", auditRes.isSuccess)

        val ponRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.logProofOfNecessity(
                ponId = "pon_${UUID.randomUUID().toString().replace("-", "")}",
                triggerVector = "AKGE_8D_EVAL",
                exclusionProof = "CSR over O(V^2)",
                minimizationCheck = "Bounded 8 domains",
                systemTensionSigma = 0.25
            )
        }
        assertTrue("Zápis PoN musí uspět: ${ponRes.exceptionOrNull()}", ponRes.isSuccess)

        signInTestUser(BOB_EMAIL)
        assertTrue(aliceUid.isNotBlank())
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("omnis_sessions").document("forged_sess").set(
                    mapOf(
                        "sessionId" to "forged_sess",
                        "userId" to aliceUid,
                        "status" to "ACTIVE"
                    )
                ).await()
            }
            fail("Očekávána výjimka PERMISSION_DENIED při podvržení cizího userId")
        } catch (ex: Exception) {
            val fsEx = ex.unwrapFirestoreException()
            assertNotNull("Chyba musí obsahovat FirebaseFirestoreException: $ex", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }

    @Test
    fun unauthenticatedRead_isDeniedWithPermissionDenied() = runBlocking {
        auth.signOut()
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("system").document("metrics").get().await()
            }
            fail("Očekávána výjimka PERMISSION_DENIED pro nepřihlášeného uživatele")
        } catch (ex: Exception) {
            val fsEx = ex.unwrapFirestoreException()
            assertNotNull("Chyba musí obsahovat FirebaseFirestoreException: $ex", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice@omnis-test.example"
        const val BOB_EMAIL = "bob@omnis-test.example"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
