package com.example.data.network

import com.example.data.model.Episode
import com.example.data.model.MediaItem
import com.example.data.model.Season
import com.example.data.model.VideoParams
import com.fleeksoft.ksoup.Ksoup
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

object CommonScraper {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun scoreAndRank(items: List<MediaItem>, query: String): List<MediaItem> {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return items
        return items.map { item ->
            val nameClean = item.name.lowercase().trim()
            var score = 0
            if (nameClean == q) {
                score += 1000
            } else if (nameClean.startsWith(q)) {
                score += 500
            } else if (nameClean.contains(q)) {
                score += 200
            }
            val queryWords = q.split(Regex("\\s+")).filter { it.length > 2 }
            if (queryWords.isNotEmpty()) {
                val matches = queryWords.count { nameClean.contains(it) }
                score += matches * 50
            }
            Pair(item, score)
        }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /* ==========================================================================================
       STREAMINGCOMMUNITY
       ========================================================================================== */

    private suspend fun getScInertiaVersion(baseUrl: String, lang: String): String {
        val html = CommonHttpClient.get("$baseUrl$lang")
        val doc = Ksoup.parse(html)
        val appDiv = doc.select("div#app").first()
            ?: throw Exception("StreamingCommunity app div#app wrapper not found")
        val dataPage = appDiv.attr("data-page")
        val obj = json.parseToJsonElement(dataPage).jsonObject
        return obj["version"]?.jsonPrimitive?.content ?: ""
    }

    suspend fun searchStreamingCommunity(query: String, domain: String): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val languages = listOf("it", "en")

