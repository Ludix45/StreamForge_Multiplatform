@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem as MediaItem3
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import com.example.data.database.ContinueWatchingItem
import com.example.data.database.FavoriteItem
import com.example.data.model.Episode
import com.example.data.model.MediaItem
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ForgeGold
import com.example.ui.theme.ForgeOrange
import com.example.ui.theme.SteelGrey
import com.example.ui.viewmodel.MainViewModel

enum class Screen {
    SEARCH,
    DETAIL,
    PLAYER
}

enum class Tab {
    HOME,
    SEARCH,
    CONTINUE_WATCHING,
    FAVORITES,
    SETTINGS
}

@Composable
fun AppNavigator(viewModel: MainViewModel) {
    var currentScreen by remember { mutableStateOf(Screen.SEARCH) }
    var selectedTab by remember { mutableStateOf(Tab.HOME) } // Starts on Home (Prime Video style)

    val activeStreamUrl by viewModel.activeStreamUrl.collectAsStateWithLifecycle()
    val selectedItem by viewModel.selectedMediaItem.collectAsStateWithLifecycle()

    // Handle back button presses cleanly
    BackHandler(enabled = currentScreen != Screen.SEARCH) {
        when (currentScreen) {
            Screen.PLAYER -> {
                viewModel.clearPlayerState()
                currentScreen = if (selectedItem != null) Screen.DETAIL else Screen.SEARCH
            }
            Screen.DETAIL -> {
                viewModel.selectMediaItem(null)
                currentScreen = Screen.SEARCH
            }
            else -> {}
        }
    }

    // Reactively switch to PLAYER screen if a stream URL is successfully extracted
    LaunchedEffect(activeStreamUrl) {
        if (activeStreamUrl != null) {
            currentScreen = Screen.PLAYER
        }
    }

    if (currentScreen == Screen.PLAYER) {
        PlayerScreen(
            viewModel = viewModel,
            onBack = {
                viewModel.clearPlayerState()
                currentScreen = if (selectedItem != null) Screen.DETAIL else Screen.SEARCH
            }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = DarkBackground,
            bottomBar = {
                if (currentScreen == Screen.SEARCH) {

                    NavigationBar(
                        containerColor = DarkBackground,
                        contentColor = Color.White,
                        tonalElevation = 8.dp
                    ) {

                        NavigationBarItem(
                            selected = selectedTab == Tab.HOME,
                            onClick = { selectedTab = Tab.HOME },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = ForgeOrange,
                                indicatorColor = ForgeOrange,
                                unselectedIconColor = SteelGrey,
                                unselectedTextColor = SteelGrey
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == Tab.SEARCH,
                            onClick = { selectedTab = Tab.SEARCH },
                            icon = { Icon(Icons.Default.Search, contentDescription = "Ricerca") },
                            label = { Text("Ricerca") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = ForgeOrange,
                                indicatorColor = ForgeOrange,
                                unselectedIconColor = SteelGrey,
                                unselectedTextColor = SteelGrey
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == Tab.CONTINUE_WATCHING,
                            onClick = { selectedTab = Tab.CONTINUE_WATCHING },
                            icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Continua") },
                            label = { Text("Continua") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = ForgeOrange,
                                indicatorColor = ForgeOrange,
                                unselectedIconColor = SteelGrey,
                                unselectedTextColor = SteelGrey
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == Tab.FAVORITES,
                            onClick = { selectedTab = Tab.FAVORITES },
                            icon = { Icon(Icons.Default.Favorite, contentDescription = "Preferiti") },
                            label = { Text("Preferiti") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = ForgeOrange,
                                indicatorColor = ForgeOrange,
                                unselectedIconColor = SteelGrey,
                                unselectedTextColor = SteelGrey
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == Tab.SETTINGS,
                            onClick = { selectedTab = Tab.SETTINGS },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Impostazioni") },
                            label = { Text("Impost.") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = ForgeOrange,
                                indicatorColor = ForgeOrange,
                                unselectedIconColor = SteelGrey,
                                unselectedTextColor = SteelGrey
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(DarkBackground)
            ) {
                when (currentScreen) {
                    Screen.SEARCH -> {
                        when (selectedTab) {
                            Tab.HOME -> {
                                HomeScreen(
                                    viewModel = viewModel,
                                    onNavigateToDetails = {
                                        currentScreen = Screen.DETAIL
                                    }
                                )
                            }
                            Tab.SEARCH -> {
                                SearchScreen(
                                    viewModel = viewModel,
                                    onNavigateToDetails = {
                                        currentScreen = Screen.DETAIL
                                    }
                                )
                            }
                            Tab.CONTINUE_WATCHING -> {
                                ContinueWatchingTab(
                                    viewModel = viewModel,
                                    onNavigateToDetails = {
                                        currentScreen = Screen.DETAIL
                                    },
                                    onInstantPlayEpisode = { provider, item, seasonNum, ep ->
                                        viewModel.setProvider(provider)
                                        viewModel.selectMediaItem(item, seasonNum)
                                        viewModel.playEpisode(item, seasonNum, ep)
                                    },
                                    onInstantPlayMovie = { provider, item ->
                                        viewModel.setProvider(provider)
                                        viewModel.selectMediaItem(item)
                                        viewModel.playMovie(item)
                                    }
                                )
                            }
                            Tab.FAVORITES -> {
                                FavoritesTab(
                                    viewModel = viewModel,
                                    onNavigateToDetails = {
                                        currentScreen = Screen.DETAIL
                                    }
                                )
                            }
                            Tab.SETTINGS -> {
                                SettingsScreen(viewModel = viewModel)
                            }
                        }
                    }
                    Screen.DETAIL -> {
                        DetailScreen(
                            viewModel = viewModel,
                            onBack = {
                                viewModel.selectMediaItem(null)
                                currentScreen = Screen.SEARCH
                            }
                        )
                    }
                    Screen.PLAYER -> {
                        // Handled above outside Scaffold
                    }
                }
            }
        }
    }
}

/* ==========================================================================================
   HOMETAB & FAVORITESTAB SCREENS
   ========================================================================================== */

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val providerLanguage by viewModel.providerLanguage.collectAsStateWithLifecycle()
    val tmdbApiKey by viewModel.tmdbApiKey.collectAsStateWithLifecycle()
    var editKey by remember { mutableStateOf(tmdbApiKey) }

    val languages = listOf(
        "it" to "Italiano", "en" to "English", "es" to "Español", "fr" to "Français",
        "de" to "Deutsch", "pt" to "Português", "ru" to "Русский", "ja" to "日本語", "ko" to "한국어", "zh" to "中文"
    )
    val providerLanguages = listOf("it" to "Italiano", "en" to "Inglese", "ja" to "Giapponese")

    var showAppLangMenu by remember { mutableStateOf(false) }
    var showProviderLangMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(text = "Impostazioni", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        
        // Impostazioni Lingua
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Lingua", fontSize = 18.sp, color = ForgeOrange)
            
            // Lingua App (TMDB e UI)
            Text(text = "Lingua App (Titoli e Trama)", color = Color.LightGray, fontSize = 14.sp)
            Box {
                Button(
                    onClick = { showAppLangMenu = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = languages.find { it.first == appLanguage }?.second ?: appLanguage, color = Color.White)
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = showAppLangMenu,
                    onDismissRequest = { showAppLangMenu = false },
                    modifier = Modifier.background(Color(0xFF2C2C2C))
                ) {
                    languages.forEach { (code, name) ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(name, color = Color.White) },
                            onClick = {
                                viewModel.setAppLanguage(code)
                                showAppLangMenu = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Lingua Provider (StreamingCommunity)
            Text(text = "Lingua Audio Predefinita", color = Color.LightGray, fontSize = 14.sp)
            Box {
                Button(
                    onClick = { showProviderLangMenu = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = providerLanguages.find { it.first == providerLanguage }?.second ?: providerLanguage, color = Color.White)
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = showProviderLangMenu,
                    onDismissRequest = { showProviderLangMenu = false },
                    modifier = Modifier.background(Color(0xFF2C2C2C))
                ) {
                    providerLanguages.forEach { (code, name) ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(name, color = Color.White) },
                            onClick = {
                                viewModel.setProviderLanguage(code)
                                showProviderLangMenu = false
                            }
                        )
                    }
                }
            }
        }
        
        HorizontalDivider(color = SteelGrey.copy(alpha = 0.5f))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Aggiornamenti", fontSize = 18.sp, color = ForgeOrange)
            Text(text = "Aggiorna manualmente i domini dei siti e le impostazioni API dal repository.", color = Color.LightGray, fontSize = 14.sp)
            val context = LocalContext.current
            var isUpdating by remember { mutableStateOf(false) }
            Button(
                onClick = {
                    isUpdating = true
                    viewModel.refreshDomainsAndApi { success ->
                        isUpdating = false
                        if (success) {
                            android.widget.Toast.makeText(context, "Aggiornamento completato con successo", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "Errore durante l'aggiornamento", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isUpdating,
                colors = ButtonDefaults.buttonColors(containerColor = ForgeOrange, contentColor = Color.Black)
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                } else {
                    Text("Aggiorna Ora")
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToDetails: () -> Unit
) {
    val trendingMovies by viewModel.homeTrendingMovies.collectAsStateWithLifecycle()
    val trendingSeries by viewModel.homeTrendingSeries.collectAsStateWithLifecycle()
    val actionMovies by viewModel.homeActionMovies.collectAsStateWithLifecycle()
    val comedyMovies by viewModel.homeComedyMovies.collectAsStateWithLifecycle()
    val homeError by viewModel.homeError.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadHomeData()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp) // Leave space for bottom nav
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
                    AsyncImage(
                        model = heroItem.posterUrl,
                        contentDescription = "Hero Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Gradient overlay
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
        item { HomeCarousel("Serie TV del Momento", trendingSeries, viewModel, onNavigateToDetails) }
        item { HomeCarousel("Film del Momento", trendingMovies, viewModel, onNavigateToDetails) }
        item { HomeCarousel("Azione", actionMovies, viewModel, onNavigateToDetails) }
        item { HomeCarousel("Commedia", comedyMovies, viewModel, onNavigateToDetails) }
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
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        AsyncImage(
                            model = item.posterUrl,
                            contentDescription = "Poster of ${item.name}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContinueWatchingTab(
    viewModel: MainViewModel,
    onNavigateToDetails: () -> Unit,
    onInstantPlayEpisode: (String, MediaItem, Int, Episode) -> Unit,
    onInstantPlayMovie: (String, MediaItem) -> Unit
) {
    val continueWatchingList by viewModel.continueWatchingList.collectAsStateWithLifecycle()
    val isBootstrapping by viewModel.isBootstrapping.collectAsStateWithLifecycle()

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
                            .size(38.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, ForgeOrange, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Continua a Guardare",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
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
        } else if (continueWatchingList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = SteelGrey.copy(alpha = 0.4f),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Nessun titolo in riproduzione",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Inizia la riproduzione di un film o serie TV dalla scheda Ricerca per ritrovarlo qui!",
                            color = SteelGrey,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
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
                items(continueWatchingList, key = { it.id }) { item ->
                    ContinueWatchingCard(
                        item = item,
                        onPlay = {
                            val mediaObj = MediaItem(
                                id = item.mediaId,
                                name = item.name,
                                type = item.type,
                                slug = item.slug,
                                posterUrl = item.posterUrl,
                                year = item.year
                            )
                            if (mediaObj.isMovie) {
                                onInstantPlayMovie(item.provider, mediaObj)
                            } else {
                                val epObj = Episode(
                                    id = item.lastEpisodeId ?: 0,
                                    number = item.lastEpisodeNumber ?: 1,
                                    name = item.lastEpisodeName ?: "Episodio"
                                )
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
                    FavoriteGridCard(
                        item = item,
                        onClick = {
                            val mediaObj = MediaItem(
                                id = item.mediaId,
                                name = item.name,
                                type = item.type,
                                slug = item.slug,
                                posterUrl = item.posterUrl,
                                year = item.year
                            )
                            viewModel.selectMediaItem(mediaObj)
                            onNavigateToDetails()
                        },
                        onRemove = {
                            val mediaObj = MediaItem(
                                id = item.mediaId,
                                name = item.name,
                                type = item.type,
                                slug = item.slug,
                                posterUrl = item.posterUrl,
                                year = item.year
                            )
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
                if (item.posterUrl != null) {
                    AsyncImage(
                        model = item.posterUrl,
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

                // Heart overlay
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

                // Provider Tag
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
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
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
    val provider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
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
                // Top Header Branding layout with custom StreamForge image logo (Feature 1)
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

                // Selector for Streaming providers
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
                        Text("Provider: ${provider.replaceFirstChar { it.uppercase() }}", color = ForgeOrange, fontWeight = FontWeight.Bold)
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
                                        color = if (p.equals(provider, ignoreCase = true)) ForgeOrange else Color.White,
                                        fontWeight = if (p.equals(provider, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal
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
                // Search Bar Input Section
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

                // Search Button (Action Trigger)
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
                            Text("Cerca su $provider", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }

                // Search Feedback error display
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

                // Search result listing
                if (!isSearching && results.isNotEmpty()) {
                    items(results) { mediaItem ->
                        MediaItemSearchRow(
                            item = mediaItem,
                            onClick = {
                                viewModel.selectMediaItem(mediaItem)
                                onNavigateToDetails()
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                } else if (!isSearching && results.isEmpty() && query.isBlank()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Placeholder",
                                    tint = SteelGrey.copy(alpha = 0.5f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Inserisci un titolo per iniziare la fucina dello streaming",
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
            if (item.posterUrl != null) {
                AsyncImage(
                    model = item.posterUrl,
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

            // Dark gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
            )

            // Absolute overlays
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
                    Icon(Icons.Default.Delete, contentDescription = "Dele", tint = Color.Red, modifier = Modifier.size(14.dp))
                }
            }

            // Central Play Floating Action (Feature 2 quick-play)
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

            // Visual Playback Progress Bar (Feature 2)
            if (item.lastPositionMillis != null && item.durationMillis != null && item.durationMillis > 0) {
                val progress = item.lastPositionMillis.toFloat() / item.durationMillis.toFloat()
                LinearProgressIndicator(
                    progress = progress.coerceIn(0f, 1f),
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
        }
    }
}

@Composable
fun MediaItemSearchRow(
    item: MediaItem,
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
            if (item.posterUrl != null) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(80.dp)
                        .height(115.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(115.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Gray.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ForgeOrange)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                Text(
                    text = item.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

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

                    if (!item.year.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = item.year,
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
                contentDescription = "Details Arrow",
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
    val currentContext = androidx.compose.ui.platform.LocalContext.current
    val selectedItem by viewModel.selectedMediaItem.collectAsStateWithLifecycle()
    val seasons by viewModel.seasons.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val selectedSeason by viewModel.selectedSeasonNumber.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingDetails.collectAsStateWithLifecycle()
    val detailsError by viewModel.detailsError.collectAsStateWithLifecycle()

    val isExtracting by viewModel.isExtractingStream.collectAsStateWithLifecycle()
    val streamError by viewModel.streamError.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isCurrentMediaFavorite.collectAsStateWithLifecycle()
    val lastWatched by viewModel.lastWatchedEpisodeForSelected.collectAsStateWithLifecycle()

    val item = selectedItem ?: return

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = { Text(text = "Dettagli Titolo", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Indietro", tint = ForgeOrange)
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
            // Header Image and Info
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    if (item.posterUrl != null) {
                        AsyncImage(
                            model = item.posterUrl,
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
                            if (!item.year.isNullOrBlank()) {
                                Text(
                                    text = "Anno di rilascio: ${item.year}",
                                    color = SteelGrey,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                text = "Provider: ${viewModel.selectedProvider.value}",
                                color = SteelGrey,
                                fontSize = 13.sp
                            )

                        }
                    }
                }
            }

            // Status message during link processing
            if (isExtracting) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = ForgeOrange, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Estrazione link di streaming HLS in corso...", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }

            // Stream extraction error
            if (streamError != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x44F44336)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = streamError ?: "",
                            color = Color.White,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Play Film Mode
            if (item.isMovie) {
                item {
                    Button(
                        onClick = { viewModel.playMovie(item) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("play_movie_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ForgeOrange),
                        enabled = !isExtracting
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("▶ RIPRODUCI FILM COMPLETO", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.copySingleStream(item, null, null, currentContext) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ForgeOrange),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ForgeOrange),
                        enabled = !isExtracting
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Copia Link m3u8", tint = ForgeOrange)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("⎘ COPIA LINK M3U8", color = ForgeOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            } else {
                // If there's an active last watched episode/season, show a stylized Resume button at the top!
                if (lastWatched != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ForgeOrange.copy(alpha = 0.15f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .border(1.dp, ForgeOrange.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Continua la visione",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = ForgeOrange
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Stagione ${lastWatched?.lastSeasonNumber} • Episodio ${lastWatched?.lastEpisodeNumber}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    if (!lastWatched?.lastEpisodeName.isNullOrBlank()) {
                                        Text(
                                            text = lastWatched?.lastEpisodeName ?: "",
                                            color = SteelGrey,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Button(
                                    onClick = {
                                        val epObj = Episode(
                                            id = lastWatched?.lastEpisodeId ?: 0,
                                            number = lastWatched?.lastEpisodeNumber ?: 1,
                                            name = lastWatched?.lastEpisodeName ?: "Episodio"
                                        )
                                        viewModel.playEpisode(item, lastWatched?.lastSeasonNumber ?: 1, epObj)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ForgeOrange),
                                    enabled = !isExtracting,
                                    modifier = Modifier.padding(start = 12.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PROSEGUI", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                // Series Seasons and Episodes
                item {
                    Divider(color = SteelGrey.copy(alpha = 0.3f), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
                }

                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = ForgeOrange)
                        }
                    }
                } else if (detailsError != null) {
                    item {
                        Text(
                            text = detailsError ?: "",
                            color = Color.Red,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    // Seasons dropdown
                    if (seasons.isNotEmpty()) {
                        item {
                            Text(
                                text = "Seleziona Stagione",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

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
                                    Icon(Icons.Default.Share, contentDescription = "Copia Stagione", tint = ForgeOrange)
                                }
                            }
                        }
                    }

                    // Episode count / header
                    item {
                        Text(
                            text = "Lista Episodi (${episodes.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForgeOrange,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    // Load episode rows onto lazy column
                    if (episodes.isEmpty()) {
                        item {
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
                        items(episodes) { episode ->
                            EpisodeRow(
                                episode = episode,
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
}

@Composable
fun EpisodeRow(
    episode: Episode,
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
                IconButton(
                    onClick = onCopyClick,
                    enabled = enabled,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Copia link m3u8",
                        tint = if (enabled) ForgeOrange else SteelGrey,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Riproduci",
                    tint = if (enabled) ForgeOrange else SteelGrey,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}


/* ==========================================================================================
   FULLSCREEN PLAYER SCREEN
   ========================================================================================== */

@Composable
fun PlayerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val streamUrl by viewModel.activeStreamUrl.collectAsStateWithLifecycle()
    val provider = viewModel.selectedProvider.value
    val hasNextEpisode by viewModel.hasNextEpisode.collectAsStateWithLifecycle()
    val resumePosition by viewModel.playbackResumePosition.collectAsStateWithLifecycle()

    val currentStreamUrl = streamUrl ?: ""
    var isControllerVisible by remember { mutableStateOf(true) }
    var showMirrorOptions by remember { mutableStateOf(false) }
    var showSubtitlesMenu by remember { mutableStateOf(false) }
    var showAudioLanguageMenu by remember { mutableStateOf(false) }
    // Stato per la funzione zoom/riempi (limita bordi neri)
    var isZoomed by remember { mutableStateOf(false) }
    
    val appLang by viewModel.appLanguage.collectAsStateWithLifecycle()
    val subLang by viewModel.subtitleLanguage.collectAsStateWithLifecycle()
    val activeItem by viewModel.selectedMediaItem.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isCurrentMediaFavorite.collectAsStateWithLifecycle()
    val audioLang = activeItem?.providerLanguage ?: "it"
    // Il cambio lingua audio (it/en) è disponibile solo dove il provider espone versioni
    // separate del contenuto per lingua (attualmente StreamingCommunity).
    val supportsAudioLanguageSwitch = provider == "StreamingCommunity"

    val subtitleOptions = listOf("off" to "Disattivati") + listOf(
        "it" to "Italiano", "en" to "English", "es" to "Español", "fr" to "Français",
        "de" to "Deutsch", "pt" to "Português", "ru" to "Русский", "ja" to "日本語", "ko" to "한국어", "zh" to "中文"
    )
    val audioLanguageOptions = listOf("it" to "Italiano", "en" to "English")

    // Set and release landscape orientation, hide/restore system bars, and keep screen on
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

    // Inizializza ExoPlayer e definisce gli header HTTP richiesti dai vari provider di streaming
    // I referrer sono necessari, altrimenti i CDN rifiutano la connessione (es. vixcloud per StreamingCommunity)
    val exoPlayer = remember(provider) {
        // Imposta il referrer corretto in base al provider attualmente in uso
        val customReferer = if (provider.lowercase().contains("community")) {
            "https://vixcloud.co/"
        } else {
            "https://www.animeunity.so/"
        }

        // Fabbrica di sorgenti HTTP che inietta User-Agent standard e Referer nel player
        val httpDataSourceFactory = DefaultHttpDataSource.Factory().apply {
            setDefaultRequestProperties(
                mapOf(
                    "User-Agent" to com.example.data.network.HttpClient.USER_AGENT,
                    "Referer" to customReferer
                )
            )
        }

        // Selettore traccia di default: permette ad ExoPlayer di popolare automaticamente il menu
        // con la scelta di lingua audio, sottotitoli e risoluzione video se presenti nel flusso.
        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context)
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setPreferredAudioLanguage(appLang)
            .setPreferredTextLanguage(subLang)
            .build()

        // Costruisce l'istanza finale di ExoPlayer
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
            .setTrackSelector(trackSelector)
            .build()
    }

    // Applica le modifiche ai sottotitoli istantaneamente quando cambia la lingua
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

    // Handle stream URL changes dynamically on the same ExoPlayer instance
    LaunchedEffect(currentStreamUrl) {
        if (currentStreamUrl.isNotEmpty()) {
            val mediaItemUri = Uri.parse(currentStreamUrl)
            val isHls = currentStreamUrl.contains(".m3u8") || currentStreamUrl.contains("vixcloud")
            val mediaItem = if (isHls) {
                MediaItem3.Builder()
                    .setUri(mediaItemUri)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build()
            } else {
                MediaItem3.fromUri(mediaItemUri)
            }
            exoPlayer.setMediaItem(mediaItem)
            if (resumePosition > 0) {
                exoPlayer.seekTo(resumePosition)
            } else {
                exoPlayer.seekTo(0L)
            }
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.stop()
        }
    }

    // Progress updates periodically (every 5 seconds)
    LaunchedEffect(exoPlayer) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            try {
                if (exoPlayer.isPlaying) {
                    val currentPos = exoPlayer.currentPosition
                    val duration = exoPlayer.duration
                    if (currentPos > 0 && duration > 0) {
                        viewModel.updatePlaybackPosition(currentPos, duration)
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            try {
                val currentPos = exoPlayer.currentPosition
                val duration = exoPlayer.duration
                if (currentPos > 0 && duration > 0) {
                    viewModel.updatePlaybackPosition(currentPos, duration)
                }
            } catch (e: Exception) {
                // Ignore
            }
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                // Ferma (mette in pausa) la riproduzione video se lo schermo si blocca o si cambia app.
                // Al ritorno nell'app, il video rimarrà in pausa e sarà possibile farlo ripartire manualmente premendo play.
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Completely blank backdrop for cinema vibes, overlaying ExoPlayer
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("player_screen_root")
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        isControllerVisible = (visibility == android.view.View.VISIBLE)
                    })
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                if (playerView.player != exoPlayer) {
                    playerView.player = exoPlayer
                }
                // Applica lo zoom (taglia i bordi neri) oppure adatta normalmente
                playerView.resizeMode = if (isZoomed) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier.fillMaxSize()
        )

        // Custom hovering Back Arrow button overlaying top left (hides when controls hide)
        AnimatedVisibility(
            visible = isControllerVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(top = 16.dp, start = 16.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Chiudi Media Player",
                    tint = Color.White
                )
            }
        }

        // Custom Next Episode and Cast buttons overlaying top right (hides when controls hide)
        AnimatedVisibility(
            visible = isControllerVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 16.dp, end = 16.dp)
            ) {
                // Pulsante Preferiti: permette di aggiungere/rimuovere il contenuto dai
                // preferiti senza dover uscire dal player.
                IconButton(
                    onClick = { viewModel.toggleFavorite() },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                        tint = if (isFavorite) Color.Red else Color.White
                    )
                }

                // Pulsante Riempi Schermo (Zoom) per tagliare i bordi neri
                IconButton(
                    onClick = { isZoomed = !isZoomed },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = if (isZoomed) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "Espandi a tutto schermo",
                        tint = Color.White
                    )
                }

                // Pulsante Lingua Audio (solo dove il provider supporta versioni multilingua)
                if (supportsAudioLanguageSwitch) {
                    Box {
                        IconButton(
                            onClick = { showAudioLanguageMenu = true },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Lingua Audio",
                                tint = Color.White
                            )
                        }

                        androidx.compose.material3.DropdownMenu(
                            expanded = showAudioLanguageMenu,
                            onDismissRequest = { showAudioLanguageMenu = false },
                            modifier = Modifier.background(Color(0xFF2C2C2C))
                        ) {
                            audioLanguageOptions.forEach { (code, name) ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(name, color = Color.White)
                                            if (audioLang == code) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Icon(Icons.Default.Check, contentDescription = "Selezionato", tint = ForgeOrange, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        showAudioLanguageMenu = false
                                        if (audioLang != code) {
                                            viewModel.switchPlaybackLanguage(code, exoPlayer.currentPosition)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Pulsante Sottotitoli
                Box {
                    IconButton(
                        onClick = { showSubtitlesMenu = true },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = "Sottotitoli",
                            tint = Color.White
                        )
                    }
                    
                    // Sottomenu Sottotitoli
                    androidx.compose.material3.DropdownMenu(
                        expanded = showSubtitlesMenu,
                        onDismissRequest = { showSubtitlesMenu = false },
                        modifier = Modifier.background(Color(0xFF2C2C2C))
                    ) {
                        subtitleOptions.forEach { (code, name) ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(name, color = Color.White)
                                        if (subLang == code) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(Icons.Default.Check, contentDescription = "Selezionato", tint = ForgeOrange, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.setSubtitleLanguage(code)
                                    showSubtitlesMenu = false
                                }
                            )
                        }
                    }
                }

                // Contenitore per il pulsante Mirrorcast e il suo menu a tendina
                Box {
                    // Pulsante Mirrorcast (Trasmissione schermo)
                    IconButton(
                        onClick = {
                            // Invece di aprire direttamente le impostazioni, mostriamo un menu
                            // con due opzioni per l'utente, mantenendo il video in riproduzione.
                            showMirrorOptions = true
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)) // Sfondo semi-trasparente
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cast, // Icona ufficiale del Cast
                            contentDescription = "Opzioni Mirrorcast",
                            tint = Color.White
                        )
                    }

                    // Menu a tendina (DropdownMenu) con le opzioni di trasmissione
                    androidx.compose.material3.DropdownMenu(
                        expanded = showMirrorOptions,
                        onDismissRequest = { showMirrorOptions = false },
                        modifier = Modifier.background(Color(0xFF2C2C2C)) // Sfondo scuro per adattarsi al player
                    ) {
                        // Opzione 1: Mirroring Schermo classico
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Mirroring Schermo", color = Color.White) },
                            onClick = {
                                showMirrorOptions = false
                                try {
                                    // Apre le impostazioni di trasmissione dello schermo di Android come prima
                                    context.startActivity(android.content.Intent("android.settings.CAST_SETTINGS"))
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Mirrorcast non supportato sul dispositivo", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        // Opzione 2: Riproduzione web tramite m3u8player.online
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Condividi Link Web Player (per TV/Cast)", color = Color.White) },
                            onClick = {
                                showMirrorOptions = false
                                try {
                                    // Codifica l'URL del video per passarlo come parametro in modo sicuro
                                    val encodedUrl = java.net.URLEncoder.encode(currentStreamUrl, "UTF-8")
                                    // Costruisce l'URL finale del player web
                                    val webPlayerUrl = "https://www.m3u8player.online/m3u8?url=$encodedUrl"
                                    
                                    // Android non permette di forzare nativamente l'apertura diretta di un link
                                    // nel browser di una Smart TV (salvo l'uso di specifici SDK come Google Cast).
                                    // La soluzione più efficace è sfruttare l'intent di Condivisione (Share):
                                    // l'utente può passare il link ad app dedicate al casting (come "Web Video Caster")
                                    // o inviarlo ad un altro dispositivo.
                                    val sendIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        putExtra(android.content.Intent.EXTRA_TEXT, webPlayerUrl)
                                        type = "text/plain"
                                    }
                                    val shareIntent = android.content.Intent.createChooser(sendIntent, "Trasmetti/Condividi Player M3U8")
                                    context.startActivity(shareIntent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Errore durante la condivisione", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }

                if (hasNextEpisode) {
                    Button(
                        onClick = { viewModel.playNextEpisode() },
                        colors = ButtonDefaults.buttonColors(containerColor = ForgeOrange),
                        modifier = Modifier.testTag("play_next_episode_button")
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Prossimo", tint = Color.Black)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PROSSIMO", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
