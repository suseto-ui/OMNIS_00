package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.BuildConfig

@Database(
    entities = [
        OmnisRecord::class,
        MemoryFragment::class,
        OmnisArtifact::class,
        OmnisGoal::class,
        OmnisTelemetry::class,
        UserEntity::class,
        UserActivityLogEntity::class,
        OmnisSyncOutboxEntity::class
    ],
    version = 18,
    exportSchema = true
)
abstract class OmnisDatabase : RoomDatabase() {
    abstract fun omnisDao(): OmnisDao
    abstract fun userAuthDao(): UserAuthDao
    abstract fun userActivityLogDao(): UserActivityLogDao
    abstract fun outboxDao(): OmnisOutboxDao

    companion object {
        const val DATABASE_NAME = "omnis_local_db"

        @Volatile
        private var INSTANCE: OmnisDatabase? = null

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "omnis_messages", "hiddenScratchpad")) {
                    db.execSQL("ALTER TABLE `omnis_messages` ADD COLUMN `hiddenScratchpad` TEXT")
                }
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "omnis_messages", "qOmnisIndex")) {
                    db.execSQL("ALTER TABLE `omnis_messages` ADD COLUMN `qOmnisIndex` REAL NOT NULL DEFAULT 0.0")
                }
                if (!hasColumn(db, "omnis_messages", "qualityTier")) {
                    db.execSQL("ALTER TABLE `omnis_messages` ADD COLUMN `qualityTier` TEXT NOT NULL DEFAULT 'OPTIMAL'")
                }
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_omnis_memory_fragments_timestamp` " +
                        "ON `omnis_memory_fragments` (`timestamp`)"
                )
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `users` (
                        `id` TEXT NOT NULL,
                        `username` TEXT NOT NULL,
                        `passwordHash` TEXT NOT NULL,
                        `passwordSalt` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_users_username` ON `users` (`username`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_activity_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `action` TEXT NOT NULL,
                        `details` TEXT NOT NULL,
                        `severity` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "omnis_messages", "auditCommitmentHash")) {
                    db.execSQL("ALTER TABLE `omnis_messages` ADD COLUMN `auditCommitmentHash` TEXT")
                }
                db.execSQL(
                    "UPDATE `omnis_messages` SET `auditCommitmentHash` = `zkSnarkCommitmentHash` " +
                        "WHERE `auditCommitmentHash` IS NULL AND `zkSnarkCommitmentHash` IS NOT NULL"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_omnis_messages_isSyncedToPostgres_timestamp` " +
                        "ON `omnis_messages` (`isSyncedToPostgres`, `timestamp`)"
                )
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `omnis_sync_outbox` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `idempotencyKey` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `entityId` INTEGER NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `sequenceNumber` INTEGER NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        `retryCount` INTEGER NOT NULL,
                        `lastError` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `syncedAt` INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_omnis_sync_outbox_idempotencyKey` " +
                        "ON `omnis_sync_outbox` (`idempotencyKey`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_omnis_sync_outbox_syncStatus_createdAt` " +
                        "ON `omnis_sync_outbox` (`syncStatus`, `createdAt`)"
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `omnis_sync_outbox` (
                        `idempotencyKey`, `entityType`, `entityId`, `sessionId`,
                        `sequenceNumber`, `payloadJson`, `syncStatus`, `retryCount`, `createdAt`
                    )
                    SELECT
                        'migrated_msg_' || `id` || '_' || `timestamp`,
                        'OMNIS_MESSAGE',
                        `id`,
                        `threadId`,
                        `timestamp`,
                        '{}',
                        'PENDING',
                        0,
                        `timestamp`
                    FROM `omnis_messages`
                    WHERE `isSyncedToPostgres` = 0
                    """.trimIndent()
                )
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18
        )

        private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIdx = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIdx >= 0 && cursor.getString(nameIdx) == column) {
                        return true
                    }
                }
            }
            return false
        }

        fun getDatabase(context: Context): OmnisDatabase {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                val builder = Room.databaseBuilder(
                    appContext,
                    OmnisDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(*ALL_MIGRATIONS)

                if (BuildConfig.DEBUG && DatabaseConfig.isTesting) {
                    builder.fallbackToDestructiveMigration()
                }

                if (!DatabaseConfig.isTesting) {
                    System.loadLibrary("sqlcipher")
                    val passphrase = com.example.security.DatabasePassphraseProvider.getDatabasePassphrase(appContext)
                    builder.openHelperFactory(net.zetetic.database.sqlcipher.SupportOpenHelperFactory(passphrase))
                }

                val instance = builder.build()
                INSTANCE = instance
                instance
            }
        }

        fun getInMemoryDatabase(context: Context): OmnisDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                OmnisDatabase::class.java
            )
                .allowMainThreadQueries()
                .addMigrations(*ALL_MIGRATIONS)
                .build()
        }
    }
}
