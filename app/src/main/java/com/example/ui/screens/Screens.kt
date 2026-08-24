@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.viewinterop.AndroidView

import coil.compose.AsyncImage
import com.example.R
import com.example.data.database.ContinueWatchingItem
import com.example.data.database.FavoriteItem
import com.example.data.model.Episode
import com.example.data.model.MediaItem
import com.example.data.model.Season
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ForgeGold
import com.example.ui.theme.ForgeOrange
import com.example.ui.theme.SteelGrey
import com.example.ui.viewmodel.MainViewModel

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.MimeTypes
import androidx.media3.common.MediaItem as MediaItem3
import androidx.media3.ui.PlayerView

/* ==========================================================================================
   APP NAVIGATOR & SCREEN DEFINITIONS
   ========================================================================================== */

private enum class Screen { SEARCH, DETAIL, PLAYER }
private enum class Tab { HOME, SEARCH, CONTINUE_WATCHING, FAVORITES, SETTINGS }

@androidx.media3.common.util.UnstableApi
@Composable
fun AppNavigator(viewModel: MainViewModel) {
    var currentScreen by remember { mutableStateOf(Screen.SEARCH) }
    var currentTab by remember { mutableStateOf(Tab.HOME) }

    BackHandler(enabled = currentScreen != Screen.SEARCH) {
        if (currentScreen == Screen.PLAYER) {
            currentScreen = Screen.DETAIL
        } else {
            currentScreen = Screen.SEARCH
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (currentScreen == Screen.SEARCH) {
                NavigationBar(
                    containerColor = DarkSurface,
                    contentColor = Color.White
                ) {
                    NavigationBarItem(
                        selected = currentTab == Tab.HOME,
                        onClick = { currentTab = Tab.HOME },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ForgeOrange,
                            selectedTextColor = ForgeOrange,
                            unselectedIconColor = SteelGrey,
                            unselectedTextColor = SteelGrey,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == Tab.SEARCH,
                        onClick = { currentTab = Tab.SEARCH },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        label = { Text("Cerca", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ForgeOrange,
                            selectedTextColor = ForgeOrange,
                            unselectedIconColor = SteelGrey,
                            unselectedTextColor = SteelGrey,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == Tab.CONTINUE_WATCHING,
                        onClick = { currentTab = Tab.CONTINUE_WATCHING },
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Continue") },
                        label = { Text("Continua", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ForgeOrange,
                            selectedTextColor = ForgeOrange,
                            unselectedIconColor = SteelGrey,
                            unselectedTextColor = SteelGrey,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == Tab.FAVORITES,
                        onClick = { currentTab = Tab.FAVORITES },
                        icon = { Icon(Icons.Default.Favorite, contentDescription = "Favorites") },
                        label = { Text("Preferiti", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ForgeOrange,
                            selectedTextColor = ForgeOrange,
                            unselectedIconColor = SteelGrey,
                            unselectedTextColor = SteelGrey,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == Tab.SETTINGS,
                        onClick = { currentTab = Tab.SETTINGS },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Impostazioni", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ForgeOrange,
                            selectedTextColor = ForgeOrange,
                            unselectedIconColor = SteelGrey,
                            unselectedTextColor = SteelGrey,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                Screen.SEARCH -> {
                    when (currentTab) {
                        Tab.HOME -> HomeScreen(viewModel, onNavigateToDetails = { currentScreen = Screen.DETAIL })
                        Tab.SEARCH -> SearchScreen(viewModel, onNavigateToDetails = { currentScreen = Screen.DETAIL })
                        Tab.CONTINUE_WATCHING -> ContinueWatchingTab(
                            viewModel,
                            onNavigateToDetails = { currentScreen = Screen.DETAIL },
                            onInstantPlayEpisode = { provider, item, season, ep ->
                                viewModel.setProvider(provider)
                                viewModel.selectMediaItem(item, season)
                                viewModel.playEpisode(item, season, ep)
                                currentScreen = Screen.PLAYER
                            },
                            onInstantPlayMovie = { provider, item ->
                                viewModel.setProvider(provider)
                                viewModel.selectMediaItem(item)
                                viewModel.playMovie(item)
                                currentScreen = Screen.PLAYER
                            }
                        )
                        Tab.FAVORITES -> FavoritesTab(viewModel, onNavigateToDetails = { currentScreen = Screen.DETAIL })
                        Tab.SETTINGS -> SettingsScreen(viewModel)
                    }
                }
                Screen.DETAIL -> DetailScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = Screen.SEARCH }
                )
                Screen.PLAYER -> PlayerScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = Screen.DETAIL }
                )
            }
        }
    }

    // Trigger player screen when a stream URL is ready
    val activeStreamUrl by viewModel.activeStreamUrl.collectAsStateWithLifecycle()
    LaunchedEffect(activeStreamUrl) {
        if (activeStreamUrl != null) {
            currentScreen = Screen.PLAYER
        }
    }
}

/* ==========================================================================================
   SETTINGS SCREEN
   ========================================================================================== */

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val providerLanguage by viewModel.providerLanguage.collectAsStateWithLifecycle()
    val tmdbApiKey by viewModel.tmdbApiKey.collectAsStateWithLifecycle()

    val languages = listOf(
        "it" to "Italiano",
        "en" to "English"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Impostazioni",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // UI Language Selection
        Text("Lingua Interfaccia", color = ForgeOrange, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            languages.forEach { (code, name) ->
                val isSelected = appLanguage == code
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setAppLanguage(code) },
                    label = { Text(name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ForgeOrange,
                        selectedLabelColor = Color.Black,
                        labelColor = Color.White,
                        containerColor = DarkSurface
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Provider Language Preference
        Text("Lingua Contenuti (Preferita)", color = ForgeOrange, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Determina la lingua dei risultati di ricerca quando possibile.", color = SteelGrey, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            languages.forEach { (code, name) ->
                val isSelected = providerLanguage == code
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setProviderLanguage(code) },
                    label = { Text(name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ForgeOrange,
                        selectedLabelColor = Color.Black,
                        labelColor = Color.White,
                        containerColor = DarkSurface
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // TMDB Configuration
        Text("TMDB API Key", color = ForgeOrange, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Utilizzata per recuperare locandine e dettagli. Chiave predefinita inclusa.", color = SteelGrey, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = tmdbApiKey,
            onValueChange = { /* Key is fixed for now */ },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            label = { Text("API Key Attiva") },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = SteelGrey,
                focusedBorderColor = ForgeOrange,
                unfocusedTextColor = Color.White,
                focusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { viewModel.refreshDomainsAndApi() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurface)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = ForgeOrange)
            Spacer(Modifier.width(8.dp))
            Text("Aggiorna Domini Provider", color = Color.White)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Versione 16.0 Multiplatform Beta",
            color = SteelGrey,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

/* ==========================================================================================
   HOME SCREEN
   ========================================================================================== */

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToDetails: () -> Unit
) {
    val trendingMovies by viewModel.homeTrendingMovies.collectAsStateWithLifecycle()
    val trendingSeries by viewModel.homeTrendingSeries.collectAsStateWithLifecycle()
    val continueWatchingList by viewModel.continueWatchingList.collectAsStateWithLifecycle()
    val actionMovies by viewModel.homeActionMovies.collectAsStateWithLifecycle()
    val comedyMovies by viewModel.homeComedyMovies.collectAsStateWithLifecycle()
    val homeError by viewModel.homeError.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadHomeData()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Hero Section
        item {
            val heroItem = trendingSeries.firstOrNull() ?: trendingMovies.firstOrNull()
            if (homeError != null) {
                Box(modifier = Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                    Text(homeError ?: "Errore", color = Color.Red, modifier = Modifier.padding(16.dp))
                }
            } else if (heroItem != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(450.dp)
                        .background(DarkSurface)
                        .clickable {
                            viewModel.selectMediaItem(heroItem)
                            onNavigateToDetails()
                        }
                ) {
                    val posterUrl = heroItem.posterUrl
                    if (posterUrl != null) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = "Hero Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xAA000000), DarkBackground),
                                    startY = 0f
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = heroItem.name,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = ForgeOrange)
                            Spacer(Modifier.width(4.dp))
                            Text("Guarda Ora", color = ForgeOrange, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ForgeOrange)
                }
            }
        }

        // Horizontal Rows
        if (continueWatchingList.isNotEmpty()) {
            item {
                ContinueWatchingRow(
                    "Continua a guardare",
                    continueWatchingList,
                    onPlay = { item ->
                        val mediaObj = MediaItem(item.mediaId, item.name, item.type, item.slug, item.posterUrl, item.year)
                        viewModel.setProvider(item.provider)
                        if (mediaObj.isMovie) {
                            viewModel.selectMediaItem(mediaObj)
                            viewModel.playMovie(mediaObj)
                        } else {
                            val epObj = Episode(item.lastEpisodeId ?: 0, item.lastEpisodeNumber ?: 1, item.lastEpisodeName ?: "Episodio")
                            viewModel.selectMediaItem(mediaObj, item.lastSeasonNumber ?: 1)
                            viewModel.playEpisode(mediaObj, item.lastSeasonNumber ?: 1, epObj)
                        }
                    },
                    onNavigateToDetails = { item ->
                        val mediaObj = MediaItem(item.mediaId, item.name, item.type, item.slug, item.posterUrl, item.year)
                        viewModel.selectMediaItem(mediaObj)
                        onNavigateToDetails()
                    }
                )
            }
        }
        item { HomeCarousel("Serie TV del Momento", trendingSeries, viewModel, onNavigateToDetails) }
        item { HomeCarousel("Film del Momento", trendingMovies, viewModel, onNavigateToDetails) }
        item { HomeCarousel("Azione", actionMovies, viewModel, onNavigateToDetails) }
        item { HomeCarousel("Commedia", comedyMovies, viewModel, onNavigateToDetails) }
    }
}

@Composable
fun ContinueWatchingRow(
    title: String,
    items: List<ContinueWatchingItem>,
    onPlay: (ContinueWatchingItem) -> Unit,
    onNavigateToDetails: (ContinueWatchingItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item ->
                Card(
                    modifier = Modifier
                        .width(260.dp)
                        .height(160.dp)
                        .clickable { onNavigateToDetails(item) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val posterUrl = item.posterUrl
                        if (posterUrl != null) {
                            AsyncImage(
                                model = posterUrl,
                                contentDescription = item.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        
                        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))

                        if (item.durationMillis != null && item.durationMillis > 0) {
                            val progress = (item.lastPositionMillis ?: 0L).toFloat() / item.durationMillis.toFloat()
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                                color = ForgeOrange,
                                trackColor = Color.White.copy(alpha = 0.2f)
                            )
                        }

                        Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                            Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                            if (item.lastEpisodeNumber != null) {
                                Text("S${item.lastSeasonNumber}:E${item.lastEpisodeNumber}", color = ForgeOrange, fontSize = 11.sp)
                            }
                        }

                        IconButton(
                            onClick = { onPlay(item) },
                            modifier = Modifier.align(Alignment.Center).size(48.dp).clip(CircleShape).background(ForgeOrange)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeCarousel(
    title: String,
    items: List<MediaItem>,
    viewModel: MainViewModel,
    onNavigateToDetails: () -> Unit
) {
    if (items.isEmpty()) return
    
    val continueWatchingList by viewModel.continueWatchingList.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item ->
                val progressEntry = continueWatchingList.find { it.mediaId == item.id }
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .width(130.dp)
                        .height(195.dp)
                        .clickable {
                            viewModel.selectMediaItem(item)
                            onNavigateToDetails()
                        }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = item.name,
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        val posterUrl = item.posterUrl
                        if (posterUrl != null) {
                            AsyncImage(
                                model = posterUrl,
                                contentDescription = "Poster of ${item.name}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        
                        if (progressEntry != null && progressEntry.durationMillis != null && progressEntry.durationMillis > 0) {
                            val progress = progressEntry.lastPositionMillis!!.toFloat() / progressEntry.durationMillis.toFloat()
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                                color = ForgeOrange,
                                trackColor = Color.White.copy(alpha = 0.2f)
                            )
                            
                            val remaining = progressEntry.durationMillis - (progressEntry.lastPositionMillis ?: 0L)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "-${formatMillis(remaining)}",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ==========================================================================================
   CONTINUE WATCHING TAB
   ========================================================================================== */

@Composable
fun ContinueWatchingTab(
    viewModel: MainViewModel,
    onNavigateToDetails: () -> Unit,
    onInstantPlayEpisode: (String, MediaItem, Int, Episode) -> Unit,
    onInstantPlayMovie: (String, MediaItem) -> Unit
) {
    val continueWatchingList by viewModel.continueWatchingList.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(top = 42.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Continua a Guardare",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    ) { innerPadding ->
        if (continueWatchingList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = SteelGrey.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Nessun contenuto in sospeso",
                        color = SteelGrey,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(continueWatchingList, key = { it.id }) { item ->
                    ContinueWatchingCard(
                        item = item,
                        onPlay = {
                            val mediaObj = MediaItem(item.mediaId, item.name, item.type, item.slug, item.posterUrl, item.year)
                            if (mediaObj.isMovie) {
                                onInstantPlayMovie(item.provider, mediaObj)
                            } else {
                                val epObj = Episode(item.lastEpisodeId ?: 0, item.lastEpisodeNumber ?: 1, item.lastEpisodeName ?: "Episodio")
                                onInstantPlayEpisode(item.provider, mediaObj, item.lastSeasonNumber ?: 1, epObj)
                            }
                        },
                        onDelete = {
                            viewModel.deleteContinueWatchingItem(item.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FavoritesTab(
    viewModel: MainViewModel,
    onNavigateToDetails: () -> Unit
) {
    val favoritesList by viewModel.favoritesList.collectAsStateWithLifecycle()
    val continueWatchingList by viewModel.continueWatchingList.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(top = 42.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "I Miei Preferiti",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    ) { innerPadding ->
        if (favoritesList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = SteelGrey.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Ancora nessun preferito",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Aggiungi i tuoi titoli preferiti premendo il cuore nella pagina dei dettagli!",
                        color = SteelGrey,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(favoritesList, key = { it.id }) { item ->
                    val progressEntry = continueWatchingList.find { it.mediaId == item.mediaId }
                    FavoriteGridCard(
                        item = item,
                        progress = if (progressEntry != null && progressEntry.durationMillis != null && progressEntry.durationMillis > 0) {
                            progressEntry.lastPositionMillis!!.toFloat() / progressEntry.durationMillis.toFloat()
                        } else null,
                        onClick = {
                            val mediaObj = MediaItem(item.mediaId, item.name, item.type, item.slug, item.posterUrl, item.year)
                            viewModel.selectMediaItem(mediaObj)
                            onNavigateToDetails()
                        },
                        onRemove = {
                            val mediaObj = MediaItem(item.mediaId, item.name, item.type, item.slug, item.posterUrl, item.year)
                            viewModel.selectMediaItem(mediaObj)
                            viewModel.toggleFavorite()
                            viewModel.selectMediaItem(null)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FavoriteGridCard(
    item: FavoriteItem,
    progress: Float? = null,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("favorite_card_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                val posterUrl = item.posterUrl
                if (posterUrl != null) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Gray.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ForgeOrange)
                    }
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Rimuovi dai preferiti",
                        tint = Color.Red,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ForgeOrange)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.provider.take(4).uppercase(),
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter),
                        color = ForgeOrange,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 12.dp, end = 8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.type.uppercase()} • ${item.year ?: "N/D"}",
                    color = SteelGrey,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/* ==========================================================================================
   SEARCH SCREEN
   ========================================================================================== */

@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onNavigateToDetails: () -> Unit
) {
    val providerName by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val continueWatchingList by viewModel.continueWatchingList.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val searchError by viewModel.searchError.collectAsStateWithLifecycle()
    val isBootstrapping by viewModel.isBootstrapping.collectAsStateWithLifecycle()

    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(top = 42.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.streamforge_logo),
                        contentDescription = "StreamForge Logo",
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .border(2.dp, ForgeOrange, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "StreamForge",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                var dropdownExpanded by remember { mutableStateOf(false) }
                val allProviders = listOf(
                    "StreamingCommunity", "AnimeUnity", "DiscoveryPlus", "Discovery", "DMax", "Nove", "RealTime",
                    "MediasetInfinity", "RaiPlay", "HomeGardenTV", "FoodNetwork", "AnimeWorld", "Crunchyroll",
                    "PrimeVideo", "TubiTV", "Cinezo", "MostraGuarda", "EuroStreaming"
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { dropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkBackground, contentColor = Color.White),
                        border = BorderStroke(1.dp, ForgeOrange),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Provider: ${providerName.replaceFirstChar { it.uppercase() }}", color = ForgeOrange, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Scegli Provider", tint = ForgeOrange)
                    }
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.92f).background(DarkSurface).heightIn(max = 400.dp)
                    ) {
                        allProviders.forEach { p ->
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        p, 
                                        color = if (p.equals(providerName, ignoreCase = true)) ForgeOrange else Color.White,
                                        fontWeight = if (p.equals(providerName, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal
                                    ) 
                                },
                                onClick = {
                                    viewModel.setProvider(p)
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (isBootstrapping) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ForgeOrange)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { viewModel.setQuery(it) },
                        placeholder = { Text("Cerca film o serie TV...", color = SteelGrey) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .testTag("search_input"),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon", tint = ForgeOrange) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear text", tint = SteelGrey)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ForgeOrange,
                            unfocusedBorderColor = SteelGrey,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboardController?.hide()
                            viewModel.search()
                        })
                    )
                }

                item {
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.search()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(bottom = 16.dp)
                            .testTag("search_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ForgeOrange)
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Cerca su $providerName", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }

                if (searchError != null && !isSearching) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0x33F44336)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = searchError ?: "",
                                color = Color.White,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                if (!isSearching && results.isNotEmpty()) {
                    items(results) { mediaItem ->
                        val progressEntry = continueWatchingList.find { it.mediaId == mediaItem.id }
                        MediaItemSearchRow(
                            item = mediaItem,
                            progress = if (progressEntry != null && progressEntry.durationMillis != null && progressEntry.durationMillis > 0) {
                                progressEntry.lastPositionMillis!!.toFloat() / progressEntry.durationMillis.toFloat()
                            } else null,
                            onClick = {
                                viewModel.selectMediaItem(mediaItem)
                                onNavigateToDetails()
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                } else if (!isSearching && results.isEmpty() && query.isNotBlank()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.SearchOff,
                                    contentDescription = null,
                                    tint = SteelGrey.copy(alpha = 0.5f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Nessun risultato per '$query'",
                                    color = SteelGrey,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, ForgeOrange.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Box(modifier = Modifier.height(120.dp).fillMaxWidth()) {
            val posterUrl = item.posterUrl
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ForgeOrange)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp)
                    .align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ForgeOrange)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.provider.take(4).uppercase(),
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(14.dp))
                }
            }

            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(ForgeOrange)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black)
            }

            if (item.lastPositionMillis != null && item.durationMillis != null && item.durationMillis > 0) {
                val progress = item.lastPositionMillis.toFloat() / item.durationMillis.toFloat()
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomStart),
                    color = ForgeOrange,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(
                text = item.name,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            if (item.lastEpisodeNumber != null) {
                Text(
                    text = "S${item.lastSeasonNumber ?: 1}:E${item.lastEpisodeNumber} - ${item.lastEpisodeName}",
                    fontSize = 11.sp,
                    color = ForgeOrange,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "Film completo",
                    fontSize = 11.sp,
                    color = SteelGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (item.lastPositionMillis != null && item.durationMillis != null && item.durationMillis > 0) {
                val elapsed = formatMillis(item.lastPositionMillis)
                val remaining = formatMillis(item.durationMillis - item.lastPositionMillis)
                Text(
                    text = "Visto $elapsed - Mancano $remaining",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

fun formatMillis(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Composable
fun MediaItemSearchRow(
    item: MediaItem,
    progress: Float? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("media_row_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        ) {
            Box(modifier = Modifier.width(80.dp).height(115.dp).clip(RoundedCornerShape(8.dp))) {
                val posterUrl = item.posterUrl
                if (posterUrl != null) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Gray.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ForgeOrange)
                    }
                }

                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter),
                        color = ForgeOrange,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (progress != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            color = ForgeOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ForgeOrange.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.type.uppercase(),
                            color = ForgeOrange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }

                    val itemYear = item.year
                    if (!itemYear.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = itemYear,
                            color = SteelGrey,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = item.providerLanguage.uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = ForgeGold,
                        fontSize = 11.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Details",
                tint = ForgeOrange,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(end = 8.dp)
            )
        }
    }
}


/* ==========================================================================================
   DETAIL SCREEN
   ========================================================================================== */

@Composable
fun DetailScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val currentContext = LocalContext.current
    val providerName by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val selectedItem by viewModel.selectedMediaItem.collectAsStateWithLifecycle()
    val seasons by viewModel.seasons.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val selectedSeason by viewModel.selectedSeasonNumber.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingDetails.collectAsStateWithLifecycle()
    val detailsError by viewModel.detailsError.collectAsStateWithLifecycle()
    val continueWatchingList by viewModel.continueWatchingList.collectAsStateWithLifecycle()

    val isExtracting by viewModel.isExtractingStream.collectAsStateWithLifecycle()
    val streamError by viewModel.streamError.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isCurrentMediaFavorite.collectAsStateWithLifecycle()

    val item = selectedItem ?: return

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = { Text(text = "Dettagli Titolo", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = ForgeOrange)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Preferiti",
                            tint = if (isFavorite) Color.Red else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    val posterUrl = item.posterUrl
                    if (posterUrl != null) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(130.dp)
                                .height(190.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, ForgeOrange, RoundedCornerShape(12.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .width(130.dp)
                                .height(190.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Gray.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ForgeOrange, modifier = Modifier.size(48.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier.height(190.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = item.name,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ForgeOrange)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = item.type.uppercase(),
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Column {
                            val itemYear = item.year
                            if (!itemYear.isNullOrBlank()) {
                                Text(
                                    text = "Anno di rilascio: $itemYear",
                                    color = SteelGrey,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                text = "Provider: $providerName",
                                color = SteelGrey,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            if (item.isMovie) {
                item {
                    Button(
                        onClick = { viewModel.playMovie(item) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ForgeOrange),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isExtracting
                    ) {
                        if (isExtracting) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("RIPRODUCI ORA", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                    
                    if (streamError != null) {
                        Text(
                            text = streamError ?: "",
                            color = Color.Red,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
            } else {
                this@LazyColumn.item {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ForgeOrange)
                        }
                    } else if (detailsError != null) {
                        Text(text = detailsError ?: "Errore", color = Color.Red)
                    } else if (seasons.isNotEmpty()) {
                        var expanded by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                Button(
                                    onClick = { expanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "Stagione ${selectedSeason ?: 1}", color = Color.White)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = ForgeOrange)
                                    }
                                }

                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .background(DarkSurface)
                                ) {
                                    seasons.forEach { season ->
                                        DropdownMenuItem(
                                            text = { Text("Stagione ${season.number}", color = Color.White) },
                                            onClick = {
                                                expanded = false
                                                viewModel.selectSeason(season.number)
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { viewModel.copySeasonStreams(item, selectedSeason ?: 1, episodes, currentContext) },
                                enabled = !isExtracting,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurface)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = ForgeOrange)
                                }
                        }
                    }
                }

                this@LazyColumn.item {
                    Text(
                        text = "Lista Episodi (${episodes.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForgeOrange,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                if (episodes.isEmpty()) {
                    this@LazyColumn.item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Nessun episodio caricato.", color = SteelGrey)
                        }
                    }
                } else {
                    this@LazyColumn.items(episodes) { episode ->
                        val progressEntry = continueWatchingList.find { it.mediaId == item.id && it.lastEpisodeId == episode.id }
                        EpisodeRow(
                            episode = episode,
                            progress = if (progressEntry != null && progressEntry.durationMillis != null && progressEntry.durationMillis > 0) {
                                progressEntry.lastPositionMillis!!.toFloat() / progressEntry.durationMillis.toFloat()
                            } else null,
                            onClick = {
                                viewModel.playEpisode(item, selectedSeason ?: 1, episode)
                            },
                            onCopyClick = {
                                viewModel.copySingleStream(item, selectedSeason ?: 1, episode, currentContext)
                            },
                            enabled = !isExtracting
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeRow(
    episode: Episode,
    progress: Float? = null,
    onClick: () -> Unit,
    onCopyClick: () -> Unit = {},
    enabled: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .testTag("episode_row_${episode.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ForgeOrange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = episode.number.toString(),
                            color = ForgeOrange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.align(Alignment.CenterVertically)) {
                        Text(
                            text = episode.name,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (progress != null) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            color = ForgeOrange,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    IconButton(onClick = onCopyClick) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copia", tint = SteelGrey, modifier = Modifier.size(20.dp))
                    }
                }
            }
            
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = ForgeOrange,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}


/* ==========================================================================================
   FULLSCREEN PLAYER SCREEN
   ========================================================================================== */

@androidx.media3.common.util.UnstableApi
@Composable
fun PlayerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val provider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val streamUrl by viewModel.activeStreamUrl.collectAsStateWithLifecycle()
    val hasNextEpisode by viewModel.hasNextEpisode.collectAsStateWithLifecycle()
    val resumePosition by viewModel.playbackResumePosition.collectAsStateWithLifecycle()
    val currentEpisode by viewModel.currentPlayingEpisode.collectAsStateWithLifecycle()
    val currentSeason by viewModel.currentPlayingSeason.collectAsStateWithLifecycle()

    val currentStreamUrl = streamUrl ?: ""
    var isControllerVisible by remember { mutableStateOf(true) }
    var showMirrorOptions by remember { mutableStateOf(false) }
    var showSubtitlesMenu by remember { mutableStateOf(false) }
    var showAudioLanguageMenu by remember { mutableStateOf(false) }
    var showExtraControls by remember { mutableStateOf(false) }
    var isZoomed by remember { mutableStateOf(false) }
    
    val appLang by viewModel.appLanguage.collectAsStateWithLifecycle()
    val subLang by viewModel.subtitleLanguage.collectAsStateWithLifecycle()
    val activeItem by viewModel.selectedMediaItem.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isCurrentMediaFavorite.collectAsStateWithLifecycle()
    val audioLang = activeItem?.providerLanguage ?: "it"
    val supportsAudioLanguageSwitch = provider == "StreamingCommunity"

    val subtitleOptions = listOf("off" to "Disattivati") + listOf(
        "it" to "Italiano", "en" to "English", "es" to "Español", "fr" to "Français",
        "de" to "Deutsch", "pt" to "Português", "ru" to "Русский", "ja" to "日本語", "ko" to "한국어", "zh" to "中文"
    )
    val audioLanguageOptions = listOf("it" to "Italiano", "en" to "English")

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val window = activity?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        var controller: WindowInsetsControllerCompat? = null
        if (window != null) {
            controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val exoPlayer = remember(provider) {
        val customReferer = if (provider.lowercase().contains("community")) "https://vixcloud.co/" else "https://www.animeunity.so/"
        val httpDataSourceFactory = DefaultHttpDataSource.Factory().apply {
            setDefaultRequestProperties(mapOf("User-Agent" to com.example.data.network.HttpClient.USER_AGENT, "Referer" to customReferer))
        }
        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context)
        trackSelector.parameters = trackSelector.buildUponParameters().setPreferredAudioLanguage(appLang).setPreferredTextLanguage(subLang).build()

        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory)).setTrackSelector(trackSelector).build()
    }

    LaunchedEffect(subLang) {
        val params = exoPlayer.trackSelectionParameters.buildUpon()
        if (subLang == "off") {
            params.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
        } else {
            params.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
            params.setPreferredTextLanguage(subLang)
        }
        exoPlayer.trackSelectionParameters = params.build()
    }

    LaunchedEffect(currentStreamUrl) {
        if (currentStreamUrl.isNotEmpty()) {
            val uri = Uri.parse(currentStreamUrl)
            val hls = currentStreamUrl.contains(".m3u8") || currentStreamUrl.contains("vixcloud")
            val mediaItem = if (hls) {
                MediaItem3.Builder().setUri(uri).setMimeType(MimeTypes.APPLICATION_M3U8).build()
            } else {
                MediaItem3.fromUri(uri)
            }
            exoPlayer.setMediaItem(mediaItem)
            if (resumePosition > 0) exoPlayer.seekTo(resumePosition) else exoPlayer.seekTo(0L)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.stop()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            try {
                if (exoPlayer.isPlaying) {
                    val currentPos = exoPlayer.currentPosition
                    val duration = exoPlayer.duration
                    if (currentPos > 0 && duration > 0) viewModel.updatePlaybackPosition(currentPos, duration)
                }
            } catch (e: Exception) {}
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            try {
                val currentPos = exoPlayer.currentPosition
                val duration = exoPlayer.duration
                if (currentPos > 0 && duration > 0) viewModel.updatePlaybackPosition(currentPos, duration)
            } catch (e: Exception) {}
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).testTag("player_screen_root")) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        isControllerVisible = (visibility == android.view.View.VISIBLE)
                    })
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = { playerView ->
                if (playerView.player != exoPlayer) playerView.player = exoPlayer
                playerView.resizeMode = if (isZoomed) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(visible = isControllerVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopStart)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        try {
                            val currentPos = exoPlayer.currentPosition
                            val duration = exoPlayer.duration
                            if (currentPos > 0 && duration > 0) viewModel.updatePlaybackPosition(currentPos, duration)
                        } catch (e: Exception) {}
                        onBack()
                    },
                    modifier = Modifier.padding(top = 16.dp, start = 16.dp).size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Chiudi", tint = Color.White)
                }
                
                activeItem?.let { item ->
                    Column(modifier = Modifier.padding(top = 16.dp, start = 12.dp)) {
                        Text(text = item.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp))
                        currentEpisode?.let { ep ->
                            Text(text = "S${currentSeason ?: 1}:E${ep.number} - ${ep.name}", color = ForgeOrange, fontSize = 12.sp, modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp))
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = isControllerVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopEnd)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp, end = 16.dp)) {
                IconButton(
                    onClick = { showExtraControls = !showExtraControls },
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(if (showExtraControls) ForgeOrange else Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(imageVector = if (showExtraControls) Icons.Default.Close else Icons.Default.Settings, contentDescription = "Menu", tint = if (showExtraControls) Color.Black else Color.White)
                }

                AnimatedVisibility(visible = showExtraControls, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { viewModel.toggleFavorite() }, modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))) {
                            Icon(imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Preferiti", tint = if (isFavorite) Color.Red else Color.White)
                        }

                        IconButton(onClick = { isZoomed = !isZoomed }, modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))) {
                            Icon(imageVector = if (isZoomed) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, contentDescription = "Zoom", tint = Color.White)
                        }

                        if (supportsAudioLanguageSwitch) {
                            Box {
                                IconButton(onClick = { showAudioLanguageMenu = true }, modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))) {
                                    Icon(imageVector = Icons.Default.Language, contentDescription = "Lingua Audio", tint = Color.White)
                                }

                                androidx.compose.material3.DropdownMenu(expanded = showAudioLanguageMenu, onDismissRequest = { showAudioLanguageMenu = false }, modifier = Modifier.background(Color(0xFF2C2C2C))) {
                                    audioLanguageOptions.forEach { (code, name) ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Row(verticalAlignment = Alignment.CenterVertically) { Text(name, color = Color.White); if (audioLang == code) { Spacer(modifier = Modifier.width(8.dp)); Icon(Icons.Default.Check, contentDescription = null, tint = ForgeOrange, modifier = Modifier.size(16.dp)) } } },
                                            onClick = { showAudioLanguageMenu = false; if (audioLang != code) viewModel.switchPlaybackLanguage(code, exoPlayer.currentPosition) }
                                        )
                                    }
                                }
                            }
                        }

                        Box {
                            IconButton(onClick = { showSubtitlesMenu = true }, modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))) {
                                Icon(imageVector = Icons.Default.Subtitles, contentDescription = "Sottotitoli", tint = Color.White)
                            }
                            
                            androidx.compose.material3.DropdownMenu(expanded = showSubtitlesMenu, onDismissRequest = { showSubtitlesMenu = false }, modifier = Modifier.background(Color(0xFF2C2C2C))) {
                                subtitleOptions.forEach { (code, name) ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Row(verticalAlignment = Alignment.CenterVertically) { Text(name, color = Color.White); if (subLang == code) { Spacer(modifier = Modifier.width(8.dp)); Icon(Icons.Default.Check, contentDescription = null, tint = ForgeOrange, modifier = Modifier.size(16.dp)) } } },
                                        onClick = { viewModel.setSubtitleLanguage(code); showSubtitlesMenu = false }
                                    )
                                }
                            }
                        }

                        Box {
                            IconButton(onClick = { showMirrorOptions = true }, modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))) {
                                Icon(imageVector = Icons.Default.Cast, contentDescription = "Cast", tint = Color.White)
                            }

                            androidx.compose.material3.DropdownMenu(expanded = showMirrorOptions, onDismissRequest = { showMirrorOptions = false }, modifier = Modifier.background(Color(0xFF2C2C2C))) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Mirroring Schermo", color = Color.White) },
                                    onClick = { showMirrorOptions = false; try { context.startActivity(android.content.Intent("android.settings.CAST_SETTINGS")) } catch (e: Exception) {} }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Condividi Link Web", color = Color.White) },
                                    onClick = {
                                        showMirrorOptions = false
                                        try {
                                            val encodedUrl = java.net.URLEncoder.encode(currentStreamUrl, "UTF-8")
                                            val webPlayerUrl = "https://www.m3u8player.online/m3u8?url=$encodedUrl"
                                            val sendIntent = android.content.Intent().apply { action = android.content.Intent.ACTION_SEND; putExtra(android.content.Intent.EXTRA_TEXT, webPlayerUrl); type = "text/plain" }
                                            context.startActivity(android.content.Intent.createChooser(sendIntent, "Trasmetti Player M3U8"))
                                        } catch (e: Exception) {}
                                    }
                                )
                            }
                        }

                        if (hasNextEpisode) {
                            Button(
                                onClick = { viewModel.playNextEpisode() },
                                colors = ButtonDefaults.buttonColors(containerColor = ForgeOrange),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier.height(44.dp).width(130.dp)
                            ) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("NEXT", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}
