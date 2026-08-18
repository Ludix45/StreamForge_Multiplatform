package com.aistudio.streamforge.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.dp
import com.example.data.model.Episode
import com.example.data.model.MediaItem
import com.example.data.model.Season
import com.example.data.network.DomainManager
import com.example.data.network.HttpClient
import com.example.data.network.Scraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI
import java.net.URL
import javax.imageio.ImageIO
import okhttp3.Request
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import androidx.compose.ui.awt.SwingPanel


/** Pages available in the desktop companion. They mirror the Android navigation model. */
private enum class DesktopPage { HOME, SEARCH, CONTINUE, FAVORITES, SETTINGS, DETAILS, PLAYER }

/** Provider identifiers are persisted with library items, rather than inferred from a title. */
private enum class Provider(val label: String) {
    STREAMING_COMMUNITY("StreamingCommunity"), ANIME_UNITY("AnimeUnity"), ANIME_WORLD("AnimeWorld"), EURO_STREAMING(
        "EuroStreaming"
    ),
    CINEZO("Cinezo");

    companion object {
        fun from(value: String) = entries.firstOrNull { it.label == value } ?: STREAMING_COMMUNITY
    }
}

/** Owns navigation and provider calls while preserving Android scraper logic verbatim. */
@Composable
fun StreamForgeDesktopApp() {
    val scope = rememberCoroutineScope()
    // ... (rest of the variables)
    var page by remember { mutableStateOf(DesktopPage.HOME) }
    var selectedProvider by remember { mutableStateOf(Provider.STREAMING_COMMUNITY) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var homeMovies by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var homeSeries by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var selectedItem by remember { mutableStateOf<MediaItem?>(null) }
    var selectedItemProvider by remember { mutableStateOf(Provider.STREAMING_COMMUNITY) }
    var seasons by remember { mutableStateOf<List<Season>>(emptyList()) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var selectedSeason by remember { mutableStateOf<Season?>(null) }
    var currentEntry by remember { mutableStateOf<LibraryEntry?>(null) }
    var playbackUrl by remember { mutableStateOf<String?>(null) }
    var favorites by remember { mutableStateOf(DesktopLibraryStore.favorites()) }
    var continued by remember { mutableStateOf(DesktopLibraryStore.continueWatching()) }
    var status by remember { mutableStateOf("Pronto") }

    /** Resolves a title/episode and records it before the embedded player starts. */
    fun play(entry: LibraryEntry) = scope.launch {
        status = "Risoluzione stream…"
        playbackUrl = runCatching { resolve(entry) }.onFailure {
            status = "Riproduzione non disponibile: ${it.message}"
        }.getOrNull()
        if (playbackUrl != null) {
            currentEntry = entry
            DesktopLibraryStore.saveProgress(entry)
            continued = DesktopLibraryStore.continueWatching()
            page = DesktopPage.PLAYER
        }
    }

    fun copyToClipboard(text: String) {
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    }

    /** Opens details and lazily retrieves seasons where that provider exposes them. */
    fun openDetails(provider: Provider, item: MediaItem) = scope.launch {
        status = "Caricamento dettagli…"
        val enrichedItem = Scraper.enrichWithTMDB(item)
        selectedItem = enrichedItem; selectedItemProvider = provider; selectedSeason = null; episodes =
        emptyList()
        seasons = runCatching { loadSeasons(provider, enrichedItem) }.getOrDefault(emptyList())
        selectedSeason = seasons.firstOrNull()
        episodes = selectedSeason?.let { season ->
            runCatching { loadEpisodes(provider, enrichedItem, season) }.getOrDefault(emptyList())
        }.orEmpty()
        status = "Pronto"; page = DesktopPage.DETAILS
    }

    LaunchedEffect(Unit) {
        // The home is useful immediately and does not depend on a manual search.
        homeMovies = runCatching { Scraper.getTrending(true) }.getOrDefault(emptyList())
        homeSeries = runCatching { Scraper.getTrending(false) }.getOrDefault(emptyList())
    }

    // Never silently replace MPV with VLC: a different engine hides the real cause of a failure.
    var mpvFailure by remember(playbackUrl) { mutableStateOf<String?>(null) }
    var playerSession by remember(playbackUrl) { mutableStateOf(0) }
    val savePlaybackProgress: (Long) -> Unit = { position ->
        currentEntry?.let { entry -> DesktopLibraryStore.saveProgress(entry.copy(resumePositionMs = position)) }
    }
    val openNextEpisode: () -> Unit = {
        val entry = currentEntry
        val now = entry?.episode
        val next = now?.let { current -> episodes.firstOrNull { it.number > current.number } }
        if (entry != null && next != null) play(entry.copy(episode = next, resumePositionMs = 0L))
    }

    if (page == DesktopPage.PLAYER && playbackUrl != null) {
        val playerTitle = currentEntry?.let(::displayTitle).orEmpty()
        val hasNext =
            currentEntry?.episode?.let { now -> episodes.any { it.number > now.number } } == true
        val playbackHeaders =
            currentEntry?.let { playbackHeadersFor(Provider.from(it.provider)) }.orEmpty()
        if (mpvFailure != null) {
            Column(
                modifier = Modifier.fillMaxSize().background(Color(0xFF101218)).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "MPV non è riuscito ad avviarsi",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFFFF7900)
                )
                
                // Usiamo SwingPanel per renderizzare l'HTML interattivo (link cliccabili)
                SwingPanel(
                    factory = {
                        JEditorPane("text/html", mpvFailure!!).apply {
                            isEditable = false
                            isOpaque = false
                            background = java.awt.Color(0,0,0,0)
                            addHyperlinkListener { e ->
                                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                                    runCatching { Desktop.getDesktop().browse(e.url.toURI()) }
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { mpvFailure = null; playerSession++ }) { Text("Riprova MPV") }
                    Button(onClick = { page = DesktopPage.DETAILS }) { Text("Indietro") }
                }
            }
        } else {
            key(playerSession) {
                EmbeddedLibMpvPlayer(
                    url = playbackUrl!!,
                    title = "[MPV] $playerTitle",
                    headers = playbackHeaders,
                    resumeAtMillis = currentEntry?.resumePositionMs ?: 0L,
                    onProgress = savePlaybackProgress,
                    onUnavailable = { message -> mpvFailure = message },
                    onBack = { page = DesktopPage.DETAILS },
                    onNext = openNextEpisode,
                    hasNext = hasNext,
                )
            }
        }
        return
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF101218) // Il tuo colore di sfondo
    ) {
    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF101218))) {
        Navigation(page) { page = it }
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (page) {
                DesktopPage.HOME -> HomePage(
                    homeMovies,
                    homeSeries,
                    continued,
                    { openDetails(Provider.STREAMING_COMMUNITY, it) },
                    { page = DesktopPage.SEARCH })

                DesktopPage.SEARCH -> SearchPage(
                    selectedProvider,
                    query,
                    results,
                    status,
                    onProvider = { selectedProvider = it },
                    onQuery = { query = it },
                    onSearch = {
                        scope.launch {
                            status = "Ricerca in corso…"; results =
                            runCatching { search(selectedProvider, query) }.onFailure {
                                status = "Ricerca non riuscita: ${it.message}"
                            }.getOrDefault(emptyList()); status = "${results.size} risultati"
                        }
                    },
                    onOpen = { openDetails(selectedProvider, it) },
                    onFavorite = { item ->
                        DesktopLibraryStore.toggleFavorite(
                            LibraryEntry(
                                selectedProvider.label,
                                item
                            )
                        ); favorites = DesktopLibraryStore.favorites()
                    },
                    isFavorite = { item -> favorites.any { it.provider == selectedProvider.label && it.item.id == item.id } })

                DesktopPage.DETAILS -> selectedItem?.let { item ->
                    DetailsPage(
                        item,
                        selectedItemProvider,
                        seasons,
                        episodes,
                        selectedSeason,
                        favorites.any { it.provider == selectedItemProvider.label && it.item.id == item.id },
                        onBack = { page = DesktopPage.HOME },
                        onFavorite = {
                            DesktopLibraryStore.toggleFavorite(
                                LibraryEntry(
                                    selectedItemProvider.label,
                                    item
                                )
                            ); favorites = DesktopLibraryStore.favorites()
                        },
                        onSeason = { season ->
                            scope.launch {
                                selectedSeason = season; episodes =
                                loadEpisodes(selectedItemProvider, item, season); status =
                                "${episodes.size} episodi"
                            }
                        },
                        onPlayMovie = { play(LibraryEntry(selectedItemProvider.label, item)) },
                        onPlayEpisode = { episode ->
                            play(
                                LibraryEntry(
                                    selectedItemProvider.label,
                                    item,
                                    selectedSeason?.number,
                                    episode
                                )
                            )
                        },
                        onCopy = { entry ->
                            scope.launch {
                                status = "Recupero link..."
                                val url = resolve(entry)
                                copyToClipboard(url)
                                status = "Link copiato!"
                            }
                        },
                        onCopySeason = { season ->
                            scope.launch {
                                status = "Recupero link stagione..."
                                val builder = StringBuilder()
                                episodes.forEach { ep ->
                                    val entry = LibraryEntry(selectedItemProvider.label, item, season.number, ep)
                                    val url = runCatching { resolve(entry) }.getOrNull()
                                    builder.append("[Stagione ${season.number} Episodio ${ep.number}] [${ep.name}]\n")
                                    builder.append("${url ?: "Link non disponibile"}\n\n")
                                }
                                copyToClipboard(builder.toString().trim())
                                status = "Link stagione copiati!"
                            }
                        }
                    )
                }

                DesktopPage.CONTINUE -> LibraryPage(
                    "Continua a guardare",
                    continued,
                    { entry -> openDetails(Provider.from(entry.provider), entry.item) },
                    { entry -> play(entry) },
                    { entry ->
                        DesktopLibraryStore.removeContinue(entry); continued =
                        DesktopLibraryStore.continueWatching()
                    })

                DesktopPage.FAVORITES -> LibraryPage(
                    "Preferiti",
                    favorites,
                    { entry -> openDetails(Provider.from(entry.provider), entry.item) },
                    null,
                    { entry ->
                        DesktopLibraryStore.toggleFavorite(entry); favorites =
                        DesktopLibraryStore.favorites()
                    })

                DesktopPage.SETTINGS -> SettingsPage(status) {
                    scope.launch {
                        status =
                            if (DomainManager.refreshDomains()) "Domini provider ripristinati." else "Aggiornamento non riuscito."
                    }
                }

                else -> Unit
            }
        }
    }
    }
}

