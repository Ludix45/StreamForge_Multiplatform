package com.example.data.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object CommonDomainManager {
    private var domains: Map<String, String> = mapOf(
        "streamingcommunity" to "https://streamingcommunityz.pl/",
        "animeunity" to "https://www.animeunity.so/",
        "animeworld" to "https://www.animeworld.ac/",
        "eurostreaming" to "https://eurostream.ing/",
        "cinezo" to "https://www.cinezo.net/",
        "mostraguarda" to "https://v.vidxgo.co/"
    )

    fun updateDomains(jsonCache: String?) {
        if (jsonCache == null) return
        try {
            val cached: Map<String, String> = Json.decodeFromString(jsonCache)
            if (cached.isNotEmpty()) {
                domains = cached
            }
        } catch (e: Exception) {
            // Ignore parse errors
        }
    }

    suspend fun refreshDomains(): String? {
        val updatedDomains = domains.toMutableMap()
        try {
            val response = CommonHttpClient.get("https://t.me/s/Streaming_community_sito")
            val regex = Regex("streamingcommunity[a-z0-9]*\\.[a-z]+")
            val matches = regex.findAll(response).map { it.value }.toList()
            if (matches.isNotEmpty()) {
                val latestDomain = matches.last()
                updatedDomains["streamingcommunity"] = "https://$latestDomain/"
                domains = updatedDomains
                return Json.encodeToString(updatedDomains)
            }
        } catch (e: Exception) {}
        return null
    }

    fun getUrl(site: String): String {
        val siteKey = site.lowercase().replace(" ", "").trim()
        return domains[siteKey] ?: when (siteKey) {
            "streamingcommunity" -> "https://streamingcommunityz.pl/"
            "animeunity" -> "https://www.animeunity.so/"
            "animeworld" -> "https://www.animeworld.ac/"
            "eurostreaming" -> "https://eurostream.ing/"
            "cinezo" -> "https://www.cinezo.net/"
            "mostraguarda" -> "https://v.vidxgo.co/"
            "discoveryplus" -> "https://www.discoveryplus.com/it/"
            "discovery" -> "https://discoveryplus.it/"
            "dmax" -> "https://dmax.it/"
            "nove" -> "https://nove.tv/"
            "realtime" -> "https://realtime.it/"
            "homegardentv" -> "https://homegardentv.it/"
            "foodnetwork" -> "https://foodnetwork.it/"
            "mediasetinfinity" -> "https://mediasetinfinity.mediaset.it/"
            "raiplay" -> "https://www.raiplay.it/"
            "crunchyroll" -> "https://www.crunchyroll.com/it/"
            "primevideo" -> "https://www.primevideo.com/"
            "tubitv" -> "https://tubitv.com/"
            else -> "https://google.com/"
        }
    }
}
