package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Episode
import com.example.data.model.MediaItem
import com.example.data.model.Season
import com.example.data.network.CommonDomainManager
import com.example.data.network.CommonScraper
import com.example.ui.components.VideoPlayer
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import kotlinx.coroutines.launch

enum class Page { HOME, SEARCH, DETAILS, PLAYER }

@Composable
fun StreamForgeApp() {
    var currentPage by remember { mutableStateOf(Page.HOME) }
    var selectedItem by remember { mutableStateOf<MediaItem?>(null) }
    var seasons by remember { mutableStateOf<List<Season>>(emptyList()) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    var isLoadingDetails by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var playbackUrl by remember { mutableStateOf<String?>(null) }
    
    var trendingMovies by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var trendingSeries by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    
    val scope = rememberCoroutineScope()
    val orange = Color(0xFFFF7900)
    val darkBg = Color(0xFF101218)
    val surface = Color(0xFF181C25)
    val tmdbKey = "c90967c3177c7d60362c59fa9cb4a333"

    LaunchedEffect(Unit) {
        scope.launch {
            trendingMovies = try { CommonScraper.getTrending(true, tmdbKey, "it-IT") } catch(e: Exception) { emptyList() }
            trendingSeries = try { CommonScraper.getTrending(false, tmdbKey, "it-IT") } catch(e: Exception) { emptyList() }
        }
    }

    MaterialTheme(colors = darkColors(primary = orange, background = darkBg, surface = surface)) {
        Scaffold(
            bottomBar = {
                if (currentPage != Page.PLAYER) {
                    BottomNavigation(backgroundColor = surface) {
                        BottomNavigationItem(
                            selected = currentPage == Page.HOME,
                            onClick = { currentPage = Page.HOME },
                            icon = { Icon(Icons.Default.Home, null) },
                            label = { Text("Home") },
                            selectedContentColor = orange,
                            unselectedContentColor = Color.Gray
                        )
                        BottomNavigationItem(
                            selected = currentPage == Page.SEARCH,
                            onClick = { currentPage = Page.SEARCH },
                            icon = { Icon(Icons.Default.Search, null) },
                            label = { Text("Cerca") },
                            selectedContentColor = orange,
                            unselectedContentColor = Color.Gray
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize().background(darkBg)) {
                when (currentPage) {
                    Page.HOME -> {
                        LazyColumn(Modifier.fillMaxSize()) {
                            item {
                                Box(Modifier.fillMaxWidth().height(300.dp)) {
                                    val hero = trendingSeries.firstOrNull() ?: trendingMovies.firstOrNull()
                                    hero?.posterUrl?.let {
                                        KamelImage(
                                            resource = asyncPainterResource(it),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, darkBg))))
                                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                                        Text(hero?.name ?: "StreamForge", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("Inizia lo streaming oggi", color = orange, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            
                            item { HomeSection("Film di tendenza", trendingMovies) { item -> selectedItem = item; currentPage = Page.DETAILS } }
                            item { HomeSection("Serie TV popolari", trendingSeries) { item -> selectedItem = item; currentPage = Page.DETAILS } }
                            
                            item { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                    Page.SEARCH -> {
                        Column(Modifier.fillMaxSize().padding(16.dp)) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text("Cerca film o serie TV...", color = Color.Gray) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = orange, cursorColor = orange),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Button(
                                onClick = {
                                    scope.launch {
                                        isSearching = true
                                        val domain = CommonDomainManager.getUrl("streamingcommunity")
                                        searchResults = CommonScraper.searchStreamingCommunity(searchQuery, domain)
                                        isSearching = false
                                    }
                                },
                                modifier = Modifier.padding(top = 12.dp).fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isSearching) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                                else Text("CERCA")
                            }
                            
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.padding(top = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(searchResults) { item ->
                                    MediaCard(item) {
                                        selectedItem = item
                                        currentPage = Page.DETAILS
                                    }
                                }
                            }
                        }
                    }
                    Page.DETAILS -> {
                        selectedItem?.let { item ->
                            LaunchedEffect(item) {
                                if (!item.isMovie) {
                                    isLoadingDetails = true
                                    val domain = CommonDomainManager.getUrl("streamingcommunity")
                                    seasons = CommonScraper.getStreamingCommunitySeasons(item, domain)
                                    if (seasons.isNotEmpty()) {
                                        selectedSeason = seasons.first().number
                                        episodes = CommonScraper.getStreamingCommunityEpisodes(item, seasons.first().number, domain)
                                    }
                                    isLoadingDetails = false
                                }
                            }

                            LazyColumn(Modifier.fillMaxSize()) {
                                item {
                                    Box(Modifier.fillMaxWidth().height(250.dp)) {
                                        item.posterUrl?.let {
                                        KamelImage(asyncPainterResource(it), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    }
                                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, darkBg))))
                                        IconButton(onClick = { currentPage = Page.SEARCH }, modifier = Modifier.padding(16.dp).background(Color.Black.copy(0.5f), RoundedCornerShape(8.dp))) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                                        }
                                    }
                                }
                                
                                item {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(item.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(item.type.uppercase() + (item.year?.let { " • $it" } ?: ""), color = Color.Gray, fontSize = 14.sp)
                                        
                                        if (item.isMovie) {
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        isLoadingDetails = true
                                                        val domain = CommonDomainManager.getUrl("streamingcommunity")
                                                        playbackUrl = CommonScraper.extractStreamingCommunityUrl(item, null, domain)
                                                        currentPage = Page.PLAYER
                                                        isLoadingDetails = false
                                                    }
                                                },
                                                modifier = Modifier.padding(top = 24.dp).fillMaxWidth().height(50.dp),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                if (isLoadingDetails) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                                                else Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.PlayArrow, null)
                                                    Spacer(Modifier.width(8.dp))
                                                    Text("RIPRODUCI ORA")
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                if (!item.isMovie) {
                                    item {
                                        if (isLoadingDetails) Box(Modifier.fillMaxWidth().height(100.dp), Alignment.Center) { CircularProgressIndicator() }
                                        else if (seasons.isNotEmpty()) {
                                            Text("Episodi", color = orange, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                                        }
                                    }
                                    
                                    items(episodes) { ep ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable {
                                                scope.launch {
                                                    val domain = CommonDomainManager.getUrl("streamingcommunity")
                                                    playbackUrl = CommonScraper.extractStreamingCommunityUrl(item, ep.id, domain)
                                                    currentPage = Page.PLAYER
                                                }
                                            },
                                            backgroundColor = surface,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(ep.number.toString(), color = orange, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                                                Text(ep.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Spacer(Modifier.weight(1f))
                                                Icon(Icons.Default.PlayCircle, null, tint = orange)
                                            }
                                        }
                                    }
                                }
                                
                                item { Spacer(Modifier.height(32.dp)) }
                            }
                        }
                    }
                    Page.PLAYER -> {
                        playbackUrl?.let { url ->
                            VideoPlayer(
                                url = url,
                                title = selectedItem?.name ?: "StreamForge",
                                modifier = Modifier.fillMaxSize(),
                                onBack = { currentPage = Page.DETAILS }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeSection(title: String, items: List<MediaItem>, onItemClick: (MediaItem) -> Unit) {
    if (items.isEmpty()) return
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items) { item ->
                Box(Modifier.width(120.dp).height(180.dp).clip(RoundedCornerShape(12.dp)).clickable { onItemClick(item) }) {
                    item.posterUrl?.let {
                        KamelImage(asyncPainterResource(it), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
            }
        }
    }
}

@Composable
fun MediaCard(item: MediaItem, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().height(260.dp).clickable { onClick() }, shape = RoundedCornerShape(12.dp), backgroundColor = Color(0xFF181C25)) {
        Column {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                item.posterUrl?.let {
                    KamelImage(asyncPainterResource(it), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } ?: Box(Modifier.fillMaxSize().background(Color.DarkGray))
            }
            Text(item.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp), textAlign = TextAlign.Center)
        }
    }
}
