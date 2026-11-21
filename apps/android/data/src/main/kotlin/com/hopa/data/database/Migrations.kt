package com.hopa.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE sessions ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0",
            )
            database.execSQL(
                "ALTER TABLE sessions ADD COLUMN archivedAt INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `pending_approvals` (" +
                    "`id` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, " +
                    "`amount` TEXT NOT NULL, `merchant` TEXT NOT NULL, " +
                    "`consequence` TEXT NOT NULL, `isPayment` INTEGER NOT NULL, " +
                    "`actionJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
        }
    }

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `memory_facts` (" +
                    "`text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`text`))",
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `skills` (" +
                    "`trigger` TEXT NOT NULL, `action` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`trigger`))",
            )
        }
    }

    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE turns ADD COLUMN attachmentName TEXT NOT NULL DEFAULT ''",
            )
            database.execSQL(
                "ALTER TABLE turns ADD COLUMN attachmentMime TEXT NOT NULL DEFAULT ''",
            )
            database.execSQL(
                "ALTER TABLE turns ADD COLUMN attachmentBytes INTEGER NOT NULL DEFAULT 0",
            )
            database.execSQL(
                "ALTER TABLE turns ADD COLUMN attachmentPath TEXT NOT NULL DEFAULT ''",
            )
        }
    }

    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE pending_approvals ADD COLUMN highlightJson TEXT NOT NULL DEFAULT ''",
            )
            database.execSQL(
                "ALTER TABLE pending_approvals ADD COLUMN rememberPackage TEXT NOT NULL DEFAULT ''",
            )
        }
    }

    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `run_history` (" +
                    "`id` TEXT NOT NULL, `at` INTEGER NOT NULL, `goal` TEXT NOT NULL, " +
                    "`path` TEXT NOT NULL, `steps` INTEGER NOT NULL, " +
                    "`tokens` INTEGER NOT NULL, `conversationId` TEXT NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
        }
    }

    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE turns ADD COLUMN proof TEXT NOT NULL DEFAULT ''",
            )
        }
    }

    val MIGRATION_8_9: Migration = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `decision_votes` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`runId` TEXT NOT NULL, `step` INTEGER NOT NULL, " +
                    "`layerId` TEXT NOT NULL, `layerVersion` TEXT NOT NULL, " +
                    "`actionKind` TEXT NOT NULL, `actionClass` TEXT NOT NULL, " +
                    "`mode` TEXT NOT NULL, `verb` TEXT, `reason` TEXT NOT NULL, " +
                    "`confidence` REAL NOT NULL, `trace` TEXT NOT NULL, " +
                    "`latencyMicros` INTEGER NOT NULL, `applied` INTEGER NOT NULL, " +
                    "`error` TEXT, `notice` TEXT, `at` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_decision_votes_runId` " +
                    "ON `decision_votes` (`runId`)",
            )
        }
    }
}
