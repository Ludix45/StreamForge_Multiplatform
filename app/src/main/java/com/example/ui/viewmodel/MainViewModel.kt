package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.ContinueWatchingItem
import com.example.data.database.FavoriteItem
import com.example.data.model.Episode
import com.example.data.model.MediaItem
import com.example.data.model.Season
import com.example.data.network.DomainManager
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

    // Sottotitoli disattivati di default: l'utente deve attivarli esplicitamente dal player.
    private val _subtitleLanguage = MutableStateFlow(prefs.getString("sub_language", "off") ?: "off")
    val subtitleLanguage = _subtitleLanguage.asStateFlow()

    private val _tmdbApiKey = MutableStateFlow("c90967c3177c7d60362c59fa9cb4a333")
    val tmdbApiKey = _tmdbApiKey.asStateFlow()

    init {
        updateScraperSettings()
    }

    // Update scraper settings
    fun setAppLanguage(lang: String) {
        prefs.edit().putString("app_language", lang).apply()
        _appLanguage.value = lang
        updateScraperSettings()
        // Refresh home data when language changes
        _homeTrendingMovies.value = emptyList()
        loadHomeData()
    }

    fun setProviderLanguage(lang: String) {
        prefs.edit().putString("provider_language", lang).apply()
        _providerLanguage.value = lang
        updateScraperSettings()
        
        // Se c'è un elemento selezionato, aggiornalo immediatamente
        val current = _selectedMediaItem.value
        if (current != null && (current.providerLanguage == "it" || current.providerLanguage == "en")) {
            switchMediaLanguage(lang)
        }
    }
    
    fun setSubtitleLanguage(lang: String) {
        prefs.edit().putString("sub_language", lang).apply()
        _subtitleLanguage.value = lang
    }

    fun setTmdbApiKey(key: String) {
        // Enforce the pre-defined api key
        _tmdbApiKey.value = "c90967c3177c7d60362c59fa9cb4a333"
        updateScraperSettings()
    }

    private fun updateScraperSettings() {
        Scraper.tmdbApiKey = _tmdbApiKey.value
        Scraper.apiLanguage = if (_appLanguage.value == "it") "it-IT" else "en-US"
        Scraper.defaultProviderLanguage = _providerLanguage.value
    }

    // Current playing episode/season tracker for next episode logic
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

    // Details state
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

    // Player state
    private val _activeStreamUrl = MutableStateFlow<String?>(null)
    val activeStreamUrl = _activeStreamUrl.asStateFlow()

    private val _isExtractingStream = MutableStateFlow(false)
    val isExtractingStream = _isExtractingStream.asStateFlow()

    private val _streamError = MutableStateFlow<String?>(null)
    val streamError = _streamError.asStateFlow()

    private val _playbackResumePosition = MutableStateFlow<Long>(0L)
    val playbackResumePosition = _playbackResumePosition.asStateFlow()

    // Bootstrap verification
    private val _isBootstrapping = MutableStateFlow(true)
    val isBootstrapping = _isBootstrapping.asStateFlow()

    // Home view state
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
                var apiMovies = try { Scraper.getTrending(isMovie = true) } catch(e: Exception) { emptyList() }
                var apiSeries = try { Scraper.getTrending(isMovie = false) } catch(e: Exception) { emptyList() }
                var actMovies = try { Scraper.getMoviesByGenre(28) } catch(e: Exception) { emptyList() }
                var comMovies = try { Scraper.getMoviesByGenre(35) } catch(e: Exception) { emptyList() }
                
                if (apiMovies.isEmpty() && apiSeries.isEmpty()) {
                    // Fallback se l'API va in crash
                    Log.d("MainViewModel", "API fallita, uso StreamingCommunity fallback")
                    try {
                        apiMovies = Scraper.searchStreamingCommunity("Batman").filter { it.isMovie }
                        apiSeries = Scraper.searchStreamingCommunity("Casa").filter { !it.isMovie }
                        actMovies = Scraper.searchStreamingCommunity("Avengers").filter { it.isMovie }
                        comMovies = Scraper.searchStreamingCommunity("Tutti").filter { it.isMovie }
                    } catch(e: Exception) {
                        Log.e("MainViewModel", "Fallback fallito", e)
                    }
                }
                
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

    // Continue Watching Flow (Feature 2)
    val continueWatchingList: StateFlow<List<ContinueWatchingItem>> = dao.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Favorites Flow
    val favoritesList: StateFlow<List<FavoriteItem>> = favDao.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val isCurrentMediaFavorite: StateFlow<Boolean> = _selectedMediaItem
        .flatMapLatest { item ->
            if (item == null) flowOf(false)
            else favDao.isFavorite("${_selectedProvider.value}_${item.id}")
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val hasNextEpisode: StateFlow<Boolean> = combine(
        _currentPlayingEpisode,
        _episodes
    ) { currentEp, eps ->
        if (currentEp == null || eps.isEmpty()) false
        else eps.any { it.number == currentEp.number + 1 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        // Bootstrap domain directories as requested
        viewModelScope.launch {
            try {
                val prefs = getApplication<Application>().getSharedPreferences("streamforge_prefs", Context.MODE_PRIVATE)
                DomainManager.load(prefs)
                Log.d("MainViewModel", "Domains initialized.")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Domain initialization error", e)
            } finally {
                _isBootstrapping.value = false
            }
        }
        
        // Auto-update domains every 12 hours
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(12 * 60 * 60 * 1000L) // 12 hours
                refreshDomainsAndApi()
            }
        }
    }

    fun refreshDomainsAndApi(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                DomainManager.load(prefs)
                Log.d("MainViewModel", "Domains reloaded successfully.")
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to reload domains", e)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun setProvider(provider: String) {
        _selectedProvider.value = provider
        _searchResults.value = emptyList()
        _searchError.value = null
    }

    fun setQuery(query: String) {
        _searchQuery.value = query
    }

    fun search() {
        val q = _searchQuery.value.trim()
        if (q.isEmpty()) return

        _isSearching.value = true
        _searchError.value = null

        viewModelScope.launch {
            try {
                val results = when (_selectedProvider.value) {
                    "StreamingCommunity" -> Scraper.searchStreamingCommunity(q)
                    "AnimeWorld" -> Scraper.searchAnimeWorld(q)
                    "EuroStreaming" -> Scraper.searchEuroStreaming(q)
                    "Cinezo" -> Scraper.searchCinezo(q)
                    "AnimeUnity" -> Scraper.searchAnimeUnity(q)
                    else -> Scraper.searchMostraGuarda(q) // Use MostraGuarda/TMDB proxy for Discovery, RaiPlay, Mediaset, etc. to make them functional
                }
                _searchResults.value = results
                if (results.isEmpty()) {
                    _searchError.value = "Nessun risultato trovato per '$q'"
                }
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
                        "StreamingCommunity" -> Scraper.searchStreamingCommunity(item.name)
                        "AnimeWorld" -> Scraper.searchAnimeWorld(item.name)
                        "EuroStreaming" -> Scraper.searchEuroStreaming(item.name)
                        "AnimeUnity" -> Scraper.searchAnimeUnity(item.name)
                        else -> emptyList()
                    }
                    targetItem = searchRes.firstOrNull { it.isMovie == item.isMovie } ?: item
                } catch(e: Exception) {
                    Log.e("MainViewModel", "Search fallback failed", e)
                }
            }

            _selectedMediaItem.value = targetItem
            _seasons.value = emptyList()
            _episodes.value = emptyList()
            _selectedSeasonNumber.value = null
            _detailsError.value = null

            if (targetItem.isMovie && _selectedProvider.value == "StreamingCommunity" || targetItem.isMovie && _selectedProvider.value == "AnimeUnity") {
                _isLoadingDetails.value = false
                return@launch
            }

            // Fetch seasons or episodes depending on the provider
            _isLoadingDetails.value = true
            try {
                when (_selectedProvider.value) {
                    "StreamingCommunity" -> {
                        val scSeasons = Scraper.getStreamingCommunitySeasons(targetItem)
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
                            val targetSeasonNum = initialSeasonNumber ?: esSeasons.first().number
                            val targetSn = esSeasons.find { it.number == targetSeasonNum } ?: esSeasons.first()
                            _selectedSeasonNumber.value = targetSn.number
                            _episodes.value = targetSn.episodes
                        }
                    }
                    "Cinezo" -> {
                        if (!targetItem.isMovie) {
                            val czSeasons = Scraper.getCinezoSeasons(targetItem)
                            _seasons.value = czSeasons
                            if (czSeasons.isNotEmpty()) {
                                val targetSeasonNum = initialSeasonNumber ?: czSeasons.first().number
                                val targetSn = czSeasons.find { it.number == targetSeasonNum } ?: czSeasons.first()
                                _selectedSeasonNumber.value = targetSn.number
                                _episodes.value = czSeasons.first().episodes
                            }
                        }
                    }
                    "AnimeWorld" -> {
                        val awEpisodes = Scraper.getAnimeWorldEpisodes(targetItem)
                        _episodes.value = awEpisodes
                        _seasons.value = listOf(Season(1, "Stagione 1", awEpisodes))
                        _selectedSeasonNumber.value = 1
                    }
                    "AnimeUnity" -> {
                        val auEpisodes = Scraper.getAnimeUnityEpisodes(targetItem.id)
                        _episodes.value = auEpisodes
                        _seasons.value = listOf(Season(1, "Stagione 1", auEpisodes))
                        _selectedSeasonNumber.value = 1
                    }
                    else -> { // MostraGuarda and all proxies
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
                _detailsError.value = "Impossibile caricare i dettagli: ${e.message}"
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
                    val scSeasons = Scraper.getStreamingCommunitySeasons(newItem)
                    _seasons.value = scSeasons
                    if (scSeasons.isNotEmpty()) {
                        val firstSeason = scSeasons.first().number
                        _selectedSeasonNumber.value = firstSeason
                        loadEpisodesForSc(newItem, firstSeason)
                    } else {
                        _episodes.value = emptyList()
                    }
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
                    "StreamingCommunity" -> {
                        loadEpisodesForSc(item, seasonNumber)
                    }
                    "EuroStreaming", "Cinezo" -> {
                        val targetSeason = _seasons.value.find { it.number == seasonNumber }
                        if (targetSeason != null) {
                            _episodes.value = targetSeason.episodes
                        }
                    }
                    "AnimeWorld", "AnimeUnity" -> {
                        val firstSeason = _seasons.value.firstOrNull { it.number == 1 }
                        if (firstSeason != null) {
                            _episodes.value = firstSeason.episodes
                        }
                    }
                    else -> { // MostraGuarda and proxies
                        val targetSeason = _seasons.value.find { it.number == seasonNumber }
                        if (targetSeason != null) {
                            _episodes.value = targetSeason.episodes
                        }
                    }
                }
            } catch (e: Exception) {
                _detailsError.value = "Errore durante il caricamento degli episodi: ${e.message}"
            } finally {
                _isLoadingDetails.value = false
            }
        }
    }

    private suspend fun loadEpisodesForSc(item: MediaItem, seasonNumber: Int) {
        val loadedEpisodes = Scraper.getStreamingCommunityEpisodes(item, seasonNumber)
        _episodes.value = loadedEpisodes
    }

    val lastWatchedEpisodeForSelected: StateFlow<ContinueWatchingItem?> = combine(
        _selectedMediaItem,
        continueWatchingList
    ) { item, list ->
        if (item == null) null
        else list.find { it.mediaId == item.id && it.provider == _selectedProvider.value }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun playMovie(item: MediaItem) {
        _isExtractingStream.value = true
        _streamError.value = null
        _activeStreamUrl.value = null
        _currentPlayingEpisode.value = null
        _currentPlayingSeason.value = null

        viewModelScope.launch {
            try {
                val provider = _selectedProvider.value
                val uniqueDbId = "${provider}_${item.id}"
                val savedItem = withContext(Dispatchers.IO) { dao.getById(uniqueDbId) }
                val resumePos = savedItem?.lastPositionMillis ?: 0L
                _playbackResumePosition.value = resumePos

                val streamUrl = when (provider) {
                    "StreamingCommunity" -> Scraper.extractStreamingCommunityUrl(item, null)
                    "AnimeWorld" -> Scraper.extractAnimeWorldUrl(item, null)
                    "EuroStreaming" -> Scraper.extractEuroStreamingUrl(item, 1, 1)
                    "Cinezo" -> Scraper.extractCinezoUrl(item, null, null)
                    "AnimeUnity" -> Scraper.extractAnimeUnityUrl(item.id)
                    else -> Scraper.extractMostraGuardaUrl(item, null, null)
                }
                _activeStreamUrl.value = streamUrl

                // Save to Continue Watching locally (Feature 2)
                saveContinueWatching(
                    item = item,
                    episode = null,
                    seasonNum = null,
                    positionMillis = resumePos
                )
            } catch (e: Exception) {
                Log.e("MainViewModel", "Extraction error", e)
                _streamError.value = "Impossibile estrarre il link di streaming: ${e.message}"
            } finally {
                _isExtractingStream.value = false
            }
        }
    }

    fun copySingleStream(item: MediaItem, seasonNumber: Int?, episode: Episode?, context: Context) {
        viewModelScope.launch {
            _isExtractingStream.value = true
            _streamError.value = null
            try {
                val provider = _selectedProvider.value
                val streamUrl = withContext(Dispatchers.IO) {
                    when (provider) {
                        "StreamingCommunity" -> Scraper.extractStreamingCommunityUrl(item, episode?.id)
                        "AnimeWorld" -> Scraper.extractAnimeWorldUrl(item, episode?.id)
                        "EuroStreaming" -> Scraper.extractEuroStreamingUrl(item, seasonNumber ?: 1, episode?.number ?: 1)
                        "Cinezo" -> Scraper.extractCinezoUrl(item, seasonNumber, episode?.number)
                        "AnimeUnity" -> if (item.isMovie) Scraper.extractAnimeUnityUrl(item.id) else Scraper.extractAnimeUnityUrl(episode?.id ?: item.id)
                        else -> Scraper.extractMostraGuardaUrl(item, seasonNumber, episode?.number)
                    }
                }
                if (streamUrl != null) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("m3u8 link", streamUrl)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(context.applicationContext, "Link copiato!", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    _streamError.value = "Link streaming non trovato."
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Copy stream error", e)
                _streamError.value = "Errore: ${e.message}"
            } finally {
                _isExtractingStream.value = false
            }
        }
    }

    fun copySeasonStreams(item: MediaItem, seasonNumber: Int, episodes: List<Episode>, context: Context) {
        viewModelScope.launch {
            _isExtractingStream.value = true
            _streamError.value = null
            try {
                val provider = _selectedProvider.value
                val stringBuilder = java.lang.StringBuilder()
                
                withContext(Dispatchers.IO) {
                    for (episode in episodes) {
                        try {
                            val streamUrl = when (provider) {
                                "StreamingCommunity" -> Scraper.extractStreamingCommunityUrl(item, episode.id)
                                "AnimeWorld" -> Scraper.extractAnimeWorldUrl(item, episode.id)
                                "EuroStreaming" -> Scraper.extractEuroStreamingUrl(item, seasonNumber, episode.number)
                                "Cinezo" -> Scraper.extractCinezoUrl(item, seasonNumber, episode.number)
                                "AnimeUnity" -> Scraper.extractAnimeUnityUrl(episode.id)
                                else -> Scraper.extractMostraGuardaUrl(item, seasonNumber, episode.number)
                            }
                            if (streamUrl != null) {
                                stringBuilder.append("${episode.number} \"${episode.name}\"\n")
                                stringBuilder.append("$streamUrl\n\n")
                            }
                            kotlinx.coroutines.delay(200) // Avoid hammering APIs for seasons with 24+ episodes
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "Error fetching episode ${episode.number}", e)
                        }
                    }
                }
                
                if (stringBuilder.isNotEmpty()) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("season m3u8 links", stringBuilder.toString().trimEnd())
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(context.applicationContext, "Link stagione copiati!", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    _streamError.value = "Nessun link trovato per questa stagione."
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Copy season error", e)
                _streamError.value = "Errore: ${e.message}"
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
                val provider = _selectedProvider.value
                val uniqueDbId = "${provider}_${item.id}"
                val savedItem = withContext(Dispatchers.IO) { dao.getById(uniqueDbId) }
                // Only resume the position if the last episode watched in DB is the exact same one!
                val resumePos = if (savedItem?.lastEpisodeId == episode.id) {
                    savedItem.lastPositionMillis ?: 0L
                } else {
                    0L
                }
                _playbackResumePosition.value = resumePos

                // Load episodes of the current season if they are not loaded yet or do not contain this episode
                if (_episodes.value.isEmpty() || _episodes.value.none { it.id == episode.id }) {
                    try {
                        when (provider) {
                            "StreamingCommunity" -> {
                                val loadedEpisodes = Scraper.getStreamingCommunityEpisodes(item, seasonNumber)
                                _episodes.value = loadedEpisodes
                                _selectedSeasonNumber.value = seasonNumber
                            }
                            "EuroStreaming" -> {
                                val esSeasons = Scraper.getEuroStreamingSeasons(item)
                                _seasons.value = esSeasons
                                val targetSn = esSeasons.find { it.number == seasonNumber }
                                if (targetSn != null) {
                                    _episodes.value = targetSn.episodes
                                    _selectedSeasonNumber.value = seasonNumber
                                }
                            }
                            "Cinezo" -> {
                                val czSeasons = Scraper.getCinezoSeasons(item)
                                _seasons.value = czSeasons
                                val targetSn = czSeasons.find { it.number == seasonNumber }
                                if (targetSn != null) {
                                    _episodes.value = targetSn.episodes
                                    _selectedSeasonNumber.value = seasonNumber
                                }
                            }
                            "AnimeWorld" -> {
                                val awEpisodes = Scraper.getAnimeWorldEpisodes(item)
                                _episodes.value = awEpisodes
                                _seasons.value = listOf(Season(1, "Stagione 1", awEpisodes))
                                _selectedSeasonNumber.value = 1
                            }
                            "AnimeUnity" -> {
                                val auEpisodes = Scraper.getAnimeUnityEpisodes(item.id)
                                _episodes.value = auEpisodes
                                _seasons.value = listOf(Season(1, "Stagione 1", auEpisodes))
                                _selectedSeasonNumber.value = 1
                            }
                            else -> { // MostraGuarda and proxies
                                val mgSeasons = Scraper.getMostraGuardaSeasons(item)
                                _seasons.value = mgSeasons
                                val targetSn = mgSeasons.find { it.number == seasonNumber }
                                if (targetSn != null) {
                                    _episodes.value = targetSn.episodes
                                    _selectedSeasonNumber.value = seasonNumber
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Error reloading episodes list on playEpisode", e)
                    }
                }

                val streamUrl = when (provider) {
                    "StreamingCommunity" -> Scraper.extractStreamingCommunityUrl(item, episode.id)
                    "AnimeWorld" -> Scraper.extractAnimeWorldUrl(item, episode.id)
                    "EuroStreaming" -> Scraper.extractEuroStreamingUrl(item, seasonNumber, episode.number)
                    "Cinezo" -> Scraper.extractCinezoUrl(item, seasonNumber, episode.number)
                    "AnimeUnity" -> Scraper.extractAnimeUnityUrl(episode.id)
                    else -> Scraper.extractMostraGuardaUrl(item, seasonNumber, episode.number)
                }
                _activeStreamUrl.value = streamUrl

                // Save to Continue Watching locally. (Feature 2)
                // This updates the single entry representing the movie/series because id is based on "${provider}_${item.id}"
                saveContinueWatching(
                    item = item,
                    episode = episode,
                    seasonNum = seasonNumber,
                    positionMillis = resumePos
                )
            } catch (e: Exception) {
                Log.e("MainViewModel", "Extraction error", e)
                _streamError.value = "Impossibile estrarre il link di streaming: ${e.message}"
            } finally {
                _isExtractingStream.value = false
            }
        }
    }

    // Cambia la lingua (it/en) del contenuto attualmente in riproduzione, senza uscire dal player.
    // Al momento la scelta della lingua audio dipende dalla versione del sito sorgente
    // (StreamingCommunity espone un dominio/versione "it" e una "en"), quindi il cambio
    // richiede una nuova estrazione del link dello stream, non un semplice switch di traccia.
    fun switchPlaybackLanguage(lang: String, currentPositionMillis: Long = 0L) {
        val current = _selectedMediaItem.value ?: return
        if (current.providerLanguage == lang) return

        val provider = _selectedProvider.value
        val currentEpisode = _currentPlayingEpisode.value
        val currentSeason = _currentPlayingSeason.value
        val positionBeforeSwitch = currentPositionMillis

        prefs.edit().putString("provider_language", lang).apply()
        _providerLanguage.value = lang
        updateScraperSettings()

        val updatedItem = current.copy(providerLanguage = lang)
        _selectedMediaItem.value = updatedItem

        if (provider != "StreamingCommunity") {
            // Le altre fonti non hanno un concetto di lingua a livello di stream: non c'è altro da fare.
            return
        }

        _isExtractingStream.value = true
        _streamError.value = null

        viewModelScope.launch {
            try {
                if (currentEpisode != null && currentSeason != null) {
                    // Ricarica la lista episodi nella nuova versione linguistica, poi riestrae
                    // lo stream dell'episodio che si stava guardando, mantenendo la posizione.
                    val scSeasons = withContext(Dispatchers.IO) { Scraper.getStreamingCommunitySeasons(updatedItem) }
                    _seasons.value = scSeasons
                    val matchingSeason = scSeasons.find { it.number == currentSeason }
                    val newEpisodes = matchingSeason?.episodes ?: emptyList()
                    _episodes.value = newEpisodes
                    val matchingEpisode = newEpisodes.find { it.number == currentEpisode.number } ?: currentEpisode

                    _playbackResumePosition.value = positionBeforeSwitch
                    val streamUrl = withContext(Dispatchers.IO) {
                        Scraper.extractStreamingCommunityUrl(updatedItem, matchingEpisode.id)
                    }
                    _currentPlayingEpisode.value = matchingEpisode
                    _activeStreamUrl.value = streamUrl
                } else {
                    // Film: riestrae semplicemente il link nella nuova lingua, mantenendo la posizione.
                    _playbackResumePosition.value = positionBeforeSwitch
                    val streamUrl = withContext(Dispatchers.IO) {
                        Scraper.extractStreamingCommunityUrl(updatedItem, null)
                    }
                    _activeStreamUrl.value = streamUrl
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Switch playback language failed", e)
                _streamError.value = "Impossibile cambiare lingua: ${e.message}"
            } finally {
                _isExtractingStream.value = false
            }
        }
    }

    fun playNextEpisode() {
        val item = _selectedMediaItem.value ?: return
        val currentEp = _currentPlayingEpisode.value ?: return
        val currentSn = _currentPlayingSeason.value ?: return
        val nextEp = _episodes.value.find { it.number == currentEp.number + 1 }
        if (nextEp != null) {
            playEpisode(item, currentSn, nextEp)
        }
    }

    fun toggleFavorite() {
        val item = _selectedMediaItem.value ?: return
        val provider = _selectedProvider.value
        val uniqueId = "${provider}_${item.id}"
        viewModelScope.launch(Dispatchers.IO) {
            val exists = favDao.isFavoriteSync(uniqueId)
            if (exists) {
                favDao.deleteById(uniqueId)
                Log.d("MainViewModel", "Removed favorite: $uniqueId")
            } else {
                val favItem = FavoriteItem(
                    id = uniqueId,
                    provider = provider,
                    mediaId = item.id,
                    name = item.name,
                    type = item.type,
                    slug = item.slug,
                    posterUrl = item.posterUrl,
                    year = item.year,
                    timestamp = System.currentTimeMillis()
                )
                favDao.insert(favItem)
                Log.d("MainViewModel", "Added favorite: $uniqueId")
            }
        }
    }

    fun clearPlayerState() {
        _activeStreamUrl.value = null
        _streamError.value = null
    }

    fun deleteContinueWatchingItem(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteById(id)
        }
    }

    fun updatePlaybackPosition(positionMillis: Long, durationMillis: Long) {
        val item = _selectedMediaItem.value ?: return
        val currentEp = _currentPlayingEpisode.value
        val currentSn = _currentPlayingSeason.value
        viewModelScope.launch(Dispatchers.IO) {
            saveContinueWatching(
                item = item,
                episode = currentEp,
                seasonNum = currentSn,
                positionMillis = positionMillis,
                durationMillis = durationMillis
            )
        }
    }

    private suspend fun saveContinueWatching(
        item: MediaItem,
        episode: Episode?,
        seasonNum: Int?,
        positionMillis: Long? = 0L,
        durationMillis: Long? = 0L
    ) = withContext(Dispatchers.IO) {
        val provider = _selectedProvider.value
        val uniqueDbId = "${provider}_${item.id}"

        val dbItem = ContinueWatchingItem(
            id = uniqueDbId,
            provider = provider,
            mediaId = item.id,
            name = item.name,
            type = item.type,
            slug = item.slug,
            posterUrl = item.posterUrl,
            year = item.year,
            lastEpisodeId = episode?.id,
            lastEpisodeNumber = episode?.number,
            lastEpisodeName = episode?.name,
            lastSeasonNumber = seasonNum,
            timestamp = System.currentTimeMillis(),
            lastPositionMillis = positionMillis,
            durationMillis = durationMillis
        )
        dao.insert(dbItem)
        Log.d("MainViewModel", "Saved to continue watching: $uniqueDbId, lastEp: ${episode?.number}, pos: $positionMillis, dur: $durationMillis")
    }
}

class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
