package com.hopa.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hopa.data.database.dao.MemoryDao
import com.hopa.data.database.dao.PendingApprovalDao
import com.hopa.data.database.dao.RunHistoryDao
import com.hopa.data.database.dao.RunRecordDao
import com.hopa.data.database.dao.SessionDao
import com.hopa.data.database.dao.SkillDao
import com.hopa.data.database.dao.TurnDao
import com.hopa.data.database.entity.MemoryFactEntity
import com.hopa.data.database.entity.PendingApprovalEntity
import com.hopa.data.database.entity.RunHistoryEntity
import com.hopa.data.database.entity.RunRecordEntity
import com.hopa.data.database.entity.SessionEntity
import com.hopa.data.database.entity.SkillEntity
import com.hopa.data.database.entity.TurnEntity

@Database(
    entities = [
        SessionEntity::class,
        TurnEntity::class,
        RunRecordEntity::class,
        PendingApprovalEntity::class,
        MemoryFactEntity::class,
        SkillEntity::class,
        RunHistoryEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
abstract class HopaDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun turnDao(): TurnDao

    abstract fun runRecordDao(): RunRecordDao

    abstract fun pendingApprovalDao(): PendingApprovalDao

    abstract fun memoryDao(): MemoryDao

    abstract fun skillDao(): SkillDao

    abstract fun runHistoryDao(): RunHistoryDao

    companion object {
        fun build(context: Context): HopaDatabase =
            Room.databaseBuilder(context, HopaDatabase::class.java, "hopa.db")
                .addMigrations(
                    Migrations.MIGRATION_1_2,
                    Migrations.MIGRATION_2_3,
                    Migrations.MIGRATION_3_4,
                    Migrations.MIGRATION_4_5,
        Migrations.MIGRATION_5_6,
                    Migrations.MIGRATION_6_7,
        Migrations.MIGRATION_7_8,
                )
                .build()
    }
}