@Composable
private fun Navigation(active: DesktopPage, onSelect: (DesktopPage) -> Unit) = Column(
    modifier = Modifier.width(190.dp).fillMaxHeight().background(Color(0xFF181C25)).padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
) {
    Logo(); Text(
    "STREAMFORGE",
    color = Color(0xFFFF7900),
    style = MaterialTheme.typography.titleLarge
)
    NavButton("Home", Icons.Default.Home, active == DesktopPage.HOME) { onSelect(DesktopPage.HOME) }
    NavButton(
        "Ricerca",
        Icons.Default.Search,
        active == DesktopPage.SEARCH
    ) { onSelect(DesktopPage.SEARCH) }
    NavButton("Continua", Icons.Default.PlayArrow, active == DesktopPage.CONTINUE) {
        onSelect(
            DesktopPage.CONTINUE
        )
    }
    NavButton("Preferiti", Icons.Default.Favorite, active == DesktopPage.FAVORITES) {
        onSelect(
            DesktopPage.FAVORITES
        )
    }
    NavButton("Impostazioni", Icons.Default.Settings, active == DesktopPage.SETTINGS) {
        onSelect(
            DesktopPage.SETTINGS
        )
    }
}

@Composable
private fun NavButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    click: () -> Unit
) = Button(
    onClick = click,
    modifier = Modifier.fillMaxWidth(),
    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
        containerColor = if (active) Color(0xFF2A303B) else Color.Transparent,
        contentColor = if (active) Color(0xFFFF7900) else Color(0xF3, 0xF5, 0xF8)
    )
) {
    Icon(
        icon,
        null
    ); Text("  $text")
}

