package com.aistudio.streamforge.desktop

import com.example.data.model.Episode
import com.example.data.model.MediaItem
import org.json.JSONArray
import org.json.JSONObject
import java.util.prefs.Preferences

/** A serialisable library entry, stored locally and independently from Android Room. */
data class LibraryEntry(
    val provider: String,
    val item: MediaItem,
    val seasonNumber: Int? = null,
    val episode: Episode? = null,
    /** Last confirmed playback time in milliseconds. */
    val resumePositionMs: Long = 0L,
    /** Total duration in milliseconds. */
    val durationMs: Long = 0L,
)

/**
 * Small desktop persistence layer for favourites and resume entries. It only stores
 * catalogue metadata, never stream URLs, tokens, credentials, or cookies.
 */
object DesktopLibraryStore {
    private val preferences = Preferences.userRoot().node("com/aistudio/streamforge/desktop")
    private const val FAVORITES = "favorites"
    private const val CONTINUE = "continue"

    fun favorites(): List<LibraryEntry> = read(FAVORITES)
    fun continueWatching(): List<LibraryEntry> = read(CONTINUE)
    fun isFavorite(provider: String, item: MediaItem): Boolean = favorites().any { it.provider == provider && it.item.id == item.id }

    fun toggleFavorite(entry: LibraryEntry) {
        val items = favorites().toMutableList()
        val index = items.indexOfFirst { it.provider == entry.provider && it.item.id == entry.item.id }
        if (index >= 0) items.removeAt(index) else items += entry.copy(seasonNumber = null, episode = null)
        write(FAVORITES, items)
    }

    fun saveProgress(entry: LibraryEntry) {
        val items = continueWatching().filterNot { it.provider == entry.provider && it.item.id == entry.item.id }.toMutableList()
        items.add(0, entry)
        write(CONTINUE, items.take(30))
    }

    fun removeContinue(entry: LibraryEntry) = write(CONTINUE, continueWatching().filterNot { it.provider == entry.provider && it.item.id == entry.item.id })

    fun savePlayerPrefs(audioLang: String, subId: Int?) {
        preferences.put("pref_audio_lang", audioLang)
        preferences.put("pref_sub_id", subId?.toString() ?: "no")
    }

    fun getPlayerPrefs(): Pair<String, Int?> {
        val audio = preferences.get("pref_audio_lang", "it")
        val subStr = preferences.get("pref_sub_id", "no")
        val subId = if (subStr == "no") null else subStr.toIntOrNull()
        return audio to subId
    }

    private fun read(key: String): List<LibraryEntry> = runCatching {
        val array = JSONArray(preferences.get(key, "[]"))
        List(array.length()) { index -> decode(array.getJSONObject(index)) }
    }.getOrDefault(emptyList())

    private fun write(key: String, entries: List<LibraryEntry>) {
        val array = JSONArray()
        entries.forEach { array.put(encode(it)) }
        preferences.put(key, array.toString())
    }

    private fun encode(entry: LibraryEntry) = JSONObject().apply {
        put("provider", entry.provider); put("id", entry.item.id); put("name", entry.item.name)
        put("type", entry.item.type); put("slug", entry.item.slug); put("poster", entry.item.posterUrl)
        put("year", entry.item.year); put("language", entry.item.providerLanguage); put("season", entry.seasonNumber); put("position", entry.resumePositionMs)
        put("duration", entry.durationMs)
        entry.episode?.let { put("episodeId", it.id); put("episodeNumber", it.number); put("episodeName", it.name) }
    }

    private fun decode(value: JSONObject): LibraryEntry {
        val item = MediaItem(value.getLong("id"), value.getString("name"), value.getString("type"), value.getString("slug"), value.optString("poster").ifBlank { null }, value.optString("year").ifBlank { null }, value.optString("language", "it"))
        val episode = value.optLong("episodeId", -1).takeIf { it >= 0 }?.let { id -> Episode(id, value.optInt("episodeNumber"), value.optString("episodeName", "Episodio")) }
        return LibraryEntry(value.getString("provider"), item, value.optInt("season", -1).takeIf { it >= 0 }, episode, value.optLong("position", 0L), value.optLong("duration", 0L))
    }
}
