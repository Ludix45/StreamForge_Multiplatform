package com.example.data.network

/**
 * JVM domain registry used in place of Android SharedPreferences-backed DomainManager.
 * It intentionally keeps the same public lookup method that Scraper.kt expects.
 */
object DomainManager {
    private val defaultDomains = mapOf(
        "streamingcommunity" to "https://streamingcommunityz.pl/",
        "animeunity" to "https://www.animeunity.so/",
        "animeworld" to "https://www.animeworld.ac/",
        "eurostreaming" to "https://eurostream.ing/",
        "cinezo" to "https://www.cinezo.net/",
        "mostraguarda" to "https://v.vidxgo.co/",
    )

    private val preferences = java.util.prefs.Preferences.userRoot().node("com/aistudio/streamforge/domains")
    @Volatile private var domains: Map<String, String> = loadCachedDomains() ?: defaultDomains

    fun getUrl(site: String): String = domains[site]
        ?: error("Provider desktop sconosciuto: $site")

    /** Refreshes the user-requested provider registry and persists the last successful result. */
    fun refreshDomains(): Boolean {
        val refreshed = defaultDomains.toMutableMap()
        return runCatching {
            val channel = HttpClient.get("https://t.me/s/Streaming_community_sito")
            Regex("streamingcommunity[a-z0-9]*\\.[a-z]+", RegexOption.IGNORE_CASE)
                .findAll(channel).lastOrNull()?.value?.let { domain ->
                    refreshed["streamingcommunity"] = "https://${domain.lowercase()}/"
                }
            domains = refreshed
            preferences.put("domains", org.json.JSONObject(refreshed).toString())
            true
        }.getOrElse { false }
    }

    private fun loadCachedDomains(): Map<String, String>? = runCatching {
        val json = org.json.JSONObject(preferences.get("domains", ""))
        json.keys().asSequence().associateWith { key -> json.getString(key) }.takeIf { it.isNotEmpty() }
    }.getOrNull()
}
