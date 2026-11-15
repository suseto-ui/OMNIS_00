package com.example.data

import android.app.Instrumentation
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OmnisDatabaseMigrationTest {

    private val testDbName = "omnis_migration_test_db"
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        instrumentation,
        OmnisDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate12To18_preservesMessagesAndBackfillsOutbox() {
        helper.createDatabase(testDbName, 12).apply {
            execSQL(
                """
                INSERT INTO omnis_messages (
                    id, role, content, cognitiveProcess, followUpQuestions,
                    valSys, valEcon, valPsych, valEco, valLaw, valSec, valPhys, valSoc,
                    compositeScore, domain, attachedImagePath, timestamp, defenseTier, defenseNotes,
                    isSyncedToPostgres, threadId, threadTitle, userName, isGlitchTriggered,
                    topologicalDiagnostic, zkSnarkCommitmentHash, proofOfNecessity,
                    confidenceScore, reliabilityStatus, suggestedClarifications
                ) VALUES (
                    42, 'user', 'Critically important AKGE query', 'PHASE_1', '',
                    0.9, 0.8, 0.7, 0.6, 0.95, 0.92, 0.85, 0.78,
                    0.83, 'SYSTEMS_INTELLIGENCE', NULL, 1700000123000, 'APPROVED', '',
                    0, 'thread_migrated', 'Migrated Thread', 'susetofu', 0,
                    NULL, 'legacy_commit_hash_42', 'PoN_42',
                    0.97, 'HIGH', ''
                )
                """.trimIndent()
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            testDbName,
            18,
            true,
            *OmnisDatabase.ALL_MIGRATIONS
        )

        migratedDb.query("SELECT content, auditCommitmentHash, qOmnisIndex, qualityTier FROM omnis_messages WHERE id = 42").use { cursor ->
            assertTrue("Záznam z v12 musí být po migraci na v18 zachován", cursor.moveToFirst())
            assertEquals("Critically important AKGE query", cursor.getString(0))
            assertEquals("legacy_commit_hash_42", cursor.getString(1))
            assertEquals(0.0f, cursor.getFloat(2), 0.001f)
            assertEquals("OPTIMAL", cursor.getString(3))
        }

        migratedDb.query("SELECT entityId, sessionId, syncStatus FROM omnis_sync_outbox WHERE entityId = 42").use { cursor ->
            assertTrue("Nesynchronizovaný záznam musí být při v17->v18 zapsán do Outboxu", cursor.moveToFirst())
            assertEquals(42L, cursor.getLong(0))
            assertEquals("thread_migrated", cursor.getString(1))
            assertEquals("PENDING", cursor.getString(2))
        }
        migratedDb.close()
    }

    @Test
    fun migrate17To18_createsOutboxAndPreservesExistingData() {
        helper.createDatabase("omnis_migration_17_18_db", 17).apply {
            execSQL(
                """
                INSERT INTO omnis_messages (
                    id, role, content, cognitiveProcess, followUpQuestions,
                    valSys, valEcon, valPsych, valEco, valLaw, valSec, valPhys, valSoc,
                    compositeScore, domain, attachedImagePath, timestamp, defenseTier, defenseNotes,
                    isSyncedToPostgres, threadId, threadTitle, userName, isGlitchTriggered,
                    topologicalDiagnostic, zkSnarkCommitmentHash, auditCommitmentHash, proofOfNecessity,
                    confidenceScore, reliabilityStatus, suggestedClarifications, hiddenScratchpad,
                    qOmnisIndex, qualityTier
                ) VALUES (
                    77, 'assistant', 'Verified v17 synthesis', '', '',
                    0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9,
                    0.9, 'SECURITY', NULL, 1700000777000, 'APPROVED', '',
                    0, 'thread_sec', 'Security Audit', 'admin', 0,
                    NULL, 'commit_77', 'commit_77', NULL,
                    0.99, 'HIGH', '', NULL,
                    0.92, 'OPTIMAL'
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            "omnis_migration_17_18_db",
            18,
            true,
            OmnisDatabase.MIGRATION_17_18
        ).close()

        val roomDb = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OmnisDatabase::class.java,
            "omnis_migration_17_18_db"
        ).addMigrations(*OmnisDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            assertEquals(1, roomDb.outboxDao().getPendingCount())
            val pending = roomDb.outboxDao().getPendingEntries()
            assertEquals(77L, pending.first().entityId)
            assertEquals("PENDING", pending.first().syncStatus)
        }
        roomDb.close()
    }
}
