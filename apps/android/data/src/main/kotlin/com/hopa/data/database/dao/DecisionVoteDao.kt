package com.hopa.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.hopa.data.database.entity.DecisionVoteEntity

@Dao
interface DecisionVoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(votes: List<DecisionVoteEntity>)

    @Query("SELECT * FROM decision_votes WHERE runId = :runId ORDER BY step, id")
    suspend fun forRun(runId: String): List<DecisionVoteEntity>

    @Query("SELECT * FROM decision_votes ORDER BY at DESC, id DESC")
    suspend fun all(): List<DecisionVoteEntity>

    @Query("DELETE FROM decision_votes")
    suspend fun clearAll()

    @Query(
        "DELETE FROM decision_votes WHERE id NOT IN " +
            "(SELECT id FROM decision_votes ORDER BY id DESC LIMIT :keep)",
    )
    suspend fun prune(keep: Int)

    /** One transaction: votes are never written without the prune that follows them. */
    @Transaction
    suspend fun insertAndPrune(votes: List<DecisionVoteEntity>, keep: Int) {
        insertAll(votes)
        prune(keep)
    }
}
