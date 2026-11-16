package com.hopa.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hopa.data.database.entity.RunHistoryEntity

@Dao
interface RunHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: RunHistoryEntity)

    @Query("SELECT * FROM run_history ORDER BY at DESC LIMIT :limit")
    suspend fun latest(limit: Int = 100): List<RunHistoryEntity>

    @Query("SELECT * FROM run_history WHERE path = :path ORDER BY at DESC LIMIT 100")
    suspend fun byPath(path: String): List<RunHistoryEntity>

    @Query("DELETE FROM run_history WHERE id NOT IN (SELECT id FROM run_history ORDER BY at DESC LIMIT 200)")
    suspend fun prune()

    @Query("DELETE FROM run_history")
    suspend fun clear()
}
