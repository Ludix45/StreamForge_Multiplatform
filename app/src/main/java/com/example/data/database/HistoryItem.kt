package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "viewing_history")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val provider: String,
    val mediaId: Long,
    val name: String,
    val type: String,
    val slug: String,
    val posterUrl: String?,
    val year: String?,
    val timestamp: Long
)
