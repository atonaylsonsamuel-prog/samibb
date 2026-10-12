package com.samdevelop.samtv.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.samdevelop.samtv.SamTvApplication
import com.samdevelop.samtv.core.model.ContentType
import com.samdevelop.samtv.core.panel.AppPhase
import com.samdevelop.samtv.ui.screens.AccountScreen
import com.samdevelop.samtv.ui.screens.ChannelPlayerScreen
import com.samdevelop.samtv.ui.screens.DashboardActions
import com.samdevelop.samtv.ui.screens.DashboardScreen
import com.samdevelop.samtv.ui.screens.FavoritesScreen
import com.samdevelop.samtv.ui.screens.HistoryScreen
import com.samdevelop.samtv.ui.screens.LiveTvScreen
import com.samdevelop.samtv.ui.screens.LoginScreen
import com.samdevelop.samtv.ui.screens.MovieDetailsScreen
import com.samdevelop.samtv.ui.screens.MoviesScreen
import com.samdevelop.samtv.ui.screens.PanelGateScreen
import com.samdevelop.samtv.ui.screens.SearchScreen
import com.samdevelop.samtv.ui.screens.SeriesDetailsScreen
import com.samdevelop.samtv.ui.screens.SeriesScreen
import com.samdevelop.samtv.ui.screens.SettingsScreen
import com.samdevelop.samtv.ui.screens.serverKeyOf
import com.samdevelop.samtv.ui.theme.SamColors

private fun optionalString(name: String) = navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null }

/**
 * Raiz do app. O painel decide o que aparece: enquanto o aparelho não está liberado
 * (ativação, vencido, bloqueado, sem conexão, atualização obrigatória) mostra a tela do painel;
 * liberado, mostra a tela inicial e as demais telas.
 */
@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as SamTvApplication).container }
    val panel by container.panel.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(SamColors.Background)) {
        if (panel.phase == AppPhase.READY) MainNavHost() else PanelGateScreen(panel)
    }
}

@Composable
private fun MainNavHost(nav: NavHostController = rememberNavController()) {
    val backStack by nav.currentBackStackEntryAsState()
    val inPlayer = backStack?.destination?.route == Routes.PLAYER
    Box(Modifier.fillMaxSize().then(if (inPlayer) Modifier else Modifier.navigationBarsPadding())) {
        NavGraph(nav)
    }
}

@Composable
private fun NavGraph(nav: NavHostController) {
    fun openItem(type: ContentType, id: String, title: String, parentId: String?) {
        when (type) {
            ContentType.CHANNEL -> nav.navigate(Routes.player(ContentType.CHANNEL, id, title))
            ContentType.MOVIE -> nav.navigate(Routes.movie(id))
            ContentType.SERIES -> nav.navigate(Routes.series(id))
            ContentType.EPISODE -> parentId?.let { nav.navigate(Routes.series(it)) }
        }
    }
    val back: () -> Unit = { nav.popBackStack() }

    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            DashboardScreen(
                DashboardActions(
                    onLive = { nav.navigate(Routes.live()) },
                    onMovies = { nav.navigate(Routes.MOVIES) },
                    onSeries = { nav.navigate(Routes.SERIES) },
                    onAccount = { nav.navigate(Routes.ACCOUNT) },
                    onSearch = { nav.navigate(Routes.SEARCH) },
                    onFavorites = { nav.navigate(Routes.FAVORITES) },
                    onHistory = { nav.navigate(Routes.HISTORY) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                ),
            )
        }
        composable(Routes.ACCOUNT) {
            AccountScreen(onBack = back, onLogin = { nav.navigate(Routes.LOGIN) })
        }
        composable(Routes.LOGIN) {
            LoginScreen(onBack = back, onDone = { nav.popBackStack(Routes.HOME, inclusive = false) })
        }
        composable(Routes.LIVE, arguments = listOf(optionalString("categoryId"))) { entry ->
            LiveTvScreen(
                initialCategory = entry.arguments?.getString("categoryId"),
                onPlay = { ch, cat -> nav.navigate(Routes.player(ContentType.CHANNEL, ch.id, ch.name, categoryId = cat)) },
                onBack = back,
            )
        }
        composable(Routes.MOVIES) {
            MoviesScreen(onOpen = { nav.navigate(Routes.movie(it.id)) }, onBack = back)
        }
        composable(Routes.SERIES) {
            SeriesScreen(onOpen = { nav.navigate(Routes.series(it.id)) }, onBack = back)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = back, onLogin = { nav.navigate(Routes.LOGIN) })
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = back,
                onPlayChannel = { nav.navigate(Routes.player(ContentType.CHANNEL, it.id, it.name)) },
                onOpenMovie = { nav.navigate(Routes.movie(it.id)) },
                onOpenSeries = { nav.navigate(Routes.series(it.id)) },
            )
        }
        composable(Routes.FAVORITES) {
            FavoritesScreen(onBack = back, onOpen = { type, id, title -> openItem(type, id, title, null) })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(onBack = back, onOpen = { h -> openItem(h.type, h.contentId, h.title, h.parentId) })
        }
        composable(Routes.MOVIE_DETAILS, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            MovieDetailsScreen(
                onBack = back,
                onPlay = { movie, ext, resume ->
                    nav.navigate(
                        Routes.player(ContentType.MOVIE, movie.id, movie.name, key = serverKeyOf(movie.id), ext = ext, image = movie.posterUrl, startMs = resume),
                    )
                },
            )
        }
        composable(Routes.SERIES_DETAILS, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            SeriesDetailsScreen(
                onBack = back,
                onPlayEpisode = { series, ep, resume ->
                    val label = buildString {
                        append(series.name)
                        append(" · T").append(ep.seasonNumber)
                        ep.episodeNumber?.let { append("E").append(it) }
                        if (ep.title.isNotBlank()) append(" · ").append(ep.title)
                    }
                    nav.navigate(
                        Routes.player(
                            ContentType.EPISODE, ep.id, label, key = serverKeyOf(ep.id), ext = ep.containerExtension,
                            image = ep.imageUrl ?: series.coverUrl, parent = series.id, startMs = resume,
                        ),
                    )
                },
            )
        }
        composable(
            Routes.PLAYER,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                optionalString("id"), optionalString("key"), optionalString("ext"), optionalString("title"),
                optionalString("image"), optionalString("parent"), optionalString("cat"), optionalString("start"),
            ),
        ) {
            ChannelPlayerScreen(onBack = back)
        }
    }
}