@Composable
private fun Logo() {
    val bitmap =
        useResource("streamforge-logo.png") { ImageIO.read(it).toComposeImageBitmap() }; Image(
        bitmap,
        "Logo StreamForge",
        Modifier.size(88.dp),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun HomePage(
    movies: List<MediaItem>,
    series: List<MediaItem>,
    continued: List<LibraryEntry>,
    open: (MediaItem) -> Unit,
    search: () -> Unit
) = LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
    item {
        Text(
            "Home",
            style = MaterialTheme.typography.headlineLarge
        ); Text("Scopri film e serie, riprendi ciò che stavi guardando."); Button(onClick = search) {
        Text(
            "Cerca contenuti"
        )
    }
    }
    if (continued.isNotEmpty()) item {
        MediaRow(
            "Continua a guardare",
            continued.map { it.item },
            open
        )
    }
    item { MediaRow("Film popolari", movies, open) }; item {
    MediaRow(
        "Serie popolari",
        series,
        open
    )
}
}

@Composable
private fun MediaRow(title: String, media: List<MediaItem>, open: (MediaItem) -> Unit) =
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge
        ); LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(
            media,
            key = { it.id }) { MediaCard(it, { open(it) }) }
    }
    }

@Composable
private fun MediaCard(item: MediaItem, open: () -> Unit) = Card(
    modifier = Modifier.width(160.dp).height(280.dp).clickable(onClick = open),
    colors = androidx.compose.material3.CardDefaults.cardColors(
        containerColor = Color(0x18, 0x1C, 0x25)
    )
) {
    Column {
        RemotePoster(item.posterUrl, Modifier.fillMaxWidth().height(220.dp))
        Column(Modifier.padding(8.dp)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                item.year.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun SearchPage(
    provider: Provider,
    query: String,
    results: List<MediaItem>,
    status: String,
    onProvider: (Provider) -> Unit,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onFavorite: (MediaItem) -> Unit,
    isFavorite: (MediaItem) -> Boolean
) = Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Text("Ricerca", style = MaterialTheme.typography.headlineLarge);
    var menu by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { menu = true }) { Text(provider.label) }; DropdownMenu(
        menu,
        { menu = false }) {
        Provider.entries.forEach { candidate ->
            DropdownMenuItem(
                { Text(candidate.label) },
                { onProvider(candidate); menu = false })
        }
    }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            query,
            onQuery,
            label = { Text("Titolo") },
            modifier = Modifier.weight(1f)
        ); Button(onClick = onSearch, enabled = query.isNotBlank()) { Text("Cerca") }
    }
    Text(status, color = Color.LightGray)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(results, key = { it.id }) { item ->
            MediaCard(item, { onOpen(item) })
        }
    }
}

