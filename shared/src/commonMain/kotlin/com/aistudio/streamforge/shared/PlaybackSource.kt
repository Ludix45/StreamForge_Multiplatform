package com.aistudio.streamforge.shared

/**
 * A stream selected by the user from a source they are entitled to access.
 *
 * The model deliberately carries only an already-authorized URL and optional HTTP
 * headers. Provider authentication and DRM negotiation must stay in the provider's
 * official SDK or API rather than being reverse engineered by the application.
 */
data class PlaybackSource(
    val title: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

/**
 * Describes the narrow contract for a catalog/search integration shared by platforms.
 * Implementations should use documented, licensed, or user-owned data sources only.
 */
interface AuthorizedCatalogProvider {
    suspend fun search(query: String): List<CatalogItem>
}

/** A portable catalogue result that does not assume an Android or desktop UI. */
data class CatalogItem(
    val id: String,
    val title: String,
    val artworkUrl: String? = null,
    val description: String? = null,
)
