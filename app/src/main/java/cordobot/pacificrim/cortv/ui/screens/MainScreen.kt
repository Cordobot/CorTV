package cordobot.pacificrim.cortv.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import cordobot.pacificrim.cortv.common.data.PreferencesManager
import cordobot.pacificrim.cortv.common.model.MediaItem
import cordobot.pacificrim.cortv.common.model.countryFlag
import cordobot.pacificrim.cortv.common.model.countryName
import cordobot.pacificrim.cortv.common.parser.IptvOrgApi
import cordobot.pacificrim.cortv.common.parser.M3UParser
import cordobot.pacificrim.cortv.ui.components.VideoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsManager = remember { PreferencesManager(context) }

    // State Variables
    var selectedCategory by remember { mutableStateOf("Canales de TV") } // "Series", "Animes", "Peliculas", "Canales de TV", "Buscador", "Favoritos"
    var searchQuery by remember { mutableStateOf("") }
    var playlistUrl by remember { mutableStateOf(prefsManager.m3uUrl) }
    var selectedMediaItem by remember { mutableStateOf<MediaItem?>(null) }
    var playingMediaItem by remember { mutableStateOf<MediaItem?>(null) }

    // Dialog states
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    // Loaded Media Items
    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Favorites list from prefs
    var favoritesList by remember { mutableStateOf(prefsManager.getFavorites()) }
    // History list from prefs
    var historyList by remember { mutableStateOf(prefsManager.getHistory()) }

    // Drawer state
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Load Playlist Data
    LaunchedEffect(playlistUrl) {
        isLoading = true
        scope.launch(Dispatchers.IO) {
            val items = if (playlistUrl == M3UParser.DEFAULT_M3U_URL) {
                M3UParser.DEMO_STREAMS
            } else {
                M3UParser.fetchFromUrl(playlistUrl)
            }
            // The iptv-org live channels (API + database, cached on disk) are always added after the configured playlist
            val channels = IptvOrgApi.loadChannels(context)
            withContext(Dispatchers.Main) {
                mediaItems = (items + channels.items).distinctBy { it.id }
                channels.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                isLoading = false
            }
        }
    }

    // Refresh function for Favorites / History
    val refreshPreferencesData = {
        favoritesList = prefsManager.getFavorites()
        historyList = prefsManager.getHistory()
    }

    // Primary premium dark theme colors
    val darkBackground = Color(0xFF0F0F1E)
    val cardBackground = Color(0xFF1E1E2F)
    val accentColor = Color(0xFFE21B4D) // Vibrant red/pink accent
    val textColor = Color.White
    val secondaryTextColor = Color(0xFF8A8A9D)

    // Main layout with Left Menu Drawer
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = cardBackground,
                modifier = Modifier.width(280.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                // App Logo/Header
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleFilled,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "COR TV",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        letterSpacing = 1.5.sp
                    )
                }

                HorizontalDivider(color = secondaryTextColor.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 16.dp))

                Spacer(modifier = Modifier.height(16.dp))

                // Left Menu Options
                val menuOptions = listOf(
                    Triple("Series", "Series", Icons.Default.Tv),
                    Triple("Animes", "Animes", Icons.Default.Face),
                    Triple("Peliculas", "Películas", Icons.Default.Movie),
                    Triple("Canales de TV", "Canales de TV", Icons.Default.LiveTv),
                    Triple("Buscador", "Buscador", Icons.Default.Search)
                )

                menuOptions.forEach { (categoryKey, label, icon) ->
                    val isSelected = selectedCategory == categoryKey
                    NavigationDrawerItem(
                        icon = { Icon(imageVector = icon, contentDescription = null, tint = if (isSelected) textColor else secondaryTextColor) },
                        label = { Text(text = label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        selected = isSelected,
                        onClick = {
                            selectedCategory = categoryKey
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = accentColor,
                            selectedTextColor = textColor,
                            unselectedTextColor = secondaryTextColor,
                            unselectedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = darkBackground
                    ),
                    title = {
                        Text(
                            text = selectedCategory,
                            color = textColor,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Menú", tint = textColor)
                        }
                    },
                    actions = {
                        // Favorites, History and Settings top actions
                        IconButton(onClick = {
                            selectedCategory = "Favoritos"
                            refreshPreferencesData()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Favoritos",
                                tint = if (selectedCategory == "Favoritos") accentColor else textColor
                            )
                        }
                        IconButton(onClick = { showHistoryDialog = true }) {
                            Icon(imageVector = Icons.Default.History, contentDescription = "Historial", tint = textColor)
                        }
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Configuración", tint = textColor)
                        }
                    }
                )
            },
            containerColor = darkBackground
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Content Filter logic
                val filteredItems = when (selectedCategory) {
                    "Favoritos" -> favoritesList
                    "Buscador" -> mediaItems.filter { it.title.contains(searchQuery, ignoreCase = true) }
                    else -> mediaItems.filter { it.category == selectedCategory }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Search bar if Buscador is selected
                    if (selectedCategory == "Buscador") {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar películas, series, canales...", color = secondaryTextColor) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = cardBackground,
                                unfocusedContainerColor = cardBackground,
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor,
                                focusedIndicatorColor = accentColor,
                                cursorColor = accentColor
                            ),
                            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = secondaryTextColor) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    if (isLoading && selectedCategory != "Favoritos") {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = accentColor)
                        }
                    } else if (filteredItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No se encontraron elementos.",
                                color = secondaryTextColor,
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        // Gorgeous Responsive Grid of cards
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 140.dp),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(filteredItems) { item ->
                                val isFav = prefsManager.isFavorite(item.id)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clickable { selectedMediaItem = item.copy(isFavorite = isFav) },
                                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        // Logo Image
                                        if (!item.logoUrl.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = item.logoUrl,
                                                contentDescription = item.title,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            // Fallback Gradient
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.verticalGradient(
                                                            colors = listOf(accentColor.copy(alpha = 0.4f), Color.Transparent)
                                                        )
                                                    )
                                            )
                                        }

                                        // Dark Overlay
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                                                        startY = 100f
                                                    )
                                                )
                                        )

                                        // Title and Info
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomStart)
                                                .padding(8.dp)
                                        ) {
                                            Text(
                                                text = item.title,
                                                color = textColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        // Favorite Badge
                                        if (isFav) {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = null,
                                                tint = accentColor,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(8.dp)
                                                    .size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Media Details Bottom Sheet / Dialog
    selectedMediaItem?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedMediaItem = null },
            containerColor = cardBackground,
            title = {
                Text(
                    text = item.title,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (!item.logoUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = item.logoUrl,
                            contentDescription = item.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    Text(
                        text = item.description ?: channelSummary(item) ?: "Sin descripción disponible.",
                        color = secondaryTextColor,
                        fontSize = 14.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        prefsManager.addToHistory(item)
                        playingMediaItem = item
                        selectedMediaItem = null
                        refreshPreferencesData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reproducir")
                }
            },
            dismissButton = {
                val isFav = prefsManager.isFavorite(item.id)
                IconButton(
                    onClick = {
                        if (isFav) {
                            prefsManager.removeFavorite(item.id)
                        } else {
                            prefsManager.addFavorite(item)
                        }
                        selectedMediaItem = selectedMediaItem?.copy(isFavorite = !isFav)
                        refreshPreferencesData()
                    }
                ) {
                    Icon(
                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorito",
                        tint = accentColor
                    )
                }
            }
        )
    }

    // Video Player overlay
    playingMediaItem?.let { item ->
        VideoPlayer(
            videoUrl = item.url,
            title = item.title,
            description = item.description,
            userAgent = item.userAgent,
            referrer = item.referrer,
            onBack = {
                playingMediaItem = null
                refreshPreferencesData()
            }
        )
    }

    // Settings Dialog
    if (showSettingsDialog) {
        var tempUrl by remember { mutableStateOf(playlistUrl) }
        var tempLang by remember { mutableStateOf(prefsManager.subtitleLanguage) }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = cardBackground,
            title = { Text("Configuración", color = textColor, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        label = { Text("URL de la lista IPTV (.m3u)") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Idioma de subtítulos predeterminado:", color = textColor, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("es" to "Español", "en" to "Inglés").forEach { (code, name) ->
                            Row(
                                modifier = Modifier
                                    .clickable { tempLang = code }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = tempLang == code,
                                    onClick = { tempLang = code },
                                    colors = RadioButtonDefaults.colors(selectedColor = accentColor)
                                )
                                Text(name, color = textColor)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        prefsManager.m3uUrl = tempUrl
                        prefsManager.subtitleLanguage = tempLang
                        playlistUrl = tempUrl
                        showSettingsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancelar", color = secondaryTextColor)
                }
            }
        )
    }

    // History Dialog
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            containerColor = cardBackground,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Historial de Reproducción", color = textColor, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        prefsManager.clearHistory()
                        refreshPreferencesData()
                        showHistoryDialog = false
                    }) {
                        Text("Limpiar", color = accentColor)
                    }
                }
            },
            text = {
                Box(modifier = Modifier.sizeIn(maxHeight = 300.dp)) {
                    if (historyList.isEmpty()) {
                        Text("El historial está vacío.", color = secondaryTextColor)
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(1),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(historyList) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            playingMediaItem = item
                                            showHistoryDialog = false
                                        }
                                        .background(darkBackground, RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = accentColor)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = item.title,
                                        color = textColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text("Cerrar", color = secondaryTextColor)
                }
            }
        )
    }
}

/** Description of an iptv-org channel built from its database fields, or null if there is nothing to say. */
private fun channelSummary(item: MediaItem): String? {
    val lines = listOfNotNull(
        item.genre,
        item.country?.let { "País: ${countryFlag(it)} ${countryName(it)}" },
        item.network?.let { "Cadena: $it" },
        item.quality?.let { "Calidad: $it" },
        item.website?.let { "Web: $it" },
        "Puede estar bloqueado en tu país".takeIf { "Geo-blocked" in item.labels },
        "No emite 24/7".takeIf { "Not 24/7" in item.labels }
    )
    return lines.joinToString("\n").ifEmpty { null }
}
