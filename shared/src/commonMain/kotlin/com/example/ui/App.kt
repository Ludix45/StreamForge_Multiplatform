package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import com.example.data.network.CommonDomainManager
import com.example.data.network.CommonScraper
import com.example.ui.components.VideoPlayer
import kotlinx.coroutines.launch

enum class Page { HOME, SEARCH, DETAILS, PLAYER }

@Composable
fun StreamForgeApp() {
    var currentPage by remember { mutableStateOf(Page.HOME) }
    var selectedItem by remember { mutableStateOf<MediaItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var playbackUrl by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    val orange = Color(0xFFFF7900)
    val darkBg = Color(0xFF101218)

    MaterialTheme(colors = darkColors(primary = orange, background = darkBg)) {
        Scaffold(
            bottomBar = {
                if (currentPage != Page.PLAYER) {
                    BottomNavigation(backgroundColor = Color(0xFF181C25)) {
                        BottomNavigationItem(
                            selected = currentPage == Page.HOME,
                            onClick = { currentPage = Page.HOME },
                            icon = { Icon(Icons.Default.Home, null) },
                            label = { Text("Home") },
                            selectedContentColor = orange
                        )
                        BottomNavigationItem(
                            selected = currentPage == Page.SEARCH,
                            onClick = { currentPage = Page.SEARCH },
                            icon = { Icon(Icons.Default.Search, null) },
                            label = { Text("Cerca") },
                            selectedContentColor = orange
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize().background(darkBg)) {
                when (currentPage) {
                    Page.HOME -> {
                        Column(Modifier.padding(16.dp)) {
                            Text("In primo piano", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(16.dp))
                            Text("Porting iOS in corso...", color = Color.Gray)
                            Button(onClick = { currentPage = Page.SEARCH }) {
                                Text("Vai alla ricerca")
                            }
                        }
                    }
                    Page.SEARCH -> {
                        Column(Modifier.fillMaxSize().padding(16.dp)) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text("Titolo", color = Color.Gray) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = orange)
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
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                            ) {
                                if (isSearching) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp))
                                else Text("Cerca")
                            }
                            
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(150.dp),
                                modifier = Modifier.padding(top = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(searchResults) { item ->
                                    Card(Modifier.height(220.dp).clickable { 
                                        selectedItem = item
                                        currentPage = Page.DETAILS
                                    }, backgroundColor = Color(0xFF181C25)) {
                                        Column {
                                            Box(Modifier.height(160.dp).fillMaxWidth().background(Color.Gray)) {
                                                Text(item.name, color = Color.White, modifier = Modifier.align(Alignment.Center))
                                            }
                                            Text(item.name, color = Color.White, maxLines = 2, modifier = Modifier.padding(4.dp), fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Page.DETAILS -> {
                        selectedItem?.let { item ->
                            Column(Modifier.padding(16.dp)) {
                                Button(onClick = { currentPage = Page.SEARCH }) { Text("Indietro") }
                                Text(item.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Button(onClick = {
                                    scope.launch {
                                        val domain = CommonDomainManager.getUrl("streamingcommunity")
                                        playbackUrl = CommonScraper.extractStreamingCommunityUrl(item, null, domain)
                                        currentPage = Page.PLAYER
                                    }
                                }) {
                                    Text("Riproduci")
                                }
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
