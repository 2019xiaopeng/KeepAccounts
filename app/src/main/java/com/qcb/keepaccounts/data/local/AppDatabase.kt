package com.qcb.keepaccounts.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.qcb.keepaccounts.data.local.dao.AgentRunDao
import com.qcb.keepaccounts.data.local.dao.AgentToolCallDao
import com.qcb.keepaccounts.data.local.dao.ChatMessageDao
import com.qcb.keepaccounts.data.local.dao.TransactionDao
import com.qcb.keepaccounts.data.local.dao.UserConfigDao
import com.qcb.keepaccounts.data.local.entity.AgentRunEntity
import com.qcb.keepaccounts.data.local.entity.AgentToolCallEntity
import com.qcb.keepaccounts.data.local.entity.ChatMessageEntity
import com.qcb.keepaccounts.data.local.entity.TransactionEntity
import com.qcb.keepaccounts.data.local.entity.UserConfigEntity

@Database(
    entities = [
        TransactionEntity::class,
        ChatMessageEntity::class,
        UserConfigEntity::class,
        AgentRunEntity::class,
        AgentToolCallEntity::class,
    ],
    version = 3,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
    ],
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun userConfigDao(): UserConfigDao
    abstract fun agentRunDao(): AgentRunDao
    abstract fun agentToolCallDao(): AgentToolCallDao

    companion object {
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `agent_runs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `requestId` TEXT NOT NULL,
                        `idempotencyKey` TEXT NOT NULL,
                        `userInput` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `endedAt` INTEGER,
                        `errorCode` TEXT,
                        `errorMessage` TEXT
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_agent_runs_requestId` ON `agent_runs` (`requestId`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_agent_runs_startedAt` ON `agent_runs` (`startedAt`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_agent_runs_status` ON `agent_runs` (`status`)",
                )

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `agent_tool_calls` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `requestId` TEXT NOT NULL,
                        `runId` TEXT NOT NULL,
                        `stepIndex` INTEGER NOT NULL,
                        `toolName` TEXT NOT NULL,
                        `argsJson` TEXT NOT NULL,
                        `resultJson` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `errorCode` TEXT,
                        `errorMessage` TEXT,
                        `latencyMs` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_agent_tool_calls_requestId_stepIndex` ON `agent_tool_calls` (`requestId`, `stepIndex`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_agent_tool_calls_toolName` ON `agent_tool_calls` (`toolName`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_agent_tool_calls_timestamp` ON `agent_tool_calls` (`timestamp`)",
                )
            }
        }
    }
}
