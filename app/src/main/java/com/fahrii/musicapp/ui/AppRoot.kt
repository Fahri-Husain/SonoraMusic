package com.fahrii.musicapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument


private const val ROUTE_HOME = "home"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_LIBRARY = "library"
private const val ROUTE_LIKED = "liked"
private const val ROUTE_PLAYLIST = "playlist/{id}"
private const val ROUTE_PLAYER = "player"

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
    val backStackEntry by navController.currentBackStackEntryAsState()

    val route = backStackEntry?.destination?.route
    val onPlayerScreen = route == ROUTE_PLAYER
    val currentTab = when (route) {
        ROUTE_LIBRARY, ROUTE_LIKED, ROUTE_PLAYLIST -> BottomTab.COLLECTION
        ROUTE_SEARCH -> BottomTab.SEARCH
        else -> BottomTab.HOME
    }

    // Sumber blur: halaman di belakang, bar mengambang mengambil gambarnya dari sini
    val backdrop = rememberGlassBackdrop()

    // Tinggi bar mengambang diukur, supaya daftar bisa di-scroll sampai tidak ketutup
    val density = LocalDensity.current
    var barHeightPx by remember { mutableIntStateOf(0) }
    val bottomInset = if (onPlayerScreen) 0.dp else with(density) { barHeightPx.toDp() }

    fun openSearch(query: String) {
        searchViewModel.onQueryChange(query)
        searchViewModel.search()
        navController.navigateToTab(ROUTE_SEARCH)
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
                        .glassSource(backdrop)
                        .statusBarsPadding()
                        .then(if (onPlayerScreen) Modifier.navigationBarsPadding() else Modifier)
                ) {
                    composable(ROUTE_HOME) {
                        HomeScreen(
                            viewModel = homeViewModel,
                            onPlay = playerViewModel::playQueue,
                            onGenreClick = ::openSearch,
                            onArtistClick = ::openSearch
                        )
                    }

                    composable(ROUTE_SEARCH) {
                        SearchScreen(
                            viewModel = searchViewModel,
                            onSongClick = { songs, index -> playerViewModel.playQueue(songs, index) }
                        )
                    }

                    composable(ROUTE_LIBRARY) {
                        LibraryScreen(
                            library = library,
                            onOpenLiked = { navController.navigate(ROUTE_LIKED) },
                            onOpenPlaylist = { id -> navController.navigate("playlist/$id") },
                            onCreatePlaylist = { name -> libraryViewModel.createPlaylist(name) }
                        )
                    }

                    composable(ROUTE_LIKED) {
                        SongListScreen(
                            title = "Lagu yang disukai",
                            songs = library.liked,
                            onBack = { navController.popBackStack() },
                            onPlay = playerViewModel::playQueue,
                            onRemove = { libraryViewModel.toggleLike(it) },
                            removeIcon = AppIcons.HeartFilled,
                            removeTint = HeartColor,
                            removeDescription = "Hapus dari disukai"
                        )
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
                            SongListScreen(
                                title = playlist.name,
                                songs = playlist.songs,
                                onBack = { navController.popBackStack() },
                                onPlay = playerViewModel::playQueue,
                                onRemove = { libraryViewModel.removeFromPlaylist(playlist.id, it.url) },
                                removeIcon = AppIcons.Trash,
                                removeTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                removeDescription = "Hapus dari playlist",
                                onDeleteList = { libraryViewModel.deletePlaylist(playlist.id) }
                            )
                        }
                    }

                    composable(ROUTE_PLAYER) {
                        NowPlayingScreen(
                            viewModel = playerViewModel,
                            libraryViewModel = libraryViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }

            // Bar mengambang (mini player + navbar), di atas halaman, bukan di dalam Scaffold
            if (!onPlayerScreen) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { barHeightPx = it.height }
                ) {
                    MiniPlayer(
                        state = playerState,
                        backdrop = backdrop,
                        onToggle = playerViewModel::togglePlayPause,
                        onClick = {
                            navController.navigate(ROUTE_PLAYER) { launchSingleTop = true }
                        }
                    )
                    SonoraBottomBar(
                        selected = currentTab,
                        backdrop = backdrop,
                        onSelect = { tab ->
                            navController.navigateToTab(
                                when (tab) {
                                    BottomTab.HOME -> ROUTE_HOME
                                    BottomTab.COLLECTION -> ROUTE_LIBRARY
                                    BottomTab.SEARCH -> ROUTE_SEARCH
                                }
                            )
                        }
                    )
                }
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