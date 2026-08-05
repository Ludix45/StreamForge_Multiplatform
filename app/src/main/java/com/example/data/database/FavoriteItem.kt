package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteItem(
    @PrimaryKey
    val id: String, // format: "${provider}_${mediaId}" to enforce uniqueness
    val provider: String,
    val mediaId: Long,
    val name: String,
    val type: String,
    val slug: String,
    val posterUrl: String?,
    val year: String?,
    val timestamp: Long
)
