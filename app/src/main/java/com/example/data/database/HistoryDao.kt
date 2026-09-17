package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM viewing_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<HistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryItem)

    @Query("DELETE FROM viewing_history")
    suspend fun deleteAll()

    @Query("SELECT * FROM viewing_history ORDER BY timestamp DESC LIMIT 5")
    suspend fun getRecent(): List<HistoryItem>
}
