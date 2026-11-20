package com.fahrii.musicapp.ui

import android.R.attr.alpha
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.foundation.shape.RoundedCornerShape
import com.fahrii.musicapp.data.ArtistAlbum
import com.fahrii.musicapp.data.isAlbumEntry
import com.fahrii.musicapp.data.toAlbumRef
import androidx.compose.foundation.layout.padding
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext


private const val ROUTE_HOME = "home"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_LIBRARY = "library"
private const val ROUTE_LIKED = "liked"
private const val ROUTE_DOWNLOADS = "downloads"
private const val ROUTE_PLAYLIST = "playlist/{id}"
private const val ROUTE_PICK = "pick/{index}"
private const val ROUTE_REPLAY = "replay"
private const val ROUTE_ARTIST = "artist/{name}"
private const val ROUTE_ALBUM = "album?title={title}&artist={artist}&url={url}&cover={cover}"
private const val ROUTE_MOST_PLAYED = "most_played"
private const val ROUTE_NEW_RELEASES = "new_releases"
private const val ROUTE_TRENDING = "trending"
private const val ROUTE_GENRE = "genre?name={name}&query={query}&accent={accent}"
private const val ROUTE_GENRE_SONGS = "genre_songs"
private const val ROUTE_SETTINGS = "settings"

/** Lama geser Beranda <-> Koleksi (ms). */
internal const val TAB_SLIDE_MS = 350

/** +1: pindah ke tab di kanan (Beranda -> Koleksi), -1: ke tab di kiri (Koleksi -> Beranda), 0: bukan keduanya. */
private fun tabSlideDirection(from: String?, to: String?): Int = when {
    from == ROUTE_HOME && to == ROUTE_LIBRARY -> 1
    from == ROUTE_LIBRARY && to == ROUTE_HOME -> -1
    else -> 0
}

