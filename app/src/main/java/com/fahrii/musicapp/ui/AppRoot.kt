package com.fahrii.musicapp.ui

import android.R.attr.alpha
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.foundation.layout.padding
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext


private const val ROUTE_HOME = "home"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_LIBRARY = "library"
private const val ROUTE_LIKED = "liked"
private const val ROUTE_PLAYLIST = "playlist/{id}"
private const val ROUTE_PLAYER = "player"
private const val ROUTE_PICK = "pick/{index}"
private const val ROUTE_REPLAY = "replay"
private const val ROUTE_ARTIST = "artist/{name}"
private const val ROUTE_ALBUM = "album?title={title}&artist={artist}&url={url}&cover={cover}"
private const val ROUTE_MOST_PLAYED = "most_played"

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
    val homeViewModel: HomeViewModel = viewModel()
    val searchViewModel: SearchViewModel = viewModel()

    val playerState by playerViewModel.state.collectAsState()
    val library by libraryViewModel.state.collectAsState()
    val playHistory by libraryViewModel.playHistory.collectAsState()
    val context = LocalContext.current
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
    val onPlayerScreen = route == ROUTE_PLAYER
    val currentTab = when (route) {
        ROUTE_LIBRARY, ROUTE_LIKED, ROUTE_PLAYLIST, ROUTE_REPLAY, ROUTE_MOST_PLAYED, ROUTE_ARTIST, ROUTE_ALBUM -> BottomTab.COLLECTION
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

            CompositionLocalProvider(LocalBottomInset provides bottomInset) {
                NavHost(
                    navController = navController,
                    startDestination = ROUTE_HOME,
                    modifier = Modifier
                        .fillMaxSize()
                        .glassSource(backdrop),
                    // Player naik dari bawah saat dibuka dan turun lagi saat kembali (hanya geser,
                    // tanpa fade, supaya ringan). Halaman di belakangnya diam.
                    // Beranda <-> Koleksi: geser horizontal (Beranda di kiri, Koleksi di kanan).
                    enterTransition = {
                        val dir = tabSlideDirection(initialState.destination.route, targetState.destination.route)
                        when {
                            targetState.destination.route == ROUTE_PLAYER ->
                                slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it }
                            dir > 0 -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing))
                            dir < 0 -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing))
                            else -> fadeIn(tween(300))
                        }
                    },
                    exitTransition = {
                        val dir = tabSlideDirection(initialState.destination.route, targetState.destination.route)
                        when {
                            // Halaman di belakang diam; player menutupinya sambil naik
                            targetState.destination.route == ROUTE_PLAYER -> ExitTransition.None
                            dir > 0 -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing))
                            dir < 0 -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing))
                            else -> fadeOut(tween(300))
                        }
                    },
                    popEnterTransition = {
                        val dir = tabSlideDirection(initialState.destination.route, targetState.destination.route)
                        when {
                            initialState.destination.route == ROUTE_PLAYER -> EnterTransition.None
                            dir > 0 -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing))
                            dir < 0 -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing))
                            else -> fadeIn(tween(300))
                        }
                    },
                    popExitTransition = {
                        val dir = tabSlideDirection(initialState.destination.route, targetState.destination.route)
                        when {
                            initialState.destination.route == ROUTE_PLAYER ->
                                slideOutVertically(tween(340, easing = FastOutSlowInEasing)) { it }
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
                            onGenreClick = ::openSearch,
                            onArtistClick = { name -> navController.navigate("artist/${Uri.encode(name)}") },
                            listState = homeListState,
                            onPickClick = { navController.navigate("pick/$it") },
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
                        Box(Modifier.fillMaxSize().statusBarsPadding()) {
                            LibraryScreen(
                                library = library,
                                history = playHistory,
                                currentSongUrl = playerState.currentSong?.url,
                                isPlaying = playerState.isPlaying,
                                onOpenFavorites = { navController.navigate(ROUTE_LIKED) },
                                onOpenReplay = { navController.navigate(ROUTE_REPLAY) },
                                onOpenMostPlayed = { navController.navigate(ROUTE_MOST_PLAYED) },
                                onOpenDownloads = {
                                    Toast.makeText(context, "Fitur unduhan belum tersedia", Toast.LENGTH_SHORT).show()
                                },
                                onOpenPlaylist = { id -> navController.navigate("playlist/$id") },
                                onCreatePlaylist = { name -> libraryViewModel.createPlaylist(name) },
                                onPlayHistory = { songs, index -> playerViewModel.playQueue(songs, index, "Riwayat") },
                                onRemoveHistory = { libraryViewModel.removeFromHistory(it.url) }
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

                    composable(ROUTE_MOST_PLAYED) {
                        Box(Modifier.fillMaxSize().statusBarsPadding()) {
                            SongListScreen(
                                title = "Paling Sering Diputar",
                                songs = playHistory.sortedByDescending { it.plays }.map { it.song },
                                onBack = { navController.popBackStack() },
                                onPlay = { songs, index -> playerViewModel.playQueue(songs, index, "Paling Sering Diputar") },
                                onRemove = { libraryViewModel.removeFromHistory(it.url) },
                                removeIcon = AppIcons.Trash,
                                removeTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                removeDescription = "Hapus dari riwayat",
                                currentSongUrl = playerState.currentSong?.url,
                                isPlaying = playerState.isPlaying
                            )
                        }
                    }

                    composable(ROUTE_LIKED) {
                        Box(Modifier.fillMaxSize().statusBarsPadding()) {
                            SongListScreen(
                                title = "Lagu yang disukai",
                                songs = library.liked,
                                onBack = { navController.popBackStack() },
                                onPlay = { songs, index -> playerViewModel.playQueue(songs, index, "Lagu yang disukai") },
                                onRemove = { libraryViewModel.toggleLike(it) },
                                removeIcon = AppIcons.HeartFilled,
                                removeTint = HeartColor,
                                removeDescription = "Hapus dari disukai",
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

                        if (playlist == null) {
                            LaunchedEffect(Unit) { navController.popBackStack() }
                        } else {
                            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                                SongListScreen(
                                    title = playlist.name,
                                    songs = playlist.songs,
                                    onBack = { navController.popBackStack() },
                                    onPlay = { songs, index -> playerViewModel.playQueue(songs, index, "Playlist \"${playlist.name}\"") },
                                    onRemove = { libraryViewModel.removeFromPlaylist(playlist.id, it.url) },
                                    removeIcon = AppIcons.Trash,
                                    removeTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    removeDescription = "Hapus dari playlist",
                                    currentSongUrl = playerState.currentSong?.url,
                                    isPlaying = playerState.isPlaying,
                                    onDeleteList = { libraryViewModel.deletePlaylist(playlist.id) }
                                )
                            }
                        }
                    }

                    composable(ROUTE_PLAYER) {
                        NowPlayingScreen(
                            viewModel = playerViewModel,
                            libraryViewModel = libraryViewModel,
                            onBack = { navController.popBackStack() },
                            modifier = Modifier
                                .background(Sonora.Background)
                                .navigationBarsPadding()
                        )
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
                        onSettingsClick = {},
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
                        navController.navigate(ROUTE_PLAYER) { launchSingleTop = true }
                    },
                    modifier = Modifier.onSizeChanged { barHeightPx = it.height }
                )
            }
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