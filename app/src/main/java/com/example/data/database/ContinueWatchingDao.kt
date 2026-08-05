package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContinueWatchingDao {
    @Query("SELECT * FROM continue_watching ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ContinueWatchingItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ContinueWatchingItem)

    @Query("SELECT * FROM continue_watching WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ContinueWatchingItem?

    @Query("DELETE FROM continue_watching WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM continue_watching")
    suspend fun clearAll()
}
