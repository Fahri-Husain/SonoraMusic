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
import com.fahrii.musicapp.data.SettingsRepository
import com.fahrii.musicapp.data.isAlbumEntry
import com.fahrii.musicapp.data.toAlbumRef
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
private const val ROUTE_FOR_YOU = "for_you"
private const val ROUTE_NEW_RELEASES = "new_releases"
private const val ROUTE_TRENDING = "trending"
private const val ROUTE_GENRE = "genre?name={name}&query={query}&accent={accent}"
private const val ROUTE_GENRE_SONGS = "genre_songs"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_EQUALIZER = "equalizer"

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
    // Hanya sampul lagu yang diamati di sini, supaya pembungkus ini tidak recompose tiap state pemutar berubah
    val coverUrl by remember(playerViewModel) {
        playerViewModel.state.map { it.currentSong?.thumbnailUrl }.distinctUntilChanged()
    }.collectAsState(initial = null)

    // Pengaturan "Tema dari sampul": mati = tanpa sampul, jadi palet kembali ke warna bawaan
    val context = LocalContext.current
    val albumThemeEnabled by remember(context) {
        SettingsRepository.getInstance(context.applicationContext).albumTheme
    }.collectAsState()

    AlbumThemeHost(coverUrl = if (albumThemeEnabled) coverUrl else null) {
        AppRootContent(playerViewModel, libraryViewModel)
    }
}

