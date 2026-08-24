package com.aistudio.streamforge.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaItem(
    val id: Long,
    val name: String,
    val type: String,
    val slug: String,
    val posterUrl: String?,
    val year: String?,
    val providerLanguage: String = "it"
) {
    val isMovie: Boolean get() = type.lowercase() in listOf("film", "movie", "ova")
}

@Serializable
data class Episode(
    val id: Long,
    val number: Int,
    val name: String,
    val playUrl: String? = null,
    val token: String? = null
)

@Serializable
data class Season(
    val number: Int,
    val name: String,
    val episodes: List<Episode>
)

@Serializable
data class LibraryEntry(
    val provider: String,
    val item: MediaItem,
    val seasonNumber: Int? = null,
    val episode: Episode? = null,
    val resumePositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
)