@Composable
fun AppRoot(
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel
) {
    val navController = rememberNavController()
    // Menu titik tiga di setiap lagu: layar memanggil songMenu.show(...), sheet-nya digambar di bawah
    val songMenu = remember { SongMenuController() }
    val homeViewModel: HomeViewModel = viewModel()
    val searchViewModel: SearchViewModel = viewModel()

    val playerState by playerViewModel.state.collectAsState()
    val library by libraryViewModel.state.collectAsState()
    val playHistory by libraryViewModel.playHistory.collectAsState()
    // Ikon "terunduh" di setiap baris lagu membaca daftar ini lewat LocalDownloadedUrls
    val downloadedUrls by libraryViewModel.downloadedUrls.collectAsState()
    val context = LocalContext.current

    // Pesan unduhan (mulai, selesai, gagal) tampil sebagai Toast di layar mana pun
    LaunchedEffect(libraryViewModel) {
        libraryViewModel.downloadEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()

    val route = backStackEntry?.destination?.route

    // Jejak rute [sebelumnya, sekarang], diisi listener navigasi sebelum recompose,
    // supaya header beranda tahu harus geser atau tidak saat berpindah Beranda <-> Koleksi
    val routeTrail = remember { arrayOfNulls<String>(2) }
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.route != routeTrail[1]) {
                routeTrail[0] = routeTrail[1]
                routeTrail[1] = destination.route
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    // Now Playing adalah lapisan di atas NavHost (bukan rute), supaya halaman yang sedang dibuka
    // tetap tampil di belakangnya saat player digeser turun.
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    // True kalau player ditutup lewat geser: layarnya sudah meluncur keluar sendiri, jadi tanpa animasi keluar lagi
    var playerClosedBySwipe by remember { mutableStateOf(false) }
    val onPlayerScreen = showPlayer
    val currentTab = when (route) {
        ROUTE_LIBRARY, ROUTE_LIKED, ROUTE_DOWNLOADS, ROUTE_PLAYLIST, ROUTE_REPLAY, ROUTE_MOST_PLAYED, ROUTE_ARTIST, ROUTE_ALBUM -> BottomTab.COLLECTION
        ROUTE_SEARCH -> BottomTab.SEARCH
        else -> BottomTab.HOME
    }

    // Sumber blur: halaman di belakang, bar mengambang mengambil gambarnya dari sini
    val backdrop = rememberGlassBackdrop()
    val homeListState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf {
            homeListState.firstVisibleItemIndex > 0 || homeListState.firstVisibleItemScrollOffset > 0
        }
    }
    val headerGlassAlpha by animateFloatAsState(if (scrolled) 1f else 0f, label = "headerGlass")
    var headerHeightPx by remember { mutableIntStateOf(0) }

    // Tinggi bar mengambang diukur, supaya daftar bisa di-scroll sampai tidak ketutup
    val density = LocalDensity.current
    var barHeightPx by remember { mutableIntStateOf(0) }
    // Bar bawah di halaman Cari: false = ringkas (bulatan cari + mini player),
    // true = navbar penuh seperti di Beranda. Selalu kembali ringkas saat berpindah halaman.
    var searchBarExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(route) { searchBarExpanded = false }
    val bottomInset = if (onPlayerScreen) 0.dp else with(density) { barHeightPx.toDp() }

    fun openSearch(query: String) {
        searchViewModel.onQueryChange(query)
        searchViewModel.search()
        navController.navigateToTab(ROUTE_SEARCH)
    }

    fun openGenre(name: String, query: String, accentArgb: Int) {
        navController.navigate(
            "genre?name=${Uri.encode(name)}&query=${Uri.encode(query)}&accent=$accentArgb"
        )
    }

    fun openAlbum(album: ArtistAlbum) {
        navController.navigate(
            "album?title=${Uri.encode(album.title)}" +
                    "&artist=${Uri.encode(album.artist)}" +
                    "&url=${Uri.encode(album.url)}" +
                    "&cover=${Uri.encode(album.coverUrl.orEmpty())}"
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            CompositionLocalProvider(
                LocalBottomInset provides bottomInset,
                LocalSongMenu provides songMenu,
                LocalDownloadedUrls provides downloadedUrls
            ) {
                NavHost(
                    navController = navController,
                    startDestination = ROUTE_HOME,
                    modifier = Modifier
                        .fillMaxSize()
                        .glassSource(backdrop),
                    // Beranda <-> Koleksi: geser horizontal (Beranda di kiri, Koleksi di kanan).
                    enterTransition = {
                        val dir = tabSlideDirection(initialState.destination.route, targetState.destination.route)
                        when {
                            dir > 0 -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing))
                            dir < 0 -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing))
                            else -> fadeIn(tween(300))
                        }
                    },
                    exitTransition = {
                        val dir = tabSlideDirection(initialState.destination.route, targetState.destination.route)
                        when {
                            dir > 0 -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing))
                            dir < 0 -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing))
                            else -> fadeOut(tween(300))
                        }
                    },
                    popEnterTransition = {
                        val dir = tabSlideDirection(initialState.destination.route, targetState.destination.route)
                        when {
                            dir > 0 -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing))
                            dir < 0 -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing))
                            else -> fadeIn(tween(300))
                        }
                    },
                    popExitTransition = {
                        val dir = tabSlideDirection(initialState.destination.route, targetState.destination.route)
                        when {
                            dir > 0 -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing))
                            dir < 0 -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing))
                            else -> fadeOut(tween(300))
                        }
                    }
                ) {
                    composable(ROUTE_HOME) {
                        HomeScreen(
                            viewModel = homeViewModel,
                            onPlay = { songs, index, source -> playerViewModel.playQueue(songs, index, source) },
                            onOpenAlbum = ::openAlbum,
                            onGenreClick = ::openGenre,
                            onArtistClick = { name -> navController.navigate("artist/${Uri.encode(name)}") },
                            listState = homeListState,
                            onPickClick = { navController.navigate("pick/$it") },
                            onSeeAllNewReleases = { navController.navigate(ROUTE_NEW_RELEASES) },
                            onSeeAllTrending = { navController.navigate(ROUTE_TRENDING) },
                            topInset = with(density) { headerHeightPx.toDp() }
                        )
                    }

                    composable(ROUTE_SEARCH) {
                        Box(Modifier.fillMaxSize().statusBarsPadding()) {
                            SearchScreen(
                                viewModel = searchViewModel,
                                currentSongUrl = playerState.currentSong?.url,
                                isPlaying = playerState.isPlaying,
                                onSongClick = { songs, index ->
                                    val q = searchViewModel.state.value.query.trim()
                                    playerViewModel.playQueue(songs, index, if (q.isNotEmpty()) "Pencarian \"$q\"" else "Pencarian")
                                },
                                onArtistClick = { name -> navController.navigate("artist/${Uri.encode(name)}") },
                                onAlbumClick = ::openAlbum
                            )
                        }
                    }

                    composable(ROUTE_LIBRARY) {
                        // Dikumpulkan di sini saja, supaya AppRoot tidak ikut recompose saat impor berjalan
                        val importState by libraryViewModel.importState.collectAsState()
                        Box(Modifier.fillMaxSize().statusBarsPadding()) {
                            LibraryScreen(
                                library = library,
                                history = playHistory,
                                currentSongUrl = playerState.currentSong?.url,
                                isPlaying = playerState.isPlaying,
                                onOpenFavorites = { navController.navigate(ROUTE_LIKED) },
                                onOpenReplay = { navController.navigate(ROUTE_REPLAY) },
                                onOpenMostPlayed = { navController.navigate(ROUTE_MOST_PLAYED) },
                                onOpenDownloads = { navController.navigate(ROUTE_DOWNLOADS) },
                                onOpenPlaylist = { id -> navController.navigate("playlist/$id") },
                                onCreatePlaylist = { name -> libraryViewModel.createPlaylist(name) },
                                onPlayHistory = { songs, index -> playerViewModel.playQueue(songs, index, "Riwayat") },
                                onRemoveHistory = { libraryViewModel.removeFromHistory(it.url) },
                                importState = importState,
                                onImportPlaylist = libraryViewModel::importPlaylist,
                                onCancelImport = libraryViewModel::cancelImport,
                                onImportConsumed = libraryViewModel::consumeImport
                            )
                        }
                    }

                    composable(ROUTE_REPLAY) {
                        // Dikumpulkan di sini saja, supaya AppRoot tidak ikut recompose saat statistik berubah
                        val replayStats by libraryViewModel.replayStats.collectAsState()
                        val artistImages by libraryViewModel.artistImages.collectAsState()
                        val songAlbums by libraryViewModel.songAlbums.collectAsState()
                        Box(Modifier.fillMaxSize()) {
                            ReplayScreen(
                                stats = replayStats,
                                artistImages = artistImages,
                                onRequestArtistImage = libraryViewModel::requestArtistImage,
                                songAlbums = songAlbums,
                                onRequestSongAlbum = libraryViewModel::requestSongAlbum,
                                onOpenArtist = { name -> navController.navigate("artist/${Uri.encode(name)}") },
                                onBack = { navController.popBackStack() },
                                onPlay = { songs, index -> playerViewModel.playQueue(songs, index, "Replay") },
                                currentSongUrl = playerState.currentSong?.url,
                                isPlaying = playerState.isPlaying
                            )
                        }
                    }

                    composable(
                        route = ROUTE_ARTIST,
                        arguments = listOf(navArgument("name") { type = NavType.StringType })
                    ) { entry ->
                        val name = entry.arguments?.getString("name").orEmpty()
                        val artistViewModel: ArtistViewModel = viewModel()
                        val artistState by artistViewModel.state.collectAsState()
                        LaunchedEffect(name) { artistViewModel.load(name) }

                        ArtistScreen(
                            state = artistState,
                            currentSongUrl = playerState.currentSong?.url,
                            isPlaying = playerState.isPlaying,
                            onBack = { navController.popBackStack() },
                            onPlay = { songs, index -> playerViewModel.playQueue(songs, index, "Artis \"$name\"") },
                            onAlbumClick = ::openAlbum
                        )
                    }

                    composable(
                        route = ROUTE_ALBUM,
                        arguments = listOf(
                            navArgument("title") { type = NavType.StringType; defaultValue = "" },
                            navArgument("artist") { type = NavType.StringType; defaultValue = "" },
                            navArgument("url") { type = NavType.StringType; defaultValue = "" },
                            navArgument("cover") { type = NavType.StringType; defaultValue = "" }
                        )
                    ) { entry ->
                        val title = entry.arguments?.getString("title").orEmpty()
                        val artist = entry.arguments?.getString("artist").orEmpty()
                        val url = entry.arguments?.getString("url").orEmpty()
                        val cover = entry.arguments?.getString("cover").orEmpty().ifBlank { null }
                        val albumViewModel: AlbumViewModel = viewModel()
                        val albumState by albumViewModel.state.collectAsState()
                        LaunchedEffect(url, title, artist) { albumViewModel.load(url, title, artist, cover) }

                        AlbumScreen(
                            state = albumState,
                            currentSongUrl = playerState.currentSong?.url,
                            isPlaying = playerState.isPlaying,
                            onBack = { navController.popBackStack() },
                            onOpenArtist = { name -> navController.navigate("artist/${Uri.encode(name)}") },
                            onPlay = { songs, index ->
                                playerViewModel.playQueue(songs, index, "Album \"${albumState.title}\"")
                            }
                        )
                    }

                    composable(
                        route = ROUTE_GENRE,
                        arguments = listOf(
                            navArgument("name") { type = NavType.StringType; defaultValue = "" },
                            navArgument("query") { type = NavType.StringType; defaultValue = "" },
                            navArgument("accent") { type = NavType.IntType; defaultValue = 0 }
                        )
                    ) { entry ->
                        val name = entry.arguments?.getString("name").orEmpty()
                        val query = entry.arguments?.getString("query").orEmpty()
                        val accent = androidx.compose.ui.graphics.Color(entry.arguments?.getInt("accent") ?: 0)
                        val genreViewModel: GenreViewModel = viewModel()
                        val genreState by genreViewModel.state.collectAsState()
                        LaunchedEffect(name, query) { genreViewModel.load(name, query) }

                        GenreScreen(
                            state = genreState,
                            accent = accent,
                            currentSongUrl = playerState.currentSong?.url,
                            isPlaying = playerState.isPlaying,
                            onBack = { navController.popBackStack() },
                            onPlay = { songs, index -> playerViewModel.playQueue(songs, index, "Genre \"$name\"") },
                            onOpenArtist = { artist -> navController.navigate("artist/${Uri.encode(artist)}") },
                            onOpenAlbum = ::openAlbum,
                            onSeeAllSongs = { navController.navigate(ROUTE_GENRE_SONGS) }
                        )
                    }

                    composable(ROUTE_GENRE_SONGS) {
                        // Daftar lengkap memakai data yang sama dengan halaman Genre di bawahnya
                        // (ViewModel milik entri halaman Genre), jadi tidak perlu memuat ulang
                        val genreEntry = remember(it) {
                            runCatching { navController.getBackStackEntry(ROUTE_GENRE) }.getOrNull()
                        }
                        if (genreEntry == null) {
                            LaunchedEffect(Unit) { navController.popBackStack() }
                        } else {
                            val genreViewModel: GenreViewModel = viewModel(viewModelStoreOwner = genreEntry)
                            val genreState by genreViewModel.state.collectAsState()
                            PickDetailScreen(
                                pick = QuickPick(
                                    label = "Lagu populer",
                                    subtitle = "Genre ${genreState.name}",
                                    query = "",
                                    songs = genreState.songs
                                ),
                                currentSongUrl = playerState.currentSong?.url,
                                isPlaying = playerState.isPlaying,
                                onBack = { navController.popBackStack() },
                                onPlay = { songs, index ->
                                    playerViewModel.playQueue(songs, index, "Genre \"${genreState.name}\"")
                                }
                            )
                        }
                    }

                    composable(ROUTE_NEW_RELEASES) {
                        val homeState by homeViewModel.state.collectAsState()
                        // Lagu rilis baru belum punya durasi; diambil sekarang karena daftarnya yang menampilkan
                        LaunchedEffect(homeState.newReleases.size) { homeViewModel.loadNewReleaseDurations() }
                        // Tampilan sama dengan detail Pilihan Cepat (cover mosaik, Putar/Acak, daftar bernomor)
                        PickDetailScreen(
                            pick = QuickPick(
                                label = "Rilis Baru",
                                subtitle = "Rilis terbaru pilihan",
                                query = "",
                                songs = homeState.newReleases
                            ),
                            currentSongUrl = playerState.currentSong?.url,
                            isPlaying = playerState.isPlaying,
                            onBack = { navController.popBackStack() },
                            onPlay = { songs, index ->
                                val tapped = songs.getOrNull(index)
                                if (tapped != null && tapped.isAlbumEntry()) {
                                    // Album/EP: buka halaman albumnya, bukan diputar
                                    openAlbum(tapped.toAlbumRef())
                                } else {
                                    // Putar semua/acak/klik lagu: album tidak ikut masuk antrean
                                    val playable = songs.filterNot { it.isAlbumEntry() }
                                    val start = tapped?.let { t -> playable.indexOfFirst { it.url == t.url } } ?: 0
                                    if (playable.isNotEmpty()) {
                                        playerViewModel.playQueue(playable, start.coerceAtLeast(0), "Rilis Baru")
                                    }
                                }
                            }
                        )
                    }

                    composable(ROUTE_TRENDING) {
                        val homeState by homeViewModel.state.collectAsState()
                        // Tampilan sama dengan detail Pilihan Cepat (cover mosaik, Putar/Acak, daftar bernomor)
                        PickDetailScreen(
                            pick = QuickPick(
                                label = "Lagu Trending",
                                subtitle = "Top chart saat ini",
                                query = "",
                                songs = homeState.trending
                            ),
                            currentSongUrl = playerState.currentSong?.url,
                            isPlaying = playerState.isPlaying,
                            onBack = { navController.popBackStack() },
                            onPlay = { songs, index -> playerViewModel.playQueue(songs, index, "Lagu Trending") }
                        )
                    }

                    composable(ROUTE_MOST_PLAYED) {
                        Box(Modifier.fillMaxSize().statusBarsPadding()) {
                            CollectionListScreen(
                                title = "Paling Sering Diputar",
                                songs = playHistory.sortedByDescending { it.plays }.map { it.song },
                                onBack = { navController.popBackStack() },
                                onPlay = { songs, index -> playerViewModel.playQueue(songs, index, "Paling Sering Diputar") },
                                currentSongUrl = playerState.currentSong?.url,
                                isPlaying = playerState.isPlaying,
                                removeLabel = "Hapus dari riwayat",
                                onRemove = { libraryViewModel.removeFromHistory(it.url) }
                            )
                        }
                    }

                    composable(ROUTE_SETTINGS) {
                        val settingsViewModel: SettingsViewModel = viewModel()
                        val crossfadeEnabled by settingsViewModel.crossfadeEnabled.collectAsState()
                        val crossfadeSeconds by settingsViewModel.crossfadeSeconds.collectAsState()
                        val streamQuality by settingsViewModel.streamQuality.collectAsState()
                        val downloadQuality by settingsViewModel.downloadQuality.collectAsState()
                        val skipSilence by settingsViewModel.skipSilence.collectAsState()
                        Box(Modifier.fillMaxSize().statusBarsPadding()) {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                crossfadeEnabled = crossfadeEnabled,
                                crossfadeSeconds = crossfadeSeconds,
                                onCrossfadeEnabledChange = settingsViewModel::setCrossfadeEnabled,
                                onCrossfadeSecondsChange = settingsViewModel::setCrossfadeSeconds,
                                streamQuality = streamQuality,
                                downloadQuality = downloadQuality,
                                skipSilence = skipSilence,
                                onStreamQualityChange = settingsViewModel::setStreamQuality,
                                onDownloadQualityChange = settingsViewModel::setDownloadQuality,
                                onSkipSilenceChange = settingsViewModel::setSkipSilence,
                                onClearSearchHistory = searchViewModel::clearHistory,
                                onClearPlayHistory = libraryViewModel::clearPlayHistory
                            )
                        }
                    }

                    composable(ROUTE_LIKED) {
                        Box(Modifier.fillMaxSize().statusBarsPadding()) {
                            CollectionListScreen(
                                title = "Favorit",
                                songs = library.liked,
                                onBack = { navController.popBackStack() },
                                onPlay = { songs, index -> playerViewModel.playQueue(songs, index, "Lagu yang disukai") },
                                currentSongUrl = playerState.currentSong?.url,
                                isPlaying = playerState.isPlaying
                            )
                        }
                    }

                    composable(ROUTE_DOWNLOADS) {
                        // Dikumpulkan di sini saja, supaya AppRoot tidak ikut recompose saat daftar unduhan berubah
                        val downloadedSongs by libraryViewModel.downloadedSongs.collectAsState()
                        Box(Modifier.fillMaxSize().statusBarsPadding()) {
                            CollectionListScreen(
                                title = "Diunduh",
                                songs = downloadedSongs,
                                onBack = { navController.popBackStack() },
                                onPlay = { songs, index -> playerViewModel.playQueue(songs, index, "Diunduh") },
                                currentSongUrl = playerState.currentSong?.url,
                                isPlaying = playerState.isPlaying
                            )
                        }
                    }

                    composable(
                        route = ROUTE_PLAYLIST,
                        arguments = listOf(navArgument("id") { type = NavType.StringType })
                    ) { entry ->
                        val id = entry.arguments?.getString("id")
                        val playlist = library.playlists.firstOrNull { it.id == id }
                        // Dikumpulkan di sini saja, supaya AppRoot tidak ikut recompose saat nama album ditemukan
                        val songAlbums by libraryViewModel.songAlbums.collectAsState()
                        val likedUrls = remember(library.liked) { library.liked.map { it.url }.toSet() }
                        val downloadingUrls by libraryViewModel.downloadingUrls.collectAsState()

                        if (playlist == null) {
                            LaunchedEffect(Unit) { navController.popBackStack() }
                        } else {
                            // Cover memenuhi layar sampai bawah status bar, jadi tanpa statusBarsPadding
                            PlaylistScreen(
                                playlist = playlist,
                                playlists = library.playlists,
                                likedUrls = likedUrls,
                                songAlbums = songAlbums,
                                currentSongUrl = playerState.currentSong?.url,
                                isPlaying = playerState.isPlaying,
                                onBack = { navController.popBackStack() },
                                onPlay = { songs, index -> playerViewModel.playQueue(songs, index, "Playlist \"${playlist.name}\"") },
                                onRemoveSong = { libraryViewModel.removeFromPlaylist(playlist.id, it.url) },
                                onMoveSong = { from, to -> libraryViewModel.moveInPlaylist(playlist.id, from, to) },
                                onRename = { libraryViewModel.renamePlaylist(playlist.id, it) },
                                onSetThumbnail = { libraryViewModel.setPlaylistThumbnail(playlist.id, it) },
                                onDeletePlaylist = { libraryViewModel.deletePlaylist(playlist.id) },
                                onAddToQueue = { playerViewModel.addToQueue(it, "Playlist \"${playlist.name}\"") },
                                onPlayNext = playerViewModel::playNext,
                                onToggleLike = libraryViewModel::toggleLike,
                                onAddToPlaylist = { target, song -> libraryViewModel.addToPlaylist(target.id, song) },
                                onRemoveFromPlaylist = { target, song -> libraryViewModel.removeFromPlaylist(target.id, song.url) },
                                onRequestAlbum = libraryViewModel::requestSongAlbum,
                                onOpenArtist = { name -> navController.navigate("artist/${Uri.encode(name)}") },
                                onOpenAlbum = ::openAlbum,
                                downloadedUrls = downloadedUrls,
                                downloadingUrls = downloadingUrls,
                                onDownloadSong = libraryViewModel::downloadSong,
                                onDownloadAll = libraryViewModel::downloadSongs,
                                onRemoveDownload = { libraryViewModel.removeDownload(it.url) }
                            )
                        }
                    }

                    composable(
                        route = ROUTE_PICK,
                        arguments = listOf(navArgument("index") { type = NavType.IntType })
                    ) { entry ->
                        val index = entry.arguments?.getInt("index") ?: 0
                        val homeState by homeViewModel.state.collectAsState()
                        val pick = homeState.quickPicks.getOrNull(index)

                        if (pick == null) {
                            LaunchedEffect(Unit) { navController.popBackStack() }
                        } else {
                            PickDetailScreen(
                                pick = pick,
                                currentSongUrl = playerState.currentSong?.url,
                                isPlaying = playerState.isPlaying,
                                onBack = { navController.popBackStack() },
                                onPlay = { songs, index -> playerViewModel.playQueue(songs, index, "Pilihan Cepat \"${pick.label}\"") }
                            )
                        }
                    }
                }
            }

            // Header ikut geser bersama halaman saat Beranda <-> Koleksi; selain itu muncul/hilang seperti biasa
            AnimatedVisibility(
                visible = route == ROUTE_HOME,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = if (routeTrail[0] == ROUTE_LIBRARY) {
                    slideInHorizontally(tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing)) { -it }
                } else EnterTransition.None,
                exit = if (routeTrail[1] == ROUTE_LIBRARY) {
                    slideOutHorizontally(tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing)) { -it }
                } else ExitTransition.None
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { headerHeightPx = it.height }
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { alpha = headerGlassAlpha }
                            .glass(
                                backdrop,
                                RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                            )
                    )
                    HomeHeader(
                        onHistoryClick = {},
                        onSettingsClick = { navController.navigate(ROUTE_SETTINGS) },
                        modifier = Modifier.statusBarsPadding()
                    )
                }
            }

            // Bar mengambang (mini player + navbar), di atas halaman, bukan di dalam Scaffold
            AnimatedVisibility(
                visible = !onPlayerScreen,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(350, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(300)),
                exit = fadeOut(tween(150))
            ) {
                SonoraBottomBars(
                    selected = currentTab,
                    // Halaman Cari: bar ringkas (bulatan cari di kiri, mini player di kanannya).
                    // Ketuk bulatan cari lagi untuk memunculkan navbar + mini player seperti di Beranda.
                    compact = route == ROUTE_SEARCH && !searchBarExpanded,
                    playerState = playerState,
                    backdrop = backdrop,
                    onSelect = { tab ->
                        navController.navigateToTab(
                            when (tab) {
                                BottomTab.HOME -> ROUTE_HOME
                                BottomTab.COLLECTION -> ROUTE_LIBRARY
                                BottomTab.SEARCH -> ROUTE_SEARCH
                            }
                        )
                    },
                    onSearchClick = {
                        if (route == ROUTE_SEARCH) {
                            // Sudah di halaman Cari: bolak-balik antara bar ringkas dan bar penuh
                            searchBarExpanded = !searchBarExpanded
                        } else {
                            navController.navigateToTab(ROUTE_SEARCH)
                        }
                    },
                    onToggle = playerViewModel::togglePlayPause,
                    onPlayerClick = {
                        playerClosedBySwipe = false
                        showPlayer = true
                    },
                    modifier = Modifier.onSizeChanged { barHeightPx = it.height }
                )
            }

            // Now Playing: naik dari bawah saat dibuka, turun lagi saat kembali. Berada di atas
            // NavHost dan bar bawah, jadi halaman di belakangnya tetap terlihat saat digeser turun.
            AnimatedVisibility(
                visible = showPlayer,
                enter = slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it },
                exit = if (playerClosedBySwipe) {
                    ExitTransition.None
                } else {
                    slideOutVertically(tween(340, easing = FastOutSlowInEasing)) { it }
                }
            ) {
                // Didaftarkan saat player dibuka, jadi lebih diprioritaskan daripada tombol kembali halaman di belakangnya
                BackHandler { showPlayer = false }
                NowPlayingScreen(
                    viewModel = playerViewModel,
                    libraryViewModel = libraryViewModel,
                    onBack = { showPlayer = false },
                    onOpenMenu = { songMenu.show(it) },
                    // Tutup pemutarnya dulu supaya halaman artis terlihat
                    onOpenArtist = { name ->
                        showPlayer = false
                        navController.navigate("artist/${Uri.encode(name)}")
                    },
                    onSwipeDismiss = {
                        playerClosedBySwipe = true
                        showPlayer = false
                    },
                    modifier = Modifier
                        .pointerInput(Unit) {} // cegah ketukan tembus ke halaman di belakang
                        .background(Sonora.Background)
                        .navigationBarsPadding()
                )
            }

            // Bottom sheet menu titik tiga lagu (satu untuk semua layar)
            SongMenuSheets(
                controller = songMenu,
                libraryViewModel = libraryViewModel,
                playerViewModel = playerViewModel,
                // Kalau menu dibuka dari layar pemutar, tutup pemutarnya dulu supaya halaman tujuan terlihat
                onOpenArtist = { name ->
                    showPlayer = false
                    navController.navigate("artist/${Uri.encode(name)}")
                },
                onOpenAlbum = { album ->
                    showPlayer = false
                    openAlbum(album)
                }
            )
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}