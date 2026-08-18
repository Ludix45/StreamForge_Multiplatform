package com.example.data.network

import android.net.Uri
import android.util.Log
import com.example.data.model.Episode
import com.example.data.model.MediaItem
import com.example.data.model.Season
import com.example.data.model.VideoParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.net.URLEncoder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object Scraper {
    private const val TAG = "Scraper"

    // Helper to score and rank items by match relevance for search quality (Feature 3)
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
            // Count number of words matching
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
       STREAMINGCOMMUNITY SCRAPER
       ========================================================================================== */

    private fun getScInertiaVersion(baseUrl: String, lang: String): String {
        val html = HttpClient.get("$baseUrl$lang")
        val doc = Jsoup.parse(html)
        val appDiv = doc.select("div#app").first()
            ?: throw Exception("StreamingCommunity app div#app wrapper not found")
        val dataPage = appDiv.attr("data-page")
        val obj = JSONObject(dataPage)
        return obj.getString("version")
    }

    suspend fun searchStreamingCommunity(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val baseUrl = DomainManager.getUrl("streamingcommunity")
            val languages = listOf("it", "en")

            for (lang in languages) {
                try {
                    val version = getScInertiaVersion(baseUrl, lang)
                    val encodedQuery = URLEncoder.encode(query, "UTF-8")
                    val searchUrl = "$baseUrl$lang/search?q=$encodedQuery"

                    val json = HttpClient.get(
                        searchUrl,
                        mapOf(
                            "X-Inertia" to "true",
                            "X-Inertia-Version" to version,
                            "Referer" to "$baseUrl$lang/"
                        )
                    )

                    val obj = JSONObject(json)
                    val props = obj.optJSONObject("props") ?: continue
                    val titles = props.optJSONArray("titles") ?: continue

                    for (i in 0 until titles.length()) {
                        val titleObj = titles.getJSONObject(i)
                        try {
                            val item = parseScTitle(titleObj, lang, baseUrl)
                            list.add(item)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing SC search title", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error searching SC in language: $lang", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical StreamingCommunity search failure", e)
        }

        // Deduplicate by media ID (prefer Italian results)
        val deduplicated = list.groupBy { it.id }.map { (_, items) ->
            items.firstOrNull { it.providerLanguage == "it" } ?: items.first()
        }

        val ranked = scoreAndRank(deduplicated, query)
        enrichList(ranked.take(20))
    }

    private fun parseScTitle(obj: JSONObject, lang: String, baseUrl: String): MediaItem {
        val id = obj.getLong("id")
        val name = obj.getString("name")
        val type = obj.optString("type", "film")
        val slug = obj.optString("slug", "")

        // Poster
        val images = obj.optJSONArray("images")
        var filename: String? = null
        val preferredTypes = listOf("poster", "cover", "cover_mobile", "background")
        outer@ for (ptype in preferredTypes) {
            for (i in 0 until (images?.length() ?: 0)) {
                val img = images!!.getJSONObject(i)
                if (img.optString("type") == ptype && img.optString("filename").isNotBlank()) {
                    filename = img.optString("filename")
                    break@outer
                }
            }
        }
        val cdnUrl = baseUrl.replace("stream", "cdn.stream")
            .replace("associates", "associates") // Ensure it doesn't break sub domains
        val posterUrl = filename?.let { "${cdnUrl}images/$it" }

        // Year
        var year: String? = null
        val translations = obj.optJSONArray("translations")
        for (i in 0 until (translations?.length() ?: 0)) {
            val t = translations!!.getJSONObject(i)
            val key = t.optString("key")
            if (key == "first_air_date" || key == "release_date") {
                val v = t.optString("value")
                if (v.isNotBlank()) {
                    year = v.substringBefore("-")
                    break
                }
            }
        }
        if (year == null) {
            val lastAir = obj.optString("last_air_date")
            year = if (lastAir.isNotBlank()) lastAir.substringBefore("-") else null
        }
        if (year.isNullOrBlank()) {
            val scReleasedAt = obj.optString("sc_released_at")
            year = if (scReleasedAt.isNotBlank()) scReleasedAt.substringBefore("-") else null
        }

        return MediaItem(id, name, type, slug, posterUrl, year, lang)
    }

    suspend fun getStreamingCommunitySeasons(item: MediaItem): List<Season> = withContext(Dispatchers.IO) {
        val seasons = mutableListOf<Season>()
        try {
            val baseUrl = DomainManager.getUrl("streamingcommunity")
            val url = "$baseUrl${item.providerLanguage}/titles/${item.id}-${item.slug}"
            val html = HttpClient.get(url)
            val doc = Jsoup.parse(html)
            val appDiv = doc.select("div#app").first()
                ?: throw Exception("StreamingCommunity app div#app wrapper not found on title details")
            val dataPage = appDiv.attr("data-page")
            val obj = JSONObject(dataPage)
            val props = obj.optJSONObject("props") ?: return@withContext emptyList()
            val titleObj = props.optJSONObject("title") ?: return@withContext emptyList()
            val seasonsArr = titleObj.optJSONArray("seasons") ?: return@withContext emptyList()

            for (i in 0 until seasonsArr.length()) {
                val sObj = seasonsArr.getJSONObject(i)
                val num = sObj.getInt("number")
                seasons.add(Season(num, "Stagione $num", emptyList()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching SC seasons", e)
        }
        seasons.sortedBy { it.number }
    }

    suspend fun getStreamingCommunityEpisodes(item: MediaItem, seasonNumber: Int): List<Episode> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Episode>()
        try {
            val baseUrl = DomainManager.getUrl("streamingcommunity")
            val lang = item.providerLanguage
            val version = getScInertiaVersion(baseUrl, lang)
            val url = "$baseUrl$lang/titles/${item.id}-${item.slug}/season-$seasonNumber"

            val json = HttpClient.get(
                url,
                mapOf(
                    "X-Inertia" to "true",
                    "X-Inertia-Version" to version,
                    "Referer" to "$baseUrl$lang/"
                )
            )

            val obj = JSONObject(json)
            val props = obj.optJSONObject("props") ?: return@withContext emptyList()
            val loadedSeason = props.optJSONObject("loadedSeason") ?: return@withContext emptyList()
            val episodesArr = loadedSeason.optJSONArray("episodes") ?: return@withContext emptyList()

            for (i in 0 until episodesArr.length()) {
                val epObj = episodesArr.getJSONObject(i)
                list.add(
                    Episode(
                        id = epObj.getLong("id"),
                        number = epObj.getInt("number"),
                        name = epObj.optString("name", "Episodio ${epObj.getInt("number")}")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching SC episodes for season $seasonNumber", e)
        }
        list.sortedBy { it.number }
    }

    suspend fun extractStreamingCommunityUrl(item: MediaItem, episodeId: Long?): String = withContext(Dispatchers.IO) {
        val baseUrl = DomainManager.getUrl("streamingcommunity")
        var parentUrl = baseUrl
        val iframeSrc = if (episodeId == null || item.isMovie) {
            val url = "$baseUrl${item.providerLanguage}/iframe/${item.id}"
            parentUrl = url
            val html = HttpClient.get(url, mapOf("Referer" to baseUrl))
            val doc = Jsoup.parse(html)
            doc.select("iframe").first()?.attr("src")
                ?: throw Exception("SC movie play iframe not found")
        } else {
            val url = "$baseUrl${item.providerLanguage}/iframe/${item.id}?episode_id=$episodeId&next_episode=1"
            parentUrl = url
            val html = HttpClient.get(url, mapOf("Referer" to baseUrl))
            val doc = Jsoup.parse(html)
            doc.select("iframe").first()?.attr("src")
                ?: throw Exception("SC episode play iframe not found")
        }

        Log.d(TAG, "SC Iframe SRC: $iframeSrc")

        // Load the iframe embed body
        val iframeHeaders = mapOf(
            "Referer" to parentUrl,
            "User-Agent" to HttpClient.USER_AGENT
        )
        val embedHtml = HttpClient.get(iframeSrc, iframeHeaders)
        val embedDoc = Jsoup.parse(embedHtml)
        val scriptText = embedDoc.select("body script").first()?.html()
            ?: throw Exception("Video parsing script not found inside vixcloud media frame")

        val params = parseScript(scriptText)
            ?: throw Exception("Could not find video params (token/expires/url) inside the player scripts")

        val finalM3u8Url = buildM3u8Url(params)
        Log.d(TAG, "Generated SC stream URL: $finalM3u8Url")
        finalM3u8Url
    }

    private fun parseScript(script: String): VideoParams? {
        val tokenRegex = Regex("""token["']?\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val expiresRegex = Regex("""expires["']?\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val urlRegex = Regex("""url["']?\s*:\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
        val fhdRegex = Regex("""canPlayFHD\s*=\s*(true|false)""", RegexOption.IGNORE_CASE)

        val token = tokenRegex.find(script)?.groupValues?.get(1) ?: return null
        val expires = expiresRegex.find(script)?.groupValues?.get(1) ?: return null
        val url = urlRegex.find(script)?.groupValues?.get(1) ?: return null
        val canPlayFHD = fhdRegex.find(script)?.groupValues?.get(1) == "true"

        return VideoParams(token, expires, url, canPlayFHD)
    }

    private fun buildM3u8Url(params: VideoParams): String {
        val parsedUrl = Uri.parse(params.url)
        val queryBuilder = Uri.Builder()
            .scheme(parsedUrl.scheme)
            .authority(parsedUrl.authority)
            .path(parsedUrl.path)

        if (params.canPlayFHD) {
            queryBuilder.appendQueryParameter("h", "1")
        }
        if (parsedUrl.getQueryParameter("b") == "1") {
            queryBuilder.appendQueryParameter("b", "1")
        }
        queryBuilder
            .appendQueryParameter("token", params.token)
            .appendQueryParameter("expires", params.expires)

        return queryBuilder.build().toString()
    }


    /* ==========================================================================================
       ANIMEUNITY SCRAPER
       ========================================================================================== */

    suspend fun searchAnimeUnity(query: String): List<MediaItem>
 = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val baseUrl = DomainManager.getUrl("animeunity")
            // Step 1 - Get Session cookie and XSRF-TOKEN
            val (homePageHtml, cookies) = HttpClient.getWithCookies(baseUrl)
            val sessionCookie = cookies["animeunity_session"] ?: ""
            val rawXsrfToken = cookies["XSRF-TOKEN"] ?: ""
            val xsrfToken = URLDecoder.decode(rawXsrfToken, "UTF-8")

            val cookieHeaderVal = "XSRF-TOKEN=$rawXsrfToken; animeunity_session=$sessionCookie"

            // Step 2a: Livesearch
            try {
                val formBody = FormBody.Builder().add("title", query).build()
                val livesearchHeaders = mapOf(
                    "Origin" to baseUrl.removeSuffix("/"),
                    "Referer" to baseUrl,
                    "X-XSRF-TOKEN" to xsrfToken,
                    "Cookie" to cookieHeaderVal
                )
                val liveResponse = HttpClient.post("$baseUrl/livesearch", formBody, livesearchHeaders)
                val obj = JSONObject(liveResponse)
                val records = obj.optJSONArray("records")
                if (records != null) {
                    for (i in 0 until records.length()) {
                        try {
                            val r = records.getJSONObject(i)
                            list.add(parseAuTitle(r))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing AU livesearch record", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "AU Livesearch failed", e)
            }

            // Step 2b: Archivio Search (for more complete results)
            try {
                val jsonPayload = """{"title":"$query","type":false,"year":false,"order":false,"status":false,"genres":false,"offset":0,"dubbed":false,"season":false}"""
                val archiveRequestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
                val archiveHeaders = mapOf(
                    "Origin" to baseUrl.removeSuffix("/"),
                    "Referer" to baseUrl,
                    "X-XSRF-TOKEN" to xsrfToken,
                    "Cookie" to cookieHeaderVal,
                    "Content-Type" to "application/json"
                )
                val archiveResponse = HttpClient.post("$baseUrl/archivio/get-animes", archiveRequestBody, archiveHeaders)
                val obj = JSONObject(archiveResponse)
                val records = obj.optJSONArray("records")
                if (records != null) {
                    for (i in 0 until records.length()) {
                        try {
                            val r = records.getJSONObject(i)
                            list.add(parseAuTitle(r))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing AU archive record", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "AU Archive search failed", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical AnimeUnity search failure", e)
        }

        // Deduplicate AU matches by id, then score and rank
        val deduplicated = list.distinctBy { it.id }
        val ranked = scoreAndRank(deduplicated, query)
        enrichList(ranked.take(20))
    }

    private fun parseAuTitle(obj: JSONObject): MediaItem {
        val id = obj.getLong("id")
        val nameEng = obj.optString("title_eng", "").trim()
        val nameDefault = obj.optString("title", "").trim()
        val nameIt = obj.optString("title_it", "").trim()

        val name = when {
            nameEng.isNotBlank() -> nameEng
            nameDefault.isNotBlank() -> nameDefault
            nameIt.isNotBlank() -> nameIt
            else -> "Anime ID $id"
        }

        val type = obj.optString("type", "tv")
        val slug = obj.optString("slug", "")
        val posterUrl = obj.optString("imageurl").trim().ifBlank { null }

        return MediaItem(id, name, type, slug, posterUrl, year = null)
    }

    suspend fun getAnimeUnityEpisodes(mediaId: Long): List<Episode> = withContext(Dispatchers.IO) {
        val allEpisodes = mutableListOf<Episode>()
        try {
            val baseUrl = DomainManager.getUrl("animeunity")
            // Step 1: get total episodes count
            val infoResponse = HttpClient.get("$baseUrl/info_api/$mediaId/")
            val infoObj = JSONObject(infoResponse)
            val episodesCount = infoObj.optInt("episodes_count", 0)

            // Step 2: load in chunks of 120
            var startRange = 1
            while (startRange <= episodesCount) {
                val endRange = startRange + 119
                try {
                    val chunkUrl = "$baseUrl/info_api/$mediaId/1?start_range=$startRange&end_range=$endRange"
                    val chunkResponse = HttpClient.get(chunkUrl)
                    val chunkObj = JSONObject(chunkResponse)
                    val episodesArr = chunkObj.optJSONArray("episodes")
                    if (episodesArr != null) {
                        for (i in 0 until episodesArr.length()) {
                            val epObj = episodesArr.getJSONObject(i)
                            allEpisodes.add(
                                Episode(
                                    id = epObj.getLong("id"),
                                    number = epObj.optInt("number", 0),
                                    name = "Episodio ${epObj.optInt("number")}"
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing AU episode chunk start_range=$startRange", e)
                }
                startRange += 120
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching AnimeUnity episodes list", e)
        }
        allEpisodes.sortedBy { it.number }
    }

    suspend fun extractAnimeUnityUrl(episodeId: Long): String = withContext(Dispatchers.IO) {
        val baseUrl = DomainManager.getUrl("animeunity")
        // Step 1: get embed URL
        val embedUrl = HttpClient.get("$baseUrl/embed-url/$episodeId").trim()
        Log.d(TAG, "AU Embed URL: $embedUrl")

        // Step 2: load embed page
        val embedHtml = HttpClient.get(embedUrl)
        val doc = Jsoup.parse(embedHtml)

        // Step 3: try vixcloud-style regex on script 1
        val scripts = doc.select("body script")
        if (scripts.isNotEmpty()) {
            val script1 = scripts.first()?.html() ?: ""
            val params = parseScript(script1)
            if (params != null) {
                val m3u8Url = buildM3u8Url(params)
                Log.d(TAG, "Found m3u8 URL in AU first script: $m3u8Url")
                return@withContext m3u8Url
            }
        }

        // Alternative: grab direct mp4 URL from second script
        if (scripts.size >= 2) {
            val script2 = scripts[1].html()
            val videoUrlLine = script2.lines().firstOrNull { it.contains("videoUrl") || it.contains("var video") }
                ?: script2
            val mp4Url = Regex("""(?:videoUrl|video\s*=\s*|url)\s*=\s*['"](https?://[^'"]+)['"]""")
                .find(videoUrlLine)?.groupValues?.get(1)
                ?: script2.split(" = ").getOrNull(1)?.trim()?.removeSurrounding("'", "'")?.removeSurrounding("\"", "\"")?.substringBefore(";")

            if (!mp4Url.isNullOrBlank()) {
                Log.d(TAG, "Found direct mp4 URL in AU second script: $mp4Url")
                return@withContext mp4Url
            }
        }

        throw Exception("Could not find play link inside AnimeUnity embed frame")
    }

    // ==========================================
    // ANIMEWORLD PROVIDER IMPLEMENTATION
    // ==========================================

    private suspend fun getAnimeWorldAuth(baseUrl: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val (html, cookies) = HttpClient.getWithCookies(baseUrl)
        val sessionId = cookies["sessionId"] ?: ""
        val doc = Jsoup.parse(html)
        var csrfToken = doc.select("meta[name=csrf-token]").first()?.attr("content") ?: ""
        if (csrfToken.isBlank()) {
            csrfToken = doc.select("input[name=_csrf]").first()?.attr("value") ?: ""
        }
        Pair(sessionId, csrfToken)
    }

    suspend fun searchAnimeWorld(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val baseUrl = DomainManager.getUrl("animeworld").removeSuffix("/")
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "$baseUrl/search?keyword=$encodedQuery"
            val html = HttpClient.get(searchUrl)
            val doc = Jsoup.parse(html)
            
            doc.select("a.poster").forEach { element ->
                try {
                    val img = element.selectFirst("img")
                    val name = img?.attr("alt") ?: ""
                    val href = element.attr("href") ?: ""
                    if (name.isBlank() || href.isBlank()) return@forEach
                    
                    val posterUrl = img?.attr("src")
                    val statusDiv = element.selectFirst("div.status")
                    val hasMovieClass = statusDiv?.selectFirst("div.movie") != null
                    val type = if (hasMovieClass) "film" else "tv"
                    
                    var slug = href
                    if (slug.startsWith("/")) {
                        slug = slug.substring(1)
                    }
                    
                    val id = slug.hashCode().toLong()
                    
                    list.add(
                        MediaItem(
                            id = id,
                            name = name,
                            type = type,
                            slug = slug,
                            posterUrl = posterUrl,
                            year = null,
                            providerLanguage = "it"
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing animeworld search element", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical searchAnimeWorld failure", e)
        }
        val ranked = scoreAndRank(list, query)
        enrichList(ranked.take(20))
    }

    suspend fun getAnimeWorldEpisodes(item: MediaItem): List<Episode> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Episode>()
        try {
            val baseUrl = DomainManager.getUrl("animeworld").removeSuffix("/")
            val url = if (item.slug.startsWith("http")) item.slug else "$baseUrl/${item.slug}"
            val html = HttpClient.get(url)
            val doc = Jsoup.parse(html)
            
            doc.select("li.episode > a").forEach { aTag ->
                val epNumStr = aTag.attr("data-episode-num")
                val epNum = epNumStr.toIntOrNull() ?: 1
                val epIdStr = aTag.attr("data-episode-id")
                val epId = epIdStr.toLongOrNull() ?: epNum.toLong()
                val playHref = aTag.attr("href") ?: ""
                val epToken = aTag.attr("data-id") ?: ""
                
                list.add(
                    Episode(
                        id = epId,
                        number = epNum,
                        name = "Episodio $epNum",
                        playUrl = playHref,
                        token = epToken
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching AnimeWorld episodes", e)
        }
        list.sortedBy { it.number }
    }

    suspend fun extractAnimeWorldUrl(item: MediaItem, episodeId: Long?): String = withContext(Dispatchers.IO) {
        val baseUrl = DomainManager.getUrl("animeworld").removeSuffix("/")
        
        // Find the episode by loading the page if we don't have playUrl/token filled
        // Let's load the episodes list first to match episodeId
        val episodes = getAnimeWorldEpisodes(item)
        val episode = (if (episodeId == null) episodes.firstOrNull() else episodes.find { it.id == episodeId })
            ?: throw Exception("Specificato un episodio dell'AnimeWorld non trovato.")
            
        val (sessionId, csrfToken) = getAnimeWorldAuth("$baseUrl/")
        
        val apiInfoUrl = "$baseUrl/api/episode/info?id=${episode.token}&alt=0"
        
        val playHref = episode.playUrl ?: ""
        val referer = if (playHref.startsWith("http")) playHref else "$baseUrl/$playHref"
        
        val headers = mapOf(
            "Cookie" to "sessionId=$sessionId",
            "csrf-token" to csrfToken,
            "X-Requested-With" to "XMLHttpRequest",
            "Accept" to "application/json, text/javascript, */*; q=0.01",
            "Referer" to referer,
            "Origin" to baseUrl
        )
        
        val responseJson = HttpClient.get(apiInfoUrl, headers)
        val jsonObj = JSONObject(responseJson)
        
        if (jsonObj.has("error")) {
            throw Exception("AnimeWorld API error: ${jsonObj.optString("error")}")
        }
        
        val grabberUrl = jsonObj.optString("grabber")
        if (grabberUrl.isNullOrBlank()) {
            throw Exception("Impossibile trovare il link m3u8 nella risposta dell'AnimeWorld: $responseJson")
        }
        
        grabberUrl
    }

    // ==========================================
    // EUROSTREAMING PROVIDER IMPLEMENTATION
    // ==========================================

    suspend fun searchEuroStreaming(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val baseUrl = DomainManager.getUrl("eurostreaming").removeSuffix("/")
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$baseUrl/wp-json/wp/v2/search?search=$encodedQuery&_fields=id&per_page=15"
            val response = HttpClient.get(url)
            val searchResults = JSONArray(response)
            
            for (i in 0 until searchResults.length()) {
                val itemObj = searchResults.getJSONObject(i)
                val postId = itemObj.optLong("id")
                if (postId == 0L) continue
                
                try {
                    val postUrl = "$baseUrl/wp-json/wp/v2/posts/$postId?_fields=content,title"
                    val postResponse = HttpClient.get(postUrl)
                    val postObj = JSONObject(postResponse)
                    val titleObj = postObj.optJSONObject("title")
                    val title = titleObj?.optString("rendered") ?: ""
                    val contentObj = postObj.optJSONObject("content")
                    val htmlContent = contentObj?.optString("rendered") ?: ""
                    
                    if (title.isBlank()) continue
                    
                    val yearRegex = Regex("""(?<![/\d])(19|20)\d{2}(?![/\d])""")
                    val year = yearRegex.find(htmlContent)?.value
                    
                    val doc = Jsoup.parse(htmlContent)
                    val posterUrl = doc.select("img").first()?.attr("src")
                    
                    list.add(
                        MediaItem(
                            id = postId,
                            name = Jsoup.parse(title).text(),
                            type = "tv",
                            slug = postId.toString(),
                            posterUrl = posterUrl,
                            year = year,
                            providerLanguage = "it"
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching post $postId", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical searchEuroStreaming failure", e)
        }
        val ranked = scoreAndRank(list, query)
        enrichList(ranked.take(20))
    }

    suspend fun getEuroStreamingSeasons(item: MediaItem): List<Season> = withContext(Dispatchers.IO) {
        val seasonsMap = mutableMapOf<Int, MutableList<Episode>>()
        try {
            val baseUrl = DomainManager.getUrl("eurostreaming").removeSuffix("/")
            val postUrl = "$baseUrl/wp-json/wp/v2/posts/${item.id}?_fields=content"
            val response = HttpClient.get(postUrl)
            val postObj = JSONObject(response)
            val contentObj = postObj.optJSONObject("content")
            val htmlContent = contentObj?.optString("rendered") ?: ""
            
            val regex = Regex("""(\d+)&#215;(\d{2})\b""")
            val matches = regex.findAll(htmlContent)
            
            matches.forEach { match ->
                val seasonNum = match.groupValues[1].toInt()
                val epNum = match.groupValues[2].toInt()
                
                val epTag = "${seasonNum}&#215;${String.format("%02d", epNum)}"
                val titleRegex = Regex("""${epTag}\s*[-–]\s*([^<\n]+)""")
                val titleMatch = titleRegex.find(htmlContent)
                val cleanTitle = titleMatch?.groupValues?.get(1)?.trim() ?: "Episodio $epNum"
                
                val epList = seasonsMap.getOrPut(seasonNum) { mutableListOf() }
                if (epList.none { it.number == epNum }) {
                    epList.add(
                        Episode(
                            id = (seasonNum * 1000 + epNum).toLong(),
                            number = epNum,
                            name = Jsoup.parse(cleanTitle).text()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching EuroStreaming seasons", e)
        }
        
        seasonsMap.map { (seasonNum, eps) ->
            Season(seasonNum, "Stagione $seasonNum", eps.sortedBy { it.number })
        }.sortedBy { it.number }
    }

    suspend fun extractEuroStreamingUrl(item: MediaItem, seasonNum: Int, epNum: Int): String = withContext(Dispatchers.IO) {
        val baseUrl = DomainManager.getUrl("eurostreaming").removeSuffix("/")
        
        val postUrl = "$baseUrl/wp-json/wp/v2/posts/${item.id}?_fields=content"
        val response = HttpClient.get(postUrl)
        val postObj = JSONObject(response)
        val contentObj = postObj.optJSONObject("content")
        val htmlContent = contentObj?.optString("rendered") ?: ""
        
        val epTag = "$seasonNum&#215;${String.format("%02d", epNum)}"
        val brRegex = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
        val lines = htmlContent.split(brRegex)
        
        var loadmUrl: String? = null
        var maxstreamUrl: String? = null
        
        for (line in lines) {
            if (!line.contains(epTag)) continue
            
            if (loadmUrl == null) {
                val m = Regex("""href="(https?://loadm[^"]+)"""", RegexOption.IGNORE_CASE).find(line)
                if (m != null) {
                    loadmUrl = m.groupValues[1]
                }
            }
            if (maxstreamUrl == null) {
                val m = Regex("""href="(https://uprot\.net/msf/[^"]+)"""", RegexOption.IGNORE_CASE).find(line)
                if (m != null) {
                    maxstreamUrl = m.groupValues[1]
                }
            }
        }
        
        val referer = "$baseUrl/?p=${item.id}"
        
        if (loadmUrl != null) {
            Log.d(TAG, "Found loadm link: $loadmUrl")
            val finalUrl = extractLoadmUrl(loadmUrl, referer)
            if (finalUrl != null) return@withContext finalUrl
        }
        
        if (maxstreamUrl != null) {
            Log.d(TAG, "Found maxstream link: $maxstreamUrl")
            val finalUrl = extractMaxStreamUrl(maxstreamUrl, referer)
            if (finalUrl != null) return@withContext finalUrl
        }
        
        throw Exception("Non è stato possibile trovare alcun link supportato (Loadm o MaxStream) per questo episodio.")
    }

    private suspend fun extractLoadmUrl(playerLink: String, referer: String): String? {
        return try {
            val parts = playerLink.split("#")
            val playerUrl = parts[0].removeSuffix("/") + "/"
            val videoId = if (parts.size > 1) parts[1] else ""
            
            val apiUrl = "${playerUrl}api/v1/video?id=$videoId&w=2560&h=1440&r=${URLEncoder.encode(referer, "UTF-8")}"
            val headers = mapOf(
                "Referer" to playerUrl,
                "User-Agent" to HttpClient.USER_AGENT
            )
            val responseText = HttpClient.get(apiUrl, headers)
            
            val decrypted = decryptLoadm(responseText)
            val data = JSONObject(decrypted)
            
            val url = data.optString("cf").ifBlank { data.optString("source") }
            if (url.isNotBlank()) url else null
        } catch (e: Exception) {
            Log.e(TAG, "Loadm extraction failed", e)
            null
        }
    }

    private fun decryptLoadm(hexText: String): String {
        val cleanHex = hexText.replace(Regex("[^0-9a-fA-F]"), "")
        val finalHex = if (cleanHex.length % 2 != 0) "0$cleanHex" else cleanHex
        
        val ciphertext = ByteArray(finalHex.length / 2)
        for (i in ciphertext.indices) {
            val index = i * 2
            ciphertext[i] = finalHex.substring(index, index + 2).toInt(16).toByte()
        }
        
        val keySpec = SecretKeySpec("kiemtienmua911ca".toByteArray(Charsets.UTF_8), "AES")
        val ivSpec = IvParameterSpec("1234567890oiuytr".toByteArray(Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        val decryptedBytes = cipher.doFinal(ciphertext)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    private suspend fun extractMaxStreamUrl(uprotLink: String, referer: String): String? {
        return try {
            val mseLink = uprotLink.replace("/msf/", "/mse/")
            val headers = mapOf(
                "User-Agent" to HttpClient.USER_AGENT,
                "Referer" to referer,
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            
            val html1 = HttpClient.get(mseLink, headers)
            val doc1 = Jsoup.parse(html1)
            
            var continueUrl: String? = null
            for (a in doc1.select("a")) {
                if (a.text().lowercase().contains("continue")) {
                    continueUrl = a.attr("href")
                    break
                }
            }
            
            if (continueUrl.isNullOrBlank()) {
                Log.e(TAG, "MaxStream: No 'continue' link in page.")
                return null
            }
            
            val headers2 = headers.toMutableMap().apply {
                put("Referer", mseLink)
            }
            val html2 = HttpClient.get(continueUrl, headers2)
            
            val m3u8Regex = Regex("""https?://[^\s"\'<>]+\.m3u8[^\s"\'<>]*""")
            val match = m3u8Regex.find(html2)
            match?.value
        } catch (e: Exception) {
            Log.e(TAG, "MaxStream extraction failed", e)
            null
        }
    }

    private suspend fun enrichList(items: List<MediaItem>): List<MediaItem> = withContext(Dispatchers.IO) {
        items.map { item ->
            async { enrichWithTMDB(item) }
        }.awaitAll()
    }

    suspend fun enrichWithTMDB(item: MediaItem): MediaItem = withContext(Dispatchers.IO) {
        // Se ha già un poster TMDB o è Cinezo (che usa già TMDB), non facciamo nulla
        if (!item.posterUrl.isNullOrBlank() && item.posterUrl!!.contains("tmdb.org")) return@withContext item
        
        val type = if (item.isMovie) "movie" else "tv"
        // Pulizia nome: rimuove (2024), (SUB), [ITA] etc per migliorare il matching su TMDB
        val cleanName = item.name
            .replace(Regex("""\(\d{4}\)"""), "")
            .replace(Regex("""\[.*?\]"""), "")
            .replace(Regex("""\s+-\s+.*$"""), "")
            .trim()
            
        val encodedQuery = URLEncoder.encode(cleanName, "UTF-8")
        
        try {
            val url = "https://api.themoviedb.org/3/search/$type?api_key=$tmdbApiKey&query=$encodedQuery&language=it"
            val json = HttpClient.get(url)
            val results = JSONObject(json).optJSONArray("results")
            if (results != null && results.length() > 0) {
                val match = results.getJSONObject(0)
                val path = match.optString("poster_path", "")
                if (path.isNotEmpty() && path != "null") {
                    return@withContext item.copy(posterUrl = "https://image.tmdb.org/t/p/w500$path")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "TMDB Enrichment failed for ${item.name}", e)
        }
        item
    }

    private const val DEFAULT_TMDB_API_KEY = "b74737aa76951bca42b32388047055c6"

    suspend fun searchCinezo(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val apiKey = tmdbApiKey

        // Search Movies
        try {
            val movieUrl = "https://api.themoviedb.org/3/search/movie?api_key=$apiKey&query=$encodedQuery&language=it"
            val mJson = HttpClient.get(movieUrl)
            val mObj = JSONObject(mJson)
            val results = mObj.optJSONArray("results")
            if (results != null) {
                val limit = minOf(results.length(), 10)
                for (i in 0 until limit) {
                    val m = results.getJSONObject(i)
                    val posterPath = m.optString("poster_path", "")
                    val posterUrl = if (posterPath.isNotEmpty() && posterPath != "null") "https://image.tmdb.org/t/p/w500$posterPath" else null
                    val releaseDate = m.optString("release_date", "")
                    val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else null

                    list.add(
                        MediaItem(
                            id = m.getLong("id"),
                            name = m.optString("title", m.optString("original_title", "")),
                            type = "film",
                            slug = "movie",
                            posterUrl = posterUrl,
                            year = year,
                            providerLanguage = "it"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cinezo movie TMDB search failed", e)
        }

        // Search TV series
        try {
            val tvUrl = "https://api.themoviedb.org/3/search/tv?api_key=$apiKey&query=$encodedQuery&language=it"
            val tvJson = HttpClient.get(tvUrl)
            val tvObj = JSONObject(tvJson)
            val results = tvObj.optJSONArray("results")
            if (results != null) {
                val limit = minOf(results.length(), 10)
                for (i in 0 until limit) {
                    val s = results.getJSONObject(i)
                    val posterPath = s.optString("poster_path", "")
                    val posterUrl = if (posterPath.isNotEmpty() && posterPath != "null") "https://image.tmdb.org/t/p/w500$posterPath" else null
                    val firstAirDate = s.optString("first_air_date", "")
                    val year = if (firstAirDate.length >= 4) firstAirDate.substring(0, 4) else null

                    list.add(
                        MediaItem(
                            id = s.getLong("id"),
                            name = s.optString("name", s.optString("original_name", "")),
                            type = "tv",
                            slug = "tv",
                            posterUrl = posterUrl,
                            year = year,
                            providerLanguage = "it"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cinezo TV TMDB search failed", e)
        }

        scoreAndRank(list, query)
    }

    suspend fun getCinezoSeasons(item: MediaItem): List<Season> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Season>()
        val tmdbId = item.id
        val apiKey = DEFAULT_TMDB_API_KEY
        val detailUrl = "https://api.themoviedb.org/3/tv/$tmdbId?api_key=$apiKey&language=it"

        try {
            val jsonStr = HttpClient.get(detailUrl)
            val obj = JSONObject(jsonStr)
            val seasonsArr = obj.optJSONArray("seasons")
            if (seasonsArr != null) {
                for (i in 0 until seasonsArr.length()) {
                    val rawSeason = seasonsArr.getJSONObject(i)
                    val sn = rawSeason.optInt("season_number", 0)
                    if (sn == 0) continue // Skip specials

                    val name = rawSeason.optString("name", "Stagione $sn")
                    val epCount = rawSeason.optInt("episode_count", 0)

                    val eps = mutableListOf<Episode>()
                    for (epNum in 1..epCount) {
                        eps.add(
                            Episode(
                                id = epNum.toLong(),
                                number = epNum,
                                name = "Episodio $epNum"
                            )
                        )
                    }

                    list.add(Season(sn, name, eps))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading Cinezo seasons via TMDB id=$tmdbId", e)
        }

        list
    }

    private fun unwrapProxyUrl(url: String): String {
        if (url.contains("?url=") || url.contains("&url=")) {
            try {
                val uri = Uri.parse(url)
                val realUrl = uri.getQueryParameter("url")
                if (!realUrl.isNullOrBlank()) {
                    return realUrl
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
        return url
    }

    private fun parseDecodedPayload(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                val obj = JSONObject(trimmed)
                if (obj.has("streams")) {
                    val streams = obj.getJSONArray("streams")
                    if (streams.length() > 0) {
                        val s = streams.getJSONObject(0)
                        return s.optString("url", s.optString("stream", ""))
                    }
                }
                if (obj.has("sources")) {
                    val sources = obj.getJSONArray("sources")
                    if (sources.length() > 0) {
                        val s = sources.getJSONObject(0)
                        return s.optString("url", s.optString("file", s.optString("stream", "")))
                    }
                }
                return obj.optString("url", obj.optString("stream", ""))
            } catch (e: Exception) {
                // Fallback to text
            }
        }
        return trimmed.removeSurrounding("\"")
    }

    suspend fun extractCinezoUrl(item: MediaItem, season: Int?, episode: Int?): String = withContext(Dispatchers.IO) {
        val tmdbId = item.id
        val isMovie = item.isMovie
        val sNum = season ?: 1
        val epNum = episode ?: 1

        val servers = listOf(
            "Icefy"        to ("https://api.tulnex.com/icefy/movie/{id}" to "https://api.tulnex.com/icefy/tv/{id}/{season}/{episode}"),
            "MovieBox"     to ("https://api.tulnex.com/moviebox/movie/{id}" to "https://api.tulnex.com/moviebox/tv/{id}/{season}/{episode}"),
            "Onion"        to ("https://api.tulnex.com/onion/movie/{id}" to "https://api.tulnex.com/onion/tv/{id}/{season}/{episode}"),
            "Alpha"        to ("https://api.tulnex.com/111movies/Alpha/movie/{id}" to "https://api.tulnex.com/111movies/Alpha/tv/{id}/{season}/{episode}"),
            "Bravo"        to ("https://api.tulnex.com/111movies/Bravo/movie/{id}" to "https://api.tulnex.com/111movies/Bravo/tv/{id}/{season}/{episode}"),
            "NgFlix"       to ("https://api.tulnex.com/111movies/NgFlix/movie/{id}" to "https://api.tulnex.com/111movies/NgFlix/tv/{id}/{season}/{episode}")
        )

        val apiHeaders = mapOf(
            "user-agent" to HttpClient.USER_AGENT,
            "referer" to "https://player.cinezo.live/embed/"
        )

        for (pair in servers) {
            val name = pair.first
            val movieUrlPattern = pair.second.first
            val tvUrlPattern = pair.second.second

            val url = if (isMovie) {
                movieUrlPattern.replace("{id}", tmdbId.toString())
            } else {
                tvUrlPattern.replace("{id}", tmdbId.toString())
                    .replace("{season}", sNum.toString())
                    .replace("{episode}", epNum.toString())
            }

            try {
                Log.d(TAG, "[Cinezo] Trying server '$name' for URL: $url")
                val jsonStr = HttpClient.get(url, apiHeaders)
                if (jsonStr.isBlank()) continue

                val data = JSONObject(jsonStr)
                if (data.optBoolean("success", true) == false || data.has("error")) {
                    continue
                }

                // Format A: direct sources
                if (data.has("sources") && !data.has("payload")) {
                    val sources = data.getJSONArray("sources")
                    if (sources.length() > 0) {
                        val first = sources.getJSONObject(0)
                        val streamUrl = first.optString("url", first.optString("file", first.optString("stream", "")))
                        if (streamUrl.startsWith("http")) {
                            return@withContext unwrapProxyUrl(streamUrl)
                        }
                    }
                }

                // Format C: VidLink-style nested data
                if (data.has("data") && !data.has("payload")) {
                    val inner = data.getJSONObject("data")
                    val streamInfo = if (inner.has("data")) inner.getJSONObject("data") else inner
                    if (streamInfo.has("stream")) {
                        val sObj = streamInfo.getJSONObject("stream")
                        val playlist = sObj.optString("playlist", sObj.optString("url", ""))
                        if (playlist.startsWith("http")) {
                            return@withContext unwrapProxyUrl(playlist)
                        }
                    }
                }

                // Format B: encrypted payload
                if (data.has("payload")) {
                    val payload = data.getString("payload")
                    val decrypted = CinezoDecryptor.decodePayload(payload)
                    val parsedUrl = parseDecodedPayload(decrypted)
                    if (parsedUrl.startsWith("http")) {
                        return@withContext unwrapProxyUrl(parsedUrl)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[Cinezo] Server '$name' query failed: ${e.message}")
            }
        }

        throw Exception("Non è stato possibile estrarre un link funzionante per Cinezo da nessuno dei server disponibili.")
    }

    private suspend fun getImdbId(tmdbId: Long, isMovie: Boolean): String? {
        val type = if (isMovie) "movie" else "tv"
        val apiKey = tmdbApiKey
        val url = "https://api.themoviedb.org/3/$type/$tmdbId/external_ids?api_key=$apiKey"
        try {
            val jsonStr = HttpClient.get(url)
            val jsonObj = JSONObject(jsonStr)
            val imdbId = jsonObj.optString("imdb_id", "")
            return if (imdbId.isNotEmpty() && imdbId != "null") imdbId.removePrefix("tt") else null
        } catch (e: Exception) {
            Log.e(TAG, "getImdbId error", e)
            return null
        }
    }

    var tmdbApiKey = "c90967c3177c7d60362c59fa9cb4a333" // replace or let user configure
    var apiLanguage = "it-IT"
    var defaultProviderLanguage = "it"

    suspend fun getTrending(isMovie: Boolean): List<MediaItem> = withContext(Dispatchers.IO) {
        val type = if (isMovie) "movie" else "tv"
        val url = "https://api.themoviedb.org/3/trending/$type/week?api_key=$tmdbApiKey&language=$apiLanguage"
        val results = mutableListOf<MediaItem>()
        try {
            val jsonStr = HttpClient.get(url)
            val jsonArr = JSONObject(jsonStr).optJSONArray("results") ?: return@withContext emptyList()
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val id = obj.optLong("id")
                val title = obj.optString("title").takeIf { it.isNotBlank() } ?: obj.optString("name")
                val posterPath = obj.optString("poster_path")
                val releaseDate = obj.optString("release_date").takeIf { it.isNotBlank() } ?: obj.optString("first_air_date")
                val year = releaseDate.split("-").firstOrNull() ?: ""
                results.add(MediaItem(
                    id = id,
                    name = title,
                    type = type,
                    slug = "tmdb_home_item",
                    posterUrl = "https://image.tmdb.org/t/p/w500$posterPath",
                    year = year,
                    providerLanguage = defaultProviderLanguage
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getTrending error (TMDB)", e)
        }
        results
    }

    suspend fun getMoviesByGenre(genreId: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        val url = "https://api.themoviedb.org/3/discover/movie?api_key=$tmdbApiKey&language=$apiLanguage&with_genres=$genreId&sort_by=popularity.desc"
        val results = mutableListOf<MediaItem>()
        try {
            val jsonStr = HttpClient.get(url)
            val jsonArr = JSONObject(jsonStr).optJSONArray("results") ?: return@withContext emptyList()
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val id = obj.optLong("id")
                val title = obj.optString("title")
                val posterPath = obj.optString("poster_path")
                val releaseDate = obj.optString("release_date")
                val year = releaseDate.split("-").firstOrNull() ?: ""
                results.add(MediaItem(
                    id = id,
                    name = title,
                    type = "movie",
                    slug = "tmdb_home_item",
                    posterUrl = "https://image.tmdb.org/t/p/w500$posterPath",
                    year = year,
                    providerLanguage = defaultProviderLanguage
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMoviesByGenre error (TMDB)", e)
        }
        results
    }

    suspend fun searchMostraGuarda(query: String): List<MediaItem> = searchCinezo(query)

    suspend fun getMostraGuardaSeasons(item: MediaItem): List<Season> = getCinezoSeasons(item)

    suspend fun extractMostraGuardaUrl(item: MediaItem, season: Int?, episode: Int?): String = withContext(Dispatchers.IO) {
        val imdbId = getImdbId(item.id, item.isMovie) ?: throw Exception("Nessun IMDb ID trovato per MostraGuarda.")
        
        val baseVidxgo = DomainManager.getUrl("mostraguarda").trimEnd('/')
        val embedUrl = if (item.isMovie) {
            "$baseVidxgo/tt$imdbId"
        } else {
            "$baseVidxgo/tt$imdbId/${season ?: 1}/${episode ?: 1}"
        }

        val headers = mapOf(
            "user-agent" to HttpClient.USER_AGENT,
            "accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "alt-used" to "v.vidxgo.co",
            "sec-fetch-dest" to "iframe",
            "referer" to "https://altadefinizione.you/"
        )

        val htmlText = try {
            HttpClient.get(embedUrl, headers)
        } catch (e: Exception) {
            throw Exception("Failed to load MostraGuarda embed: ${e.message}")
        }

        if (htmlText.isBlank()) throw Exception("MostraGuarda (VidXgo) returned empty HTML")

        val regex = Regex("""var\s+\w+\s*=\s*'(\w+)'\s*,?\s*[a-zA-Z0-9_$]+\s*=\s*atob\s*\(\s*'([A-Za-z0-9+/=]+)'\s*\)""")
        val matches = regex.findAll(htmlText)

        for (match in matches) {
            val key = match.groupValues[1]
            val b64Payload = match.groupValues[2]
            try {
                val decoded = android.util.Base64.decode(b64Payload, android.util.Base64.DEFAULT)
                val keyBytes = key.toByteArray(Charsets.UTF_8)
                val keyLen = keyBytes.size
                if (keyLen == 0) continue

                val decryptedBytes = ByteArray(decoded.size)
                for (i in decoded.indices) {
                    decryptedBytes[i] = (decoded[i].toInt() xor keyBytes[i % keyLen].toInt()).toByte()
                }
                
                val decryptedStr = String(decryptedBytes, Charsets.UTF_8)
                val srcMatch = Regex("""currentSrc[^"]+"(https:[^";]+)""").find(decryptedStr)
                if (srcMatch != null) {
                    return@withContext srcMatch.groupValues[1].replace("\\\\", "")
                }
            } catch (e: Exception) {
                // Ignore matching errors
            }
        }

        throw Exception("Impossibile trovare il link stream in MostraGuarda VidXgo.")
    }
}

object Pbkdf2 {
    fun deriveKey(password: String, salt: ByteArray, iterations: Int, dkLen: Int, algorithm: String): ByteArray {
        val macAlg = when (algorithm.lowercase()) {
            "sha256", "sha-256" -> "HmacSHA256"
            "sha512", "sha-512" -> "HmacSHA512"
            else -> "HmacSHA512"
        }
        val hLen = if (macAlg == "HmacSHA256") 32 else 64
        val result = ByteArray(dkLen)
        val mac = javax.crypto.Mac.getInstance(macAlg)
        val keySpec = javax.crypto.spec.SecretKeySpec(password.toByteArray(Charsets.UTF_8), macAlg)

        var offset = 0
        var blockIndex = 1

        while (offset < dkLen) {
            mac.init(keySpec)
            mac.update(salt)
            val indexBytes = byteArrayOf(
                (blockIndex ushr 24).toByte(),
                (blockIndex ushr 16).toByte(),
                (blockIndex ushr 8).toByte(),
                blockIndex.toByte()
            )
            var u = mac.doFinal(indexBytes)
            val f = u.clone()

            for (j in 2..iterations) {
                mac.init(keySpec)
                u = mac.doFinal(u)
                for (k in f.indices) {
                    f[k] = (f[k].toInt() xor u[k].toInt()).toByte()
                }
            }

            val copyLen = minOf(dkLen - offset, hLen)
            System.arraycopy(f, 0, result, offset, copyLen)
            offset += copyLen
            blockIndex++
        }
        return result
    }
}

object CinezoDecryptor {
    fun decodePayload(payload: String): String {
        val sep = payload.indexOf('|')
        if (sep == -1) throw IllegalArgumentException("No separator found in payload")
        val dataB64 = payload.substring(sep + 1)

        val l3Bytes = android.util.Base64.decode(dataB64, android.util.Base64.DEFAULT)
        val l3String = String(l3Bytes, Charsets.UTF_8)

        val parts = l3String.split('.')
        if (parts.size != 3) throw IllegalArgumentException("L3 parts count is ${parts.size}, expected 3")

        val iv = android.util.Base64.decode(parts[0], android.util.Base64.DEFAULT)
        val salt = android.util.Base64.decode(parts[1], android.util.Base64.DEFAULT)
        val ciphertext = android.util.Base64.decode(parts[2], android.util.Base64.DEFAULT)

        val aesKey = Pbkdf2.deriveKey(
            "Sn00pD0g#L3_AES_S3cur3K3y@2026\$sex",
            salt,
            100000,
            32,
            "sha512"
        )

        val intermediateBytes = decryptAesCbc(ciphertext, aesKey, iv)
        val intermediateB64 = String(intermediateBytes, Charsets.UTF_8)

        val binaryBytes = android.util.Base64.decode(intermediateB64, android.util.Base64.DEFAULT)
        val binaryStr = String(binaryBytes, Charsets.UTF_8)

        val hexBuilder = StringBuilder()
        binaryStr.split(" ").forEach { token ->
            val trimmed = token.trim()
            if (trimmed.isNotEmpty()) {
                val code = trimmed.toInt(2)
                hexBuilder.append(code.toChar())
            }
        }
        val hexStr = hexBuilder.toString()

        val rawBytes = hexToBytes(hexStr)
        val xorKey = Pbkdf2.deriveKey(
            "Sn00pD0g#L1_X0R_M4st3rK3y!2026sex",
            "xK9!mR2@pL5#nQ8sex".toByteArray(Charsets.UTF_8),
            50000,
            32,
            "sha256"
        )

        val finalBytes = ByteArray(rawBytes.size)
        for (i in rawBytes.indices) {
            finalBytes[i] = (rawBytes[i].toInt() xor xorKey[i % xorKey.size].toInt()).toByte()
        }

        return String(finalBytes, Charsets.UTF_8)
    }

    private fun decryptAesCbc(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = javax.crypto.spec.SecretKeySpec(key, "AES")
        val ivSpec = javax.crypto.spec.IvParameterSpec(iv)
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(ciphertext)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(Regex("[^0-9a-fA-F]"), "")
        val len = cleanHex.length
        val result = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            result[i / 2] = cleanHex.substring(i, i + 2).toInt(16).toByte()
        }
        return result
    }
}