        for (lang in languages) {
            try {
                val version = getScInertiaVersion(domain, lang)
                val searchUrl = "$domain$lang/search?q=${query.encodeURLParameter()}"

                val jsonStr = CommonHttpClient.get(
                    searchUrl,
                    mapOf(
                        "X-Inertia" to "true",
                        "X-Inertia-Version" to version,
                        "Referer" to "$domain$lang/"
                    )
                )

                val obj = json.parseToJsonElement(jsonStr).jsonObject
                val props = obj["props"]?.jsonObject ?: continue
                val titles = props["titles"]?.jsonArray ?: continue

                for (titleElem in titles) {
                    val titleObj = titleElem.jsonObject
                    try {
                        val item = parseScTitle(titleObj, lang, domain)
                        list.add(item)
                    } catch (e: Exception) {
                        println("Error parsing SC search title: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                // Log.e
            }
        }
        val deduplicated = list.groupBy { it.id }.map { (_, items) ->
            items.find { it.providerLanguage == "it" } ?: items.first()
        }
        return scoreAndRank(deduplicated, query)
    }

    private fun parseScTitle(obj: JsonObject, lang: String, baseUrl: String): MediaItem {
        val id = obj["id"]?.jsonPrimitive?.long ?: 0L
        val name = obj["name"]?.jsonPrimitive?.content ?: ""
        val slug = obj["slug"]?.jsonPrimitive?.content ?: ""
        val type = obj["type"]?.jsonPrimitive?.content ?: "film"
        
        var year: String? = null
        val translations = obj["translations"]?.jsonArray
        if (translations != null) {
            for (t in translations) {
                val tObj = t.jsonObject
                val key = tObj["key"]?.jsonPrimitive?.content
                if (key == "first_air_date" || key == "release_date") {
                    val v = tObj["value"]?.jsonPrimitive?.content
                    if (!v.isNullOrBlank()) {
                        year = v.substringBefore("-")
                        break
                    }
                }
            }
        }
        if (year == null) {
            year = obj["last_air_date"]?.jsonPrimitive?.content?.substringBefore("-")
                   ?: obj["release_date"]?.jsonPrimitive?.content?.substringBefore("-")
        }

        var poster: String? = null
        val images = obj["images"]?.jsonArray
        if (images != null) {
            val preferredTypes = listOf("poster", "cover", "cover_mobile", "background")
            outer@for (ptype in preferredTypes) {
                for (img in images) {
                    val imgObj = img.jsonObject
                    if (imgObj["type"]?.jsonPrimitive?.content == ptype) {
                        val filename = imgObj["filename"]?.jsonPrimitive?.content
                        if (!filename.isNullOrBlank()) {
                            val cdnUrl = baseUrl.replace("stream", "cdn.stream")
                            poster = "${cdnUrl}images/$filename"
                            break@outer
                        }
                    }
                }
            }
        }

        return MediaItem(id, name, type, slug, poster, year, lang)
    }

    suspend fun getStreamingCommunitySeasons(item: MediaItem, domain: String): List<Season> {
        val seasons = mutableListOf<Season>()
        try {
            val url = "$domain${item.providerLanguage}/titles/${item.id}-${item.slug}"
            val html = CommonHttpClient.get(url)
            val doc = Ksoup.parse(html)
            val appDiv = doc.select("div#app").first() ?: return emptyList()
            val dataPage = appDiv.attr("data-page")
            val obj = json.parseToJsonElement(dataPage).jsonObject
            val props = obj["props"]?.jsonObject ?: return emptyList()
            val titleObj = props["title"]?.jsonObject ?: return emptyList()
            val seasonsArr = titleObj["seasons"]?.jsonArray ?: return emptyList()

            for (sElem in seasonsArr) {
                val sObj = sElem.jsonObject
                val num = sObj["number"]?.jsonPrimitive?.int ?: 1
                seasons.add(Season(num, "Stagione $num", emptyList()))
            }
        } catch (e: Exception) {
            // Log
        }
        return seasons.sortedBy { it.number }
    }

    suspend fun getStreamingCommunityEpisodes(item: MediaItem, seasonNumber: Int, domain: String): List<Episode> {
        val list = mutableListOf<Episode>()
        try {
            val lang = item.providerLanguage
            val version = getScInertiaVersion(domain, lang)
            val url = "$domain$lang/titles/${item.id}-${item.slug}/season-$seasonNumber"

            val jsonStr = CommonHttpClient.get(
                url,
                mapOf(
                    "X-Inertia" to "true",
                    "X-Inertia-Version" to version,
                    "Referer" to "$domain$lang/"
                )
            )

            val obj = json.parseToJsonElement(jsonStr).jsonObject
            val props = obj["props"]?.jsonObject ?: return emptyList()
            val loadedSeason = props["loadedSeason"]?.jsonObject ?: return emptyList()
            val episodesArr = loadedSeason["episodes"]?.jsonArray ?: return emptyList()

            for (epElem in episodesArr) {
                val epObj = epElem.jsonObject
                val num = epObj["number"]?.jsonPrimitive?.int ?: 0
                list.add(
                    Episode(
                        id = epObj["id"]?.jsonPrimitive?.long ?: 0L,
                        number = num,
                        name = epObj["name"]?.jsonPrimitive?.content ?: "Episodio $num"
                    )
                )
            }
        } catch (e: Exception) {
            // Log
        }
        return list.sortedBy { it.number }
    }

    suspend fun extractStreamingCommunityUrl(item: MediaItem, episodeId: Long?, domain: String): String {
        val parentUrl: String
        val iframeSrc = if (episodeId == null || item.isMovie) {
            val url = "$domain${item.providerLanguage}/iframe/${item.id}"
            parentUrl = url
            val html = CommonHttpClient.get(url, mapOf("Referer" to domain))
            val doc = Ksoup.parse(html)
            doc.select("iframe").first()?.attr("src") ?: throw Exception("SC movie iframe not found")
        } else {
            val url = "$domain${item.providerLanguage}/iframe/${item.id}?episode_id=$episodeId&next_episode=1"
            parentUrl = url
            val html = CommonHttpClient.get(url, mapOf("Referer" to domain))
            val doc = Ksoup.parse(html)
            doc.select("iframe").first()?.attr("src") ?: throw Exception("SC episode iframe not found")
        }

        val embedHtml = CommonHttpClient.get(iframeSrc, mapOf("Referer" to parentUrl))
        val embedDoc = Ksoup.parse(embedHtml)
        val scriptText = embedDoc.select("body script").first()?.html() ?: ""
        
        val params = parseScript(scriptText) ?: throw Exception("SC video params not found")
        return buildM3u8Url(params)
    }

    private fun parseScript(script: String): VideoParams? {
        val token = Regex("""token["']?\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(script)?.groupValues?.get(1) ?: return null
        val expires = Regex("""expires["']?\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(script)?.groupValues?.get(1) ?: return null
        val url = Regex("""url["']?\s*:\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE).find(script)?.groupValues?.get(1) ?: return null
        val canPlayFHD = Regex("""canPlayFHD\s*=\s*(true|false)""", RegexOption.IGNORE_CASE).find(script)?.groupValues?.get(1) == "true"
        return VideoParams(token, expires, url, canPlayFHD)
    }

    private fun buildM3u8Url(params: VideoParams): String {
        val url = Url(params.url)
        return URLBuilder(url).apply {
            if (params.canPlayFHD) parameters.append("h", "1")
            if (url.parameters["b"] == "1") parameters.append("b", "1")
            parameters.append("token", params.token)
            parameters.append("expires", params.expires)
        }.buildString()
    }

    /* ==========================================================================================
       ANIMEUNITY
       ========================================================================================== */

    suspend fun searchAnimeUnity(query: String, domain: String): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        try {
            val response = CommonHttpClient.getWithResponse(domain)
            val cookies = response.headers.getAll(HttpHeaders.SetCookie)?.associate { header ->
                val parts = header.split(";")[0].split("=", limit = 2)
                parts[0].trim() to parts.getOrElse(1) { "" }.trim()
            } ?: emptyMap()
            
            val sessionCookie = cookies["animeunity_session"] ?: ""
            val rawXsrfToken = cookies["XSRF-TOKEN"] ?: ""
            val xsrfToken = rawXsrfToken.decodeURLQueryComponent()

            val cookieHeaderVal = "XSRF-TOKEN=$rawXsrfToken; animeunity_session=$sessionCookie"
            val commonHeaders = mapOf(
                "Origin" to domain.removeSuffix("/"),
                "Referer" to domain,
                "X-XSRF-TOKEN" to xsrfToken,
                "Cookie" to cookieHeaderVal
            )

            // Step 1: Livesearch
            try {
                val liveResponse = CommonHttpClient.postForm(
                    "$domain/livesearch",
                    parameters { append("title", query) },
                    commonHeaders
                )
                val obj = json.parseToJsonElement(liveResponse).jsonObject
                obj["records"]?.jsonArray?.forEach { list.add(parseAuTitle(it.jsonObject)) }
            } catch (e: Exception) {}

            // Step 2: Archivio
            try {
                val archivePayload = """{"title":"$query","type":false,"year":false,"order":false,"status":false,"genres":false,"offset":0,"dubbed":false,"season":false}"""
                val archiveResponse = CommonHttpClient.post(
                    "$domain/archivio/get-animes",
                    archivePayload,
                    commonHeaders + ("Content-Type" to "application/json")
                )
                val obj = json.parseToJsonElement(archiveResponse).jsonObject
                obj["records"]?.jsonArray?.forEach { list.add(parseAuTitle(it.jsonObject)) }
            } catch (e: Exception) {}

        } catch (e: Exception) {}
        return scoreAndRank(list.distinctBy { it.id }, query)
    }

    private fun parseAuTitle(obj: JsonObject): MediaItem {
        val id = obj["id"]?.jsonPrimitive?.long ?: 0L
        val nameEng = obj["title_eng"]?.jsonPrimitive?.content ?: ""
        val nameDefault = obj["title"]?.jsonPrimitive?.content ?: ""
        val nameIt = obj["title_it"]?.jsonPrimitive?.content ?: ""
        val name = when {
            nameEng.isNotBlank() -> nameEng
            nameDefault.isNotBlank() -> nameDefault
            nameIt.isNotBlank() -> nameIt
            else -> "Anime ID $id"
        }
        val type = obj["type"]?.jsonPrimitive?.content ?: "tv"
        val slug = obj["slug"]?.jsonPrimitive?.content ?: ""
        val poster = obj["imageurl"]?.jsonPrimitive?.content?.trim()?.ifBlank { null }
        return MediaItem(id, name, type, slug, poster, null, "it")
    }

    suspend fun getAnimeUnityEpisodes(mediaId: Long, domain: String): List<Episode> {
        val allEpisodes = mutableListOf<Episode>()
        try {
            val infoResponse = CommonHttpClient.get("$domain/info_api/$mediaId/")
            val infoObj = json.parseToJsonElement(infoResponse).jsonObject
            val episodesCount = infoObj["episodes_count"]?.jsonPrimitive?.int ?: 0

            var startRange = 1
            while (startRange <= episodesCount) {
                val endRange = startRange + 119
                try {
                    val chunkUrl = "$domain/info_api/$mediaId/1?start_range=$startRange&end_range=$endRange"
                    val chunkResponse = CommonHttpClient.get(chunkUrl)
                    val chunkObj = json.parseToJsonElement(chunkResponse).jsonObject
                    chunkObj["episodes"]?.jsonArray?.forEach {
                        val epObj = it.jsonObject
                        val num = epObj["number"]?.jsonPrimitive?.int ?: 0
                        allEpisodes.add(Episode(epObj["id"]?.jsonPrimitive?.long ?: 0L, num, "Episodio $num"))
                    }
                } catch (e: Exception) {}
                startRange += 120
            }
        } catch (e: Exception) {}
        return allEpisodes.sortedBy { it.number }
    }

    /* ==========================================================================================
       ANIMEWORLD
       ========================================================================================== */

    suspend fun searchAnimeWorld(query: String, domain: String): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        try {
            val searchUrl = "${domain.removeSuffix("/")}/search?keyword=${query.encodeURLParameter()}"
            val html = CommonHttpClient.get(searchUrl)
            val doc = Ksoup.parse(html)
            doc.select("a.poster").forEach { element ->
                try {
                    val img = element.selectFirst("img")
                    val name = img?.attr("alt") ?: ""
                    val href = element.attr("href") ?: ""
                    if (name.isBlank() || href.isBlank()) return@forEach
                    val posterUrl = img?.attr("src")
                    val type = if (element.selectFirst("div.status")?.selectFirst("div.movie") != null) "film" else "tv"
                    val slug = href.removePrefix("/")
                    list.add(MediaItem(slug.hashCode().toLong(), name, type, slug, posterUrl, null, "it"))
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {}
        return scoreAndRank(list, query)
    }

    suspend fun getAnimeWorldEpisodes(item: MediaItem, domain: String): List<Episode> {
        val list = mutableListOf<Episode>()
        try {
            val url = if (item.slug.startsWith("http")) item.slug else "${domain.removeSuffix("/")}/${item.slug}"
            val html = CommonHttpClient.get(url)
            val doc = Ksoup.parse(html)
            doc.select("li.episode > a").forEach { aTag ->
                val epNum = aTag.attr("data-episode-num").toIntOrNull() ?: 1
                val epId = aTag.attr("data-episode-id").toLongOrNull() ?: epNum.toLong()
                list.add(Episode(epId, epNum, "Episodio $epNum", aTag.attr("href"), aTag.attr("data-id")))
            }
        } catch (e: Exception) {}
        return list.sortedBy { it.number }
    }

    /* ==========================================================================================
       EUROSTREAMING
       ========================================================================================== */

    suspend fun searchEuroStreaming(query: String, domain: String): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        try {
            val searchUrl = "${domain.removeSuffix("/")}/?s=${query.encodeURLParameter()}"
            val html = CommonHttpClient.get(searchUrl)
            val doc = Ksoup.parse(html)
            doc.select("div.post-thumb > a").forEach { aTag ->
                val href = aTag.attr("href") ?: ""
                val img = aTag.selectFirst("img")
                val name = img?.attr("alt") ?: ""
                val poster = img?.attr("src")
                if (name.isNotBlank() && href.isNotBlank()) {
                    list.add(MediaItem(href.hashCode().toLong(), name, "tv", href, poster, null, "it"))
                }
            }
        } catch (e: Exception) {}
        return scoreAndRank(list, query)
    }

    suspend fun getEuroStreamingSeasons(item: MediaItem): List<Season> {
        val list = mutableListOf<Season>()
        try {
            val html = CommonHttpClient.get(item.slug)
            val doc = Ksoup.parse(html)
            doc.select("div.entry-content > p").forEach { p ->
                val text = p.text()
                if (text.contains("STAGIONE", ignoreCase = true)) {
                    val num = text.filter { it.isDigit() }.toIntOrNull() ?: 1
                    val episodes = mutableListOf<Episode>()
                    p.select("a").forEach { a ->
                        val epText = a.text()
                        val epNum = epText.substringBefore(" ").filter { it.isDigit() }.toIntOrNull() ?: 1
                        episodes.add(Episode(epNum.toLong(), epNum, "Episodio $epNum", a.attr("href")))
                    }
                    list.add(Season(num, "Stagione $num", episodes))
                }
            }
        } catch (e: Exception) {}
        return list.sortedBy { it.number }
    }

    /* ==========================================================================================
       TMDB & TRENDING
       ========================================================================================== */

    suspend fun getTrending(isMovie: Boolean, apiKey: String, language: String): List<MediaItem> {
        val type = if (isMovie) "movie" else "tv"
        val url = "https://api.themoviedb.org/3/trending/$type/week?api_key=$apiKey&language=$language"
        val results = mutableListOf<MediaItem>()
        try {
            val jsonStr = CommonHttpClient.get(url)
            val obj = json.parseToJsonElement(jsonStr).jsonObject
            val jsonArr = obj["results"]?.jsonArray ?: return emptyList()
            for (elem in jsonArr) {
                val o = elem.jsonObject
                val id = o["id"]?.jsonPrimitive?.long ?: 0L
                val title = o["title"]?.jsonPrimitive?.content ?: o["name"]?.jsonPrimitive?.content ?: ""
                val posterPath = o["poster_path"]?.jsonPrimitive?.content ?: ""
                val releaseDate = o["release_date"]?.jsonPrimitive?.content ?: o["first_air_date"]?.jsonPrimitive?.content ?: ""
                val year = releaseDate.split("-").firstOrNull() ?: ""
                results.add(MediaItem(id, title, if (isMovie) "film" else "tv", "tmdb_home_item", "https://image.tmdb.org/t/p/w500$posterPath", year))
            }
        } catch (e: Exception) {}
        return results
    }

    suspend fun getMoviesByGenre(genreId: Int, apiKey: String, language: String): List<MediaItem> {
        val url = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&language=$language&with_genres=$genreId&sort_by=popularity.desc"
        val results = mutableListOf<MediaItem>()
        try {
            val jsonStr = CommonHttpClient.get(url)
            val obj = json.parseToJsonElement(jsonStr).jsonObject
            val jsonArr = obj["results"]?.jsonArray ?: return emptyList()
            for (elem in jsonArr) {
                val o = elem.jsonObject
                val id = o["id"]?.jsonPrimitive?.long ?: 0L
                val posterPath = o["poster_path"]?.jsonPrimitive?.content ?: ""
                results.add(MediaItem(id, o["title"]?.jsonPrimitive?.content ?: "", "film", "tmdb_home_item", "https://image.tmdb.org/t/p/w500$posterPath", ""))
            }
        } catch (e: Exception) {}
        return results
    }

    /* ==========================================================================================
       CINEZO
       ========================================================================================== */

    suspend fun searchCinezo(query: String, apiKey: String): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        // Search movies
        try {
            val movieUrl = "https://api.themoviedb.org/3/search/movie?api_key=$apiKey&query=${query.encodeURLParameter()}&language=it"
            val jsonStr = CommonHttpClient.get(movieUrl)
            val obj = json.parseToJsonElement(jsonStr).jsonObject
            obj["results"]?.jsonArray?.forEach {
                val m = it.jsonObject
                val posterPath = m["poster_path"]?.jsonPrimitive?.content ?: ""
                val releaseDate = m["release_date"]?.jsonPrimitive?.content ?: ""
                list.add(MediaItem(m["id"]?.jsonPrimitive?.long ?: 0L, m["title"]?.jsonPrimitive?.content ?: "", "film", "movie", "https://image.tmdb.org/t/p/w500$posterPath", releaseDate.take(4)))
            }
        } catch (e: Exception) {}
        // Search TV
        try {
            val tvUrl = "https://api.themoviedb.org/3/search/tv?api_key=$apiKey&query=${query.encodeURLParameter()}&language=it"
            val jsonStr = CommonHttpClient.get(tvUrl)
            val obj = json.parseToJsonElement(jsonStr).jsonObject
            obj["results"]?.jsonArray?.forEach {
                val s = it.jsonObject
                val posterPath = s["poster_path"]?.jsonPrimitive?.content ?: ""
                val firstAirDate = s["first_air_date"]?.jsonPrimitive?.content ?: ""
                list.add(MediaItem(s["id"]?.jsonPrimitive?.long ?: 0L, s["name"]?.jsonPrimitive?.content ?: "", "tv", "tv", "https://image.tmdb.org/t/p/w500$posterPath", firstAirDate.take(4)))
            }
        } catch (e: Exception) {}
        return scoreAndRank(list, query)
    }

    suspend fun getCinezoSeasons(item: MediaItem, apiKey: String): List<Season> {
        val list = mutableListOf<Season>()
        try {
            val detailUrl = "https://api.themoviedb.org/3/tv/${item.id}?api_key=$apiKey&language=it"
            val jsonStr = CommonHttpClient.get(detailUrl)
            val obj = json.parseToJsonElement(jsonStr).jsonObject
            obj["seasons"]?.jsonArray?.forEach {
                val s = it.jsonObject
                val sn = s["season_number"]?.jsonPrimitive?.int ?: 0
                if (sn > 0) {
                    val epCount = s["episode_count"]?.jsonPrimitive?.int ?: 0
                    val eps = (1..epCount).map { epNum -> Episode(epNum.toLong(), epNum, "Episodio $epNum") }
                    list.add(Season(sn, s["name"]?.jsonPrimitive?.content ?: "Stagione $sn", eps))
                }
            }
        } catch (e: Exception) {}
        return list
    }





}