@Composable
private fun DetailsPage(
    item: MediaItem,
    provider: Provider,
    seasons: List<Season>,
    episodes: List<Episode>,
    selectedSeason: Season?,
    favorite: Boolean,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onSeason: (Season) -> Unit,
    onPlayMovie: () -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onCopy: (LibraryEntry) -> Unit,
    onCopySeason: (Season) -> Unit
) = LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
    item {
        Button(onClick = onBack) { Text("← Indietro") }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            RemotePoster(
                item.posterUrl,
                Modifier.width(260.dp).height(380.dp)
            )
            Column(
                Modifier.padding(start = 24.dp).weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White
                )
                Text(
                    "${provider.label} • ${item.type.uppercase()} • ${item.year.orEmpty()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFFF7900)
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onFavorite,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0x29, 0x31, 0x3E)
                        )
                    ) {
                        Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = Color.Red)
                        Text(if (favorite) "  Nei preferiti" else "  Aggiungi ai preferiti", color = Color.White)
                    }
                    
                    if (item.isMovie) {
                        Button(onClick = onPlayMovie) {
                            Icon(Icons.Default.PlayArrow, null)
                            Text("  Riproduci", color = Color.White)
                        }
                        Button(
                            onClick = { onCopy(LibraryEntry(provider.label, item)) },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0x29, 0x31, 0x3E)
                            )
                        ) {
                            Icon(Icons.Default.ContentCopy, null)
                            Text("  Copia Link m3u8", color = Color.White)
                        }
                    }
                }
            }
        }
    }
    
    if (!item.isMovie) {
        item {
            Text(
                "Episodi",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            if (seasons.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    items(seasons) { season ->
                        Button(
                            onClick = { onSeason(season) },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = if (selectedSeason == season) Color(0xFFFF7900) else Color(0x29, 0x31, 0x3E)
                            )
                        ) { Text(season.name, color = Color.White) }
                    }
                }
            }
            
            if (selectedSeason != null) {
                Button(
                    onClick = { onCopySeason(selectedSeason) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0x29, 0x31, 0x3E)
                    )
                ) {
                    Icon(Icons.Default.ContentCopy, null)
                    Text("  Copia tutti i link della stagione", color = Color.White)
                }
            }
        }
        
        if (selectedSeason != null) {
            items(episodes, key = { it.id }) { episode ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onPlayEpisode(episode) },
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = Color(0x18, 0x1C, 0x25)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "E${episode.number} · ${episode.name}",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                        androidx.compose.material3.IconButton(
                            onClick = { onCopy(LibraryEntry(provider.label, item, selectedSeason.number, episode)) }
                        ) {
                            Icon(Icons.Default.ContentCopy, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryPage(
    title: String,
    entries: List<LibraryEntry>,
    open: (LibraryEntry) -> Unit,
    resume: ((LibraryEntry) -> Unit)?,
    remove: (LibraryEntry) -> Unit
) = Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
    Text(
        title,
        style = MaterialTheme.typography.headlineLarge,
        color = Color.White
    )
    if (entries.isEmpty()) {
        Text("Nessun elemento salvato.", color = Color.Gray)
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(entries, key = { "${it.provider}-${it.item.id}" }) { entry ->
                Box {
                    MediaCard(entry.item, { open(entry) })
                    androidx.compose.material3.IconButton(
                        onClick = { remove(entry) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(status: String, refresh: () -> Unit) =
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Impostazioni",
            style = MaterialTheme.typography.headlineLarge
        ); Text("Aggiorna i domini predefiniti dei provider quando un indirizzo non risponde."); Button(
        onClick = refresh
    ) { Text("Aggiorna domini provider") }; Text(status, color = Color.LightGray)
    }

/** Downloads poster images off the UI thread; a neutral card is shown when an image is unavailable. */
@Composable
private fun RemotePoster(url: String?, modifier: Modifier) {
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }; LaunchedEffect(url) {
        image = url?.let {
            runCatching {
                withContext(Dispatchers.IO) {
                    val request =
                        Request.Builder().url(it).header("User-Agent", HttpClient.USER_AGENT)
                            .build(); HttpClient.client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.byteStream()?.use(ImageIO::read)
                        ?.toComposeImageBitmap() else null
                }
                }
            }.getOrNull()
        }
    }; Box(
        modifier.background(Color(0xFF2A303B)),
        contentAlignment = Alignment.Center
    ) {
        image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Text("STREAMFORGE", color = Color(0xFFFF7900))
    }
}

private fun displayTitle(entry: LibraryEntry) =
    entry.episode?.let { "${entry.item.name} · E${it.number}" } ?: entry.item.name

private suspend fun search(provider: Provider, query: String): List<MediaItem> = when (provider) {
    Provider.STREAMING_COMMUNITY -> Scraper.searchStreamingCommunity(query); Provider.ANIME_UNITY -> Scraper.searchAnimeUnity(
        query
    ); Provider.ANIME_WORLD -> Scraper.searchAnimeWorld(query); Provider.EURO_STREAMING -> Scraper.searchEuroStreaming(
        query
    ); Provider.CINEZO -> Scraper.searchCinezo(query)
}

private suspend fun loadSeasons(provider: Provider, item: MediaItem): List<Season> =
    when (provider) {
        Provider.STREAMING_COMMUNITY -> Scraper.getStreamingCommunitySeasons(item); Provider.EURO_STREAMING -> Scraper.getEuroStreamingSeasons(
        item
    ); Provider.CINEZO -> Scraper.getCinezoSeasons(item); else -> emptyList()
    }

private suspend fun loadEpisodes(
    provider: Provider,
    item: MediaItem,
    season: Season
): List<Episode> = when (provider) {
    Provider.STREAMING_COMMUNITY -> Scraper.getStreamingCommunityEpisodes(
        item,
        season.number
    ); else -> season.episodes
}

private suspend fun resolve(entry: LibraryEntry): String = when (Provider.from(entry.provider)) {
    Provider.STREAMING_COMMUNITY -> Scraper.extractStreamingCommunityUrl(
        entry.item,
        entry.episode?.id
    ); Provider.ANIME_UNITY -> Scraper.extractAnimeUnityUrl(
        entry.episode?.id ?: entry.item.id
    ); Provider.ANIME_WORLD -> Scraper.extractAnimeWorldUrl(
        entry.item,
        entry.episode?.id
    ); Provider.EURO_STREAMING -> Scraper.extractEuroStreamingUrl(
        entry.item,
        entry.seasonNumber ?: 1,
        entry.episode?.number ?: 1
    ); Provider.CINEZO -> Scraper.extractCinezoUrl(
        entry.item,
        entry.seasonNumber,
        entry.episode?.number
    )
}

/** Media segments must receive the same origin context used while resolving their source URL. */
private fun playbackHeadersFor(provider: Provider): Map<String, String> {
    val domainKey = when (provider) {
        Provider.STREAMING_COMMUNITY -> "streamingcommunity"
        Provider.ANIME_UNITY -> "animeunity"
        Provider.ANIME_WORLD -> "animeworld"
        Provider.EURO_STREAMING -> "eurostreaming"
        Provider.CINEZO -> "cinezo"
    }
    val referer = runCatching { DomainManager.getUrl(domainKey) }.getOrNull()
    return buildMap {
        put("User-Agent", HttpClient.USER_AGENT)
        referer?.let { put("Referer", it) }
    }
}
