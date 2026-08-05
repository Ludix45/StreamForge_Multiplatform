package com.example.data.model

import java.io.Serializable

data class MediaItem(
    val id: Long,
    val name: String,
    val type: String,                 // "film", "serie", "ova", "tv", etc.
    val slug: String,
    val posterUrl: String?,
    val year: String?,
    val providerLanguage: String = "it"  // "it" or "en" for StreamingCommunity
) : Serializable {
    val isMovie: Boolean get() = type.lowercase() in listOf("film", "movie", "ova")
}

data class Episode(
    val id: Long,
    val number: Int,
    val name: String,
    val playUrl: String? = null,
    val token: String? = null
) : Serializable

data class Season(
    val number: Int,
    val name: String,
    val episodes: List<Episode>
) : Serializable

data class VideoParams(
    val token: String,
    val expires: String,
    val url: String,
    val canPlayFHD: Boolean
)
