package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "continue_watching")
data class ContinueWatchingItem(
    @PrimaryKey
    val id: String, // format: "${provider}_${mediaId}" to enforce one entry per series
    val provider: String,
    val mediaId: Long,
    val name: String,
    val type: String,
    val slug: String,
    val posterUrl: String?,
    val year: String?,
    val lastEpisodeId: Long?,
    val lastEpisodeNumber: Int?,
    val lastEpisodeName: String?,
    val lastSeasonNumber: Int?,
    val timestamp: Long,
    val lastPositionMillis: Long? = 0L,
    val durationMillis: Long? = 0L
)