@Composable
private fun AppRootContent(
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel
) {
    val navController = rememberNavController()
    val songMenu = remember { SongMenuController() }
    val homeViewModel: HomeViewModel = viewModel()
    val searchViewModel: SearchViewModel = viewModel()

    val playerState by playerViewModel.state.collectAsState()
    val library by libraryViewModel.state.collectAsState()
    val playHistory by libraryViewModel.playHistory.collectAsState()
    val downloadedUrls by libraryViewModel.downloadedUrls.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(libraryViewModel) {
        libraryViewModel.downloadEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()

    val route = backStackEntry?.destination?.route

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
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    val onPlayerScreen = showPlayer
    val currentTab = when (route) {
        ROUTE_LIBRARY, ROUTE_LIKED, ROUTE_DOWNLOADS, ROUTE_PLAYLIST, ROUTE_REPLAY, ROUTE_MOST_PLAYED, ROUTE_ARTIST, ROUTE_ALBUM -> BottomTab.COLLECTION
        ROUTE_SEARCH -> BottomTab.SEARCH
        else -> BottomTab.HOME
    }

    val backdrop = rememberGlassBackdrop()
    val homeListState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf {
            homeListState.firstVisibleItemIndex > 0 || homeListState.firstVisibleItemScrollOffset > 0
        }
    }
    val headerGlassAlpha by animateFloatAsState(if (scrolled) 1f else 0f, label = "headerGlass")
    var headerHeightPx by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    var barHeightPx by remember { mutableIntStateOf(0) }
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

    // Latar digambar oleh AlbumThemeHost (mengikuti sampul), jadi Surface ini transparan
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        CoverTransitionHost(modifier = Modifier.fillMaxSize()) {
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
                                onSeeAllForYou = { navController.navigate(ROUTE_FOR_YOU) },
                                onSeeAllNewReleases = { navController.navigate(ROUTE_NEW_RELEASES) },
                                onSeeAllTrending = { navController.navigate(ROUTE_TRENDING) },
                                topInset = with(density) { headerHeightPx.toDp() },
                                onPullRefresh = playerViewModel::recoverFromError
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
                            LaunchedEffect(homeState.newReleases.size) { homeViewModel.loadNewReleaseDurations() }
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
                                        openAlbum(tapped.toAlbumRef())
                                    } else {
                                        val playable = songs.filterNot { it.isAlbumEntry() }
                                        val start = tapped?.let { t -> playable.indexOfFirst { it.url == t.url } } ?: 0
                                        if (playable.isNotEmpty()) {
                                            playerViewModel.playQueue(playable, start.coerceAtLeast(0), "Rilis Baru")
                                        }
                                    }
                                }
                            )
                        }

                        composable(ROUTE_FOR_YOU) {
                            val homeState by homeViewModel.state.collectAsState()
                            PickDetailScreen(
                                pick = QuickPick(
                                    label = "Untukmu",
                                    subtitle = "Dari artis favoritmu dan yang mirip",
                                    query = "",
                                    songs = homeState.forYou
                                ),
                                currentSongUrl = playerState.currentSong?.url,
                                isPlaying = playerState.isPlaying,
                                onBack = { navController.popBackStack() },
                                onPlay = { songs, index -> playerViewModel.playQueue(songs, index, "Untukmu") }
                            )
                        }

                        composable(ROUTE_TRENDING) {
                            val homeState by homeViewModel.state.collectAsState()
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
                            val crossfadeDjMode by settingsViewModel.crossfadeDjMode.collectAsState()
                            val streamQuality by settingsViewModel.streamQuality.collectAsState()
                            val downloadQuality by settingsViewModel.downloadQuality.collectAsState()
                            val skipSilence by settingsViewModel.skipSilence.collectAsState()
                            val normalizeVolume by settingsViewModel.normalizeVolume.collectAsState()
                            val shakeToSkip by settingsViewModel.shakeToSkip.collectAsState()
                            val albumTheme by settingsViewModel.albumTheme.collectAsState()
                            val lyricsFontSize by settingsViewModel.lyricsFontSize.collectAsState()
                            val lyricsBlur by settingsViewModel.lyricsBlur.collectAsState()
                            val sleepTimerEndAt by settingsViewModel.sleepTimerEndAt.collectAsState()
                            val downloadWifiOnly by settingsViewModel.downloadWifiOnly.collectAsState()
                            val downloadedSongs by settingsViewModel.downloaded.collectAsState()
                            val cacheBytes by settingsViewModel.cacheBytes.collectAsState()
                            val backupBusy by settingsViewModel.backupBusy.collectAsState()
                            val backupOutcome by settingsViewModel.backupOutcome.collectAsState()
                            val equalizer by settingsViewModel.equalizer.collectAsState()
                            Box(Modifier.fillMaxSize()) {
                                SettingsScreen(
                                    onBack = { navController.popBackStack() },
                                    crossfadeEnabled = crossfadeEnabled,
                                    crossfadeSeconds = crossfadeSeconds,
                                    onCrossfadeEnabledChange = settingsViewModel::setCrossfadeEnabled,
                                    onCrossfadeSecondsChange = settingsViewModel::setCrossfadeSeconds,
                                    crossfadeDjMode = crossfadeDjMode,
                                    onCrossfadeDjModeChange = settingsViewModel::setCrossfadeDjMode,
                                    streamQuality = streamQuality,
                                    downloadQuality = downloadQuality,
                                    skipSilence = skipSilence,
                                    onStreamQualityChange = settingsViewModel::setStreamQuality,
                                    onDownloadQualityChange = settingsViewModel::setDownloadQuality,
                                    onSkipSilenceChange = settingsViewModel::setSkipSilence,
                                    normalizeVolume = normalizeVolume,
                                    onNormalizeVolumeChange = settingsViewModel::setNormalizeVolume,
                                    equalizerSummary = if (equalizer.enabled) equalizer.preset.label else "Mati",
                                    onOpenEqualizer = { navController.navigate(ROUTE_EQUALIZER) },
                                    shakeToSkip = shakeToSkip,
                                    onShakeToSkipChange = settingsViewModel::setShakeToSkip,
                                    albumTheme = albumTheme,
                                    onAlbumThemeChange = settingsViewModel::setAlbumTheme,
                                    lyricsFontSize = lyricsFontSize,
                                    onLyricsFontSizeChange = settingsViewModel::setLyricsFontSize,
                                    lyricsBlur = lyricsBlur,
                                    onLyricsBlurChange = settingsViewModel::setLyricsBlur,
                                    sleepTimerEndAt = sleepTimerEndAt,
                                    onSleepTimerSet = settingsViewModel::setSleepTimer,
                                    downloadWifiOnly = downloadWifiOnly,
                                    onDownloadWifiOnlyChange = settingsViewModel::setDownloadWifiOnly,
                                    downloadedCount = downloadedSongs.size,
                                    downloadedBytes = downloadedSongs.sumOf { it.sizeBytes },
                                    cacheBytes = cacheBytes,
                                    onClearCache = settingsViewModel::clearCache,
                                    onClearDownloads = settingsViewModel::removeAllDownloads,
                                    onClearSearchHistory = searchViewModel::clearHistory,
                                    onClearPlayHistory = libraryViewModel::clearPlayHistory,
                                    backupBusy = backupBusy,
                                    backupOutcome = backupOutcome,
                                    onExportBackup = settingsViewModel::exportBackup,
                                    onImportBackup = settingsViewModel::importBackup,
                                    onDismissBackupOutcome = settingsViewModel::dismissBackupOutcome
                                )
                            }
                        }

                        composable(ROUTE_EQUALIZER) {
                            val settingsViewModel: SettingsViewModel = viewModel()
                            val equalizer by settingsViewModel.equalizer.collectAsState()
                            Box(Modifier.fillMaxSize()) {
                                EqualizerScreen(
                                    state = equalizer,
                                    onBack = { navController.popBackStack() },
                                    onEnabledChange = settingsViewModel::setEqualizerEnabled,
                                    onPresetSelect = settingsViewModel::setEqualizerPreset,
                                    onBandChange = settingsViewModel::setEqualizerBand,
                                    onBassBoostChange = settingsViewModel::setBassBoost,
                                    onReset = settingsViewModel::resetEqualizer
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
                            val songAlbums by libraryViewModel.songAlbums.collectAsState()
                            val likedUrls = remember(library.liked) { library.liked.map { it.url }.toSet() }
                            val downloadingUrls by libraryViewModel.downloadingUrls.collectAsState()

                            if (playlist == null) {
                                LaunchedEffect(Unit) { navController.popBackStack() }
                            } else {
                                PlaylistScreen(
                                    playlist = playlist,
                                    playlists = library.playlists,
                                    likedUrls = likedUrls,
                                    songAlbums = songAlbums,
                                    currentSongUrl = playerState.currentSong?.url,
                                    isPlaying = playerState.isPlaying,
                                    onBack = { navController.popBackStack() },
                                    onPlay = { songs, index -> playerViewModel.playQueue(songs, index, "Playlist \"${playlist.name}\"", autoplay = false) },
                                    onRemoveSong = { libraryViewModel.removeFromPlaylist(playlist.id, it.url) },
                                    onMoveSong = { from, to -> libraryViewModel.moveInPlaylist(playlist.id, from, to) },
                                    onRename = { libraryViewModel.renamePlaylist(playlist.id, it) },
                                    onSetThumbnail = { libraryViewModel.setPlaylistThumbnail(playlist.id, it) },
                                    onDeletePlaylist = { libraryViewModel.deletePlaylist(playlist.id) },
                                    onAddToQueue = { playerViewModel.addToQueue(it, "Playlist \"${playlist.name}\"", autoplay = false) },
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

                AnimatedVisibility(
                    visible = !onPlayerScreen,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    // Bar memudar di tempat (tanpa slide) supaya sudah ada saat cover mendarat di mini player
                    enter = fadeIn(tween(200)),
                    // Memudar cepat, tapi dipertahankan di komposisi selama cover terbang (offset 0 = tidak bergerak)
                    exit = fadeOut(tween(150)) + slideOutVertically(tween(COVER_FLIGHT_MS)) { 0 }
                ) {
                    ProvideCoverVisibilityScope(this@AnimatedVisibility) {
                        SonoraBottomBars(
                            selected = currentTab,
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
                                    searchBarExpanded = !searchBarExpanded
                                } else {
                                    navController.navigateToTab(ROUTE_SEARCH)
                                }
                            },
                            onToggle = playerViewModel::togglePlayPause,
                            onPlayerClick = {
                                showPlayer = true
                            },
                            modifier = Modifier.onSizeChanged { barHeightPx = it.height }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showPlayer,
                    enter = slideInVertically(tween(COVER_FLIGHT_MS, easing = FastOutSlowInEasing)) { it / 3 } +
                            fadeIn(tween(300)),
                    exit = slideOutVertically(tween(COVER_FLIGHT_MS, easing = FastOutSlowInEasing)) { it / 3 } +
                            fadeOut(tween(300))
                ) {
                    ProvideCoverVisibilityScope(this@AnimatedVisibility) {
                        BackHandler { showPlayer = false }
                        NowPlayingScreen(
                            viewModel = playerViewModel,
                            libraryViewModel = libraryViewModel,
                            onBack = { showPlayer = false },
                            onOpenMenu = { songMenu.show(it) },
                            onOpenArtist = { name ->
                                showPlayer = false
                                navController.navigate("artist/${Uri.encode(name)}")
                            },
                            modifier = Modifier
                                .pointerInput(Unit) {}
                                .background(Sonora.Background)
                                .navigationBarsPadding()
                        )
                    }
                }

                SongMenuSheets(
                    controller = songMenu,
                    libraryViewModel = libraryViewModel,
                    playerViewModel = playerViewModel,
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
}

private fun NavHostController.navigateToTab(route: String) {
    if (popBackStack(route, inclusive = false)) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}