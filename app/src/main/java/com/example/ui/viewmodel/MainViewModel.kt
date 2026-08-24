package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.ContinueWatchingItem
import com.example.data.database.FavoriteItem
import com.example.data.model.Episode
import com.example.data.model.MediaItem
import com.example.data.model.Season
import com.example.data.network.DomainManager
import com.example.data.network.CommonScraper
import com.example.data.network.Scraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.continueWatchingDao()
    private val favDao = db.favoriteDao()
    private val prefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _appLanguage = MutableStateFlow(prefs.getString("app_language", "it") ?: "it")
    val appLanguage = _appLanguage.asStateFlow()
    
    private val _providerLanguage = MutableStateFlow(prefs.getString("provider_language", "it") ?: "it")
    val providerLanguage = _providerLanguage.asStateFlow()

    private val _subtitleLanguage = MutableStateFlow(prefs.getString("sub_language", "off") ?: "off")
    val subtitleLanguage = _subtitleLanguage.asStateFlow()

    private val _tmdbApiKey = MutableStateFlow("c90967c3177c7d60362c59fa9cb4a333")
    val tmdbApiKey = _tmdbApiKey.asStateFlow()

    init {
        updateScraperSettings()
    }

    fun setAppLanguage(lang: String) {
        prefs.edit().putString("app_language", lang).apply()
        _appLanguage.value = lang
        updateScraperSettings()
        _homeTrendingMovies.value = emptyList()
        loadHomeData()
    }

    fun setProviderLanguage(lang: String) {
        prefs.edit().putString("provider_language", lang).apply()
        _providerLanguage.value = lang
        updateScraperSettings()
        val current = _selectedMediaItem.value
        if (current != null && (current.providerLanguage == "it" || current.providerLanguage == "en")) {
            switchMediaLanguage(lang)
        }
    }
    
    fun setSubtitleLanguage(lang: String) {
        prefs.edit().putString("sub_language", lang).apply()
        _subtitleLanguage.value = lang
    }

    private fun updateScraperSettings() {
        Scraper.tmdbApiKey = _tmdbApiKey.value
        Scraper.apiLanguage = if (_appLanguage.value == "it") "it-IT" else "en-US"
        Scraper.defaultProviderLanguage = _providerLanguage.value
    }

    private val _currentPlayingEpisode = MutableStateFlow<Episode?>(null)
    val currentPlayingEpisode = _currentPlayingEpisode.asStateFlow()

    private val _currentPlayingSeason = MutableStateFlow<Int?>(null)
    val currentPlayingSeason = _currentPlayingSeason.asStateFlow()

    private val _selectedProvider = MutableStateFlow("StreamingCommunity")
    val selectedProvider = _selectedProvider.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError = _searchError.asStateFlow()

    private val _selectedMediaItem = MutableStateFlow<MediaItem?>(null)
    val selectedMediaItem = _selectedMediaItem.asStateFlow()

    private val _seasons = MutableStateFlow<List<Season>>(emptyList())
    val seasons = _seasons.asStateFlow()

    private val _episodes = MutableStateFlow<List<Episode>>(emptyList())
    val episodes = _episodes.asStateFlow()

    private val _selectedSeasonNumber = MutableStateFlow<Int?>(null)
    val selectedSeasonNumber = _selectedSeasonNumber.asStateFlow()

    private val _isLoadingDetails = MutableStateFlow(false)
    val isLoadingDetails = _isLoadingDetails.asStateFlow()

    private val _detailsError = MutableStateFlow<String?>(null)
    val detailsError = _detailsError.asStateFlow()

    private val _activeStreamUrl = MutableStateFlow<String?>(null)
    val activeStreamUrl = _activeStreamUrl.asStateFlow()

    private val _isExtractingStream = MutableStateFlow(false)
    val isExtractingStream = _isExtractingStream.asStateFlow()

    private val _streamError = MutableStateFlow<String?>(null)
    val streamError = _streamError.asStateFlow()

    private val _playbackResumePosition = MutableStateFlow<Long>(0L)
    val playbackResumePosition = _playbackResumePosition.asStateFlow()

    private val _isBootstrapping = MutableStateFlow(true)
    val isBootstrapping = _isBootstrapping.asStateFlow()

    private val _homeTrendingMovies = MutableStateFlow<List<MediaItem>>(emptyList())
    val homeTrendingMovies = _homeTrendingMovies.asStateFlow()

    private val _homeTrendingSeries = MutableStateFlow<List<MediaItem>>(emptyList())
    val homeTrendingSeries = _homeTrendingSeries.asStateFlow()

    private val _homeActionMovies = MutableStateFlow<List<MediaItem>>(emptyList())
    val homeActionMovies = _homeActionMovies.asStateFlow()

    private val _homeComedyMovies = MutableStateFlow<List<MediaItem>>(emptyList())
    val homeComedyMovies = _homeComedyMovies.asStateFlow()

    private val _homeError = MutableStateFlow<String?>(null)
    val homeError = _homeError.asStateFlow()

    fun loadHomeData() {
        if (_homeTrendingMovies.value.isNotEmpty()) return
        _homeError.value = null
        viewModelScope.launch {
            try {
                val lang = if (_appLanguage.value == "it") "it-IT" else "en-US"
                val apiMovies = try { CommonScraper.getTrending(true, _tmdbApiKey.value, lang) } catch(e: Exception) { emptyList() }
                val apiSeries = try { CommonScraper.getTrending(false, _tmdbApiKey.value, lang) } catch(e: Exception) { emptyList() }
                val actMovies = try { CommonScraper.getMoviesByGenre(28, _tmdbApiKey.value, lang) } catch(e: Exception) { emptyList() }
                val comMovies = try { CommonScraper.getMoviesByGenre(35, _tmdbApiKey.value, lang) } catch(e: Exception) { emptyList() }
                
                _homeTrendingMovies.value = apiMovies
                _homeTrendingSeries.value = apiSeries
                _homeActionMovies.value = actMovies
                _homeComedyMovies.value = comMovies
                
                if (apiMovies.isEmpty() && apiSeries.isEmpty()) {
                    _homeError.value = "Nessun risultato. Controlla connessione o API key."
                }
            } catch (e: Exception) {
                _homeError.value = "Errore: ${e.message}"
                Log.e("MainViewModel", "Error loading home data", e)
            }
        }
    }

    val continueWatchingList: StateFlow<List<ContinueWatchingItem>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoritesList: StateFlow<List<FavoriteItem>> = favDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val isCurrentMediaFavorite: StateFlow<Boolean> = _selectedMediaItem
        .flatMapLatest { item ->
            if (item == null) flowOf(false)
            else favDao.isFavorite("${_selectedProvider.value}_${item.id}")
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasNextEpisode: StateFlow<Boolean> = combine(_currentPlayingEpisode, _episodes) { currentEp, eps ->
        if (currentEp == null || eps.isEmpty()) false
        else eps.any { it.number == currentEp.number + 1 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        viewModelScope.launch {
            try {
                val sPrefs = getApplication<Application>().getSharedPreferences("streamforge_prefs", Context.MODE_PRIVATE)
                DomainManager.load(sPrefs)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Domain initialization error", e)
            } finally {
                _isBootstrapping.value = false
            }
        }
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(12 * 60 * 60 * 1000L)
                refreshDomainsAndApi()
            }
        }
    }

    fun refreshDomainsAndApi(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                DomainManager.load(prefs)
                withContext(Dispatchers.Main) { onComplete?.invoke(true) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onComplete?.invoke(false) }
            }
        }
    }

    fun setProvider(provider: String) {
        _selectedProvider.value = provider
        _searchResults.value = emptyList()
        _searchError.value = null
    }

    fun setQuery(query: String) { _searchQuery.value = query }

    fun search() {
        val q = _searchQuery.value.trim()
        if (q.isEmpty()) return
        _isSearching.value = true
        _searchError.value = null
        viewModelScope.launch {
            try {
                val results = when (_selectedProvider.value) {
                    "StreamingCommunity" -> CommonScraper.searchStreamingCommunity(q, DomainManager.getUrl("streamingcommunity"))
                    "AnimeWorld" -> CommonScraper.searchAnimeWorld(q, DomainManager.getUrl("animeworld"))
                    "EuroStreaming" -> CommonScraper.searchEuroStreaming(q, DomainManager.getUrl("eurostreaming"))
                    "Cinezo" -> CommonScraper.searchCinezo(q, _tmdbApiKey.value)
                    "AnimeUnity" -> CommonScraper.searchAnimeUnity(q, DomainManager.getUrl("animeunity"))
                    else -> Scraper.searchMostraGuarda(q)
                }
                _searchResults.value = results
                if (results.isEmpty()) _searchError.value = "Nessun risultato trovato per '$q'"
            } catch (e: Exception) {
                _searchError.value = "Errore durante la ricerca: ${e.message}"
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun selectMediaItem(item: MediaItem?, initialSeasonNumber: Int? = null) {
        if (item == null) {
            _selectedMediaItem.value = null
            _seasons.value = emptyList()
            _episodes.value = emptyList()
            _selectedSeasonNumber.value = null
            _detailsError.value = null
            return
        }

        viewModelScope.launch {
            var targetItem = item
            if (item.slug == "tmdb_home_item" && _selectedProvider.value != "Cinezo" && _selectedProvider.value != "MostraGuarda") {
                _isLoadingDetails.value = true
                try {
                    val searchRes = when (_selectedProvider.value) {
                        "StreamingCommunity" -> CommonScraper.searchStreamingCommunity(item.name, DomainManager.getUrl("streamingcommunity"))
                        "AnimeWorld" -> CommonScraper.searchAnimeWorld(item.name, DomainManager.getUrl("animeworld"))
                        "EuroStreaming" -> CommonScraper.searchEuroStreaming(item.name, DomainManager.getUrl("eurostreaming"))
                        "AnimeUnity" -> CommonScraper.searchAnimeUnity(item.name, DomainManager.getUrl("animeunity"))
                        else -> emptyList()
                    }
                    targetItem = searchRes.firstOrNull { it.isMovie == item.isMovie } ?: item
                } catch(e: Exception) {}
            }

            _selectedMediaItem.value = targetItem
            _seasons.value = emptyList()
            _episodes.value = emptyList()
            _selectedSeasonNumber.value = null
            _detailsError.value = null

            if (targetItem.isMovie && (_selectedProvider.value == "StreamingCommunity" || _selectedProvider.value == "AnimeUnity")) {
                _isLoadingDetails.value = false
                return@launch
            }

            _isLoadingDetails.value = true
            try {
                when (_selectedProvider.value) {
                    "StreamingCommunity" -> {
                        val scSeasons = CommonScraper.getStreamingCommunitySeasons(targetItem, DomainManager.getUrl("streamingcommunity"))
                        _seasons.value = scSeasons
                        if (scSeasons.isNotEmpty()) {
                            val targetSeason = initialSeasonNumber ?: scSeasons.first().number
                            _selectedSeasonNumber.value = targetSeason
                            loadEpisodesForSc(targetItem, targetSeason)
                        }
                    }
                    "EuroStreaming" -> {
                        val esSeasons = Scraper.getEuroStreamingSeasons(targetItem)
                        _seasons.value = esSeasons
                        if (esSeasons.isNotEmpty()) {
                            val tSn = initialSeasonNumber ?: esSeasons.first().number
                            val targetSn = esSeasons.find { it.number == tSn } ?: esSeasons.first()
                            _selectedSeasonNumber.value = targetSn.number
                            _episodes.value = targetSn.episodes
                        }
                    }
                    "Cinezo" -> {
                        if (!targetItem.isMovie) {
                            val czSeasons = CommonScraper.getCinezoSeasons(targetItem, _tmdbApiKey.value)
                            _seasons.value = czSeasons
                            if (czSeasons.isNotEmpty()) {
                                val tSn = initialSeasonNumber ?: czSeasons.first().number
                                val targetSn = czSeasons.find { it.number == tSn } ?: czSeasons.first()
                                _selectedSeasonNumber.value = targetSn.number
                                _episodes.value = targetSn.episodes
                            }
                        }
                    }
                    "AnimeWorld" -> {
                        val awEpisodes = CommonScraper.getAnimeWorldEpisodes(targetItem, DomainManager.getUrl("animeworld"))
                        _episodes.value = awEpisodes
                        _seasons.value = listOf(Season(1, "Stagione 1", awEpisodes))
                        _selectedSeasonNumber.value = 1
                    }
                    "AnimeUnity" -> {
                        val auEpisodes = CommonScraper.getAnimeUnityEpisodes(targetItem.id, DomainManager.getUrl("animeunity"))
                        _episodes.value = auEpisodes
                        _seasons.value = listOf(Season(1, "Stagione 1", auEpisodes))
                        _selectedSeasonNumber.value = 1
                    }
                    else -> {
                        if (!targetItem.isMovie) {
                            val mgSeasons = Scraper.getMostraGuardaSeasons(targetItem)
                            _seasons.value = mgSeasons
                            if (mgSeasons.isNotEmpty()) {
                                val firstSeason = mgSeasons.first().number
                                _selectedSeasonNumber.value = firstSeason
                                _episodes.value = mgSeasons.first().episodes
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _detailsError.value = "Dettagli non caricati: ${e.message}"
            } finally {
                _isLoadingDetails.value = false
            }
        }
    }

    fun switchMediaLanguage(lang: String) {
        val current = _selectedMediaItem.value ?: return
        if (current.providerLanguage == lang) return
        viewModelScope.launch {
            _isLoadingDetails.value = true
            try {
                val newItem = current.copy(providerLanguage = lang)
                _selectedMediaItem.value = newItem
                if (!newItem.isMovie) {
                    val scSeasons = CommonScraper.getStreamingCommunitySeasons(newItem, DomainManager.getUrl("streamingcommunity"))
                    _seasons.value = scSeasons
                    if (scSeasons.isNotEmpty()) {
                        val firstSeason = scSeasons.first().number
                        _selectedSeasonNumber.value = firstSeason
                        loadEpisodesForSc(newItem, firstSeason)
                    } else _episodes.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Switch language failed", e)
            } finally {
                _isLoadingDetails.value = false
            }
        }
    }

    fun selectSeason(seasonNumber: Int) {
        val item = _selectedMediaItem.value ?: return
        if (_selectedSeasonNumber.value == seasonNumber) return
        _selectedSeasonNumber.value = seasonNumber
        _episodes.value = emptyList()
        _isLoadingDetails.value = true
        viewModelScope.launch {
            try {
                when (_selectedProvider.value) {
                    "StreamingCommunity" -> loadEpisodesForSc(item, seasonNumber)
                    "EuroStreaming", "Cinezo", "MostraGuarda" -> {
                        val targetSeason = _seasons.value.find { it.number == seasonNumber }
                        if (targetSeason != null) _episodes.value = targetSeason.episodes
                    }
                    "AnimeWorld", "AnimeUnity" -> {
                        val firstSeason = _seasons.value.firstOrNull { it.number == 1 }
                        if (firstSeason != null) _episodes.value = firstSeason.episodes
                    }
                }
            } catch (e: Exception) {
                _detailsError.value = "Errore caricamento episodi: ${e.message}"
            } finally {
                _isLoadingDetails.value = false
            }
        }
    }

    private suspend fun loadEpisodesForSc(item: MediaItem, seasonNumber: Int) {
        val loadedEpisodes = CommonScraper.getStreamingCommunityEpisodes(item, seasonNumber, DomainManager.getUrl("streamingcommunity"))
        _episodes.value = loadedEpisodes
    }

    fun playMovie(item: MediaItem) {
        _isExtractingStream.value = true
        _streamError.value = null
        _activeStreamUrl.value = null
        _currentPlayingEpisode.value = null
        _currentPlayingSeason.value = null
        viewModelScope.launch {
            try {
                val url = when (_selectedProvider.value) {
                    "StreamingCommunity" -> CommonScraper.extractStreamingCommunityUrl(item, null, DomainManager.getUrl("streamingcommunity"))
                    "AnimeWorld" -> Scraper.extractAnimeWorldUrl(item, null)
                    "Cinezo" -> Scraper.extractCinezoUrl(item, null, null)
                    "AnimeUnity" -> Scraper.extractAnimeUnityUrl(item.id) // Scraper still has it
                    else -> Scraper.extractMostraGuardaUrl(item, null, null)
                }
                _playbackResumePosition.value = dao.getById("${_selectedProvider.value}_${item.id}")?.lastPositionMillis ?: 0L
                _activeStreamUrl.value = url
            } catch (e: Exception) {
                _streamError.value = "Errore estrazione stream: ${e.message}"
            } finally {
                _isExtractingStream.value = false
            }
        }
    }

    fun playEpisode(item: MediaItem, seasonNumber: Int, episode: Episode) {
        _isExtractingStream.value = true
        _streamError.value = null
        _activeStreamUrl.value = null
        _currentPlayingEpisode.value = episode
        _currentPlayingSeason.value = seasonNumber
        viewModelScope.launch {
            try {
                val url = when (_selectedProvider.value) {
                    "StreamingCommunity" -> CommonScraper.extractStreamingCommunityUrl(item, episode.id, DomainManager.getUrl("streamingcommunity"))
                    "AnimeWorld" -> Scraper.extractAnimeWorldUrl(item, episode.id)
                    "EuroStreaming" -> Scraper.extractEuroStreamingUrl(item, seasonNumber, episode.number)
                    "Cinezo" -> Scraper.extractCinezoUrl(item, seasonNumber, episode.number)
                    "AnimeUnity" -> Scraper.extractAnimeUnityUrl(episode.id)
                    else -> Scraper.extractMostraGuardaUrl(item, seasonNumber, episode.number)
                }
                _playbackResumePosition.value = dao.getById("${_selectedProvider.value}_${item.id}_S${seasonNumber}_E${episode.number}")?.lastPositionMillis ?: 0L
                _activeStreamUrl.value = url
            } catch (e: Exception) {
                _streamError.value = "Errore estrazione stream: ${e.message}"
            } finally {
                _isExtractingStream.value = false
            }
        }
    }

    fun playNextEpisode() {
        val item = _selectedMediaItem.value ?: return
        val currentEp = _currentPlayingEpisode.value ?: return
        val currentSn = _currentPlayingSeason.value ?: 1
        val nextEp = _episodes.value.find { it.number == currentEp.number + 1 }
        if (nextEp != null) playEpisode(item, currentSn, nextEp)
    }

    fun updatePlaybackPosition(position: Long, duration: Long) {
        val item = _selectedMediaItem.value ?: return
        val provider = _selectedProvider.value
        val episode = _currentPlayingEpisode.value
        val season = _currentPlayingSeason.value
        
        val key = if (episode == null) "${provider}_${item.id}" 
                  else "${provider}_${item.id}_S${season}_E${episode.number}"
        
        viewModelScope.launch(Dispatchers.IO) {
            dao.insert(ContinueWatchingItem(
                id = key, mediaId = item.id, name = item.name, type = item.type, slug = item.slug, 
                posterUrl = item.posterUrl, year = item.year, provider = provider,
                lastSeasonNumber = season, lastEpisodeNumber = episode?.number, lastEpisodeId = episode?.id, lastEpisodeName = episode?.name,
                lastPositionMillis = position, durationMillis = duration, timestamp = System.currentTimeMillis()
            ))
        }
    }

    fun deleteContinueWatchingItem(id: String) {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteById(id) }
    }

    fun toggleFavorite() {
        val item = _selectedMediaItem.value ?: return
        val provider = _selectedProvider.value
        val key = "${provider}_${item.id}"
        viewModelScope.launch(Dispatchers.IO) {
            if (favDao.isFavoriteSync(key)) favDao.deleteById(key)
            else favDao.insert(FavoriteItem(key, provider, item.id, item.name, item.type, item.slug, item.posterUrl, item.year, System.currentTimeMillis()))
        }
    }

    fun copySingleStream(item: MediaItem, season: Int, episode: Episode, context: Context) {
        viewModelScope.launch {
            try {
                val url = when (_selectedProvider.value) {
                    "StreamingCommunity" -> CommonScraper.extractStreamingCommunityUrl(item, episode.id, DomainManager.getUrl("streamingcommunity"))
                    "AnimeWorld" -> Scraper.extractAnimeWorldUrl(item, episode.id)
                    "EuroStreaming" -> Scraper.extractEuroStreamingUrl(item, season, episode.number)
                    "Cinezo" -> Scraper.extractCinezoUrl(item, season, episode.number)
                    "AnimeUnity" -> Scraper.extractAnimeUnityUrl(episode.id)
                    else -> Scraper.extractMostraGuardaUrl(item, season, episode.number)
                }
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("m3u8 link", url)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(context, "Link m3u8 copiato!", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Errore copia: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun copySeasonStreams(item: MediaItem, season: Int, episodes: List<Episode>, context: Context) {
        viewModelScope.launch {
            try {
                val results = mutableListOf<String>()
                episodes.forEach { ep ->
                    try {
                        val url = when (_selectedProvider.value) {
                            "StreamingCommunity" -> CommonScraper.extractStreamingCommunityUrl(item, ep.id, DomainManager.getUrl("streamingcommunity"))
                            "AnimeWorld" -> Scraper.extractAnimeWorldUrl(item, ep.id)
                            "EuroStreaming" -> Scraper.extractEuroStreamingUrl(item, season, ep.number)
                            "Cinezo" -> Scraper.extractCinezoUrl(item, season, ep.number)
                            "AnimeUnity" -> Scraper.extractAnimeUnityUrl(ep.id)
                            else -> Scraper.extractMostraGuardaUrl(item, season, ep.number)
                        }
                        results.add("${item.name} S${season}E${ep.number}: $url")
                    } catch (e: Exception) {}
                }
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("m3u8 list", results.joinToString("\n"))
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(context, "Tutti i link della stagione copiati!", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Errore copia stagione", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun switchPlaybackLanguage(lang: String, currentPosition: Long) {
        val item = _selectedMediaItem.value ?: return
        val episode = _currentPlayingEpisode.value
        viewModelScope.launch {
            _isExtractingStream.value = true
            try {
                _providerLanguage.value = lang
                val url = if (episode == null) {
                    CommonScraper.extractStreamingCommunityUrl(item.copy(providerLanguage = lang), null, DomainManager.getUrl("streamingcommunity"))
                } else {
                    CommonScraper.extractStreamingCommunityUrl(item.copy(providerLanguage = lang), episode.id, DomainManager.getUrl("streamingcommunity"))
                }
                _playbackResumePosition.value = currentPosition
                _activeStreamUrl.value = url
            } catch (e: Exception) {
                Log.e("MainViewModel", "Switch playback language failed", e)
            } finally {
                _isExtractingStream.value = false
            }
        }
    }
}

class MainViewModelFactory(private val application: Application) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
