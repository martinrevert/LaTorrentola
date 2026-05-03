package com.martinrevert.latorrentola.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.martinrevert.latorrentola.ui.detail.DetailViewModel
import com.martinrevert.latorrentola.ui.detail.MovieDetailScreen
import com.martinrevert.latorrentola.ui.home.HomeScreen
import com.martinrevert.latorrentola.ui.home.HomeViewModel
import com.martinrevert.latorrentola.ui.search.SearchScreen
import com.martinrevert.latorrentola.ui.search.SearchViewModel
import com.martinrevert.latorrentola.ui.settings.SettingsScreen
import com.martinrevert.latorrentola.ui.settings.SettingsViewModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.martinrevert.latorrentola.model.YTS.Movie

@Serializable
sealed interface Route : NavKey {
    @Serializable object Home : Route
    @Serializable data class Detail(val movieJson: String) : Route
    @Serializable object Settings : Route
    @Serializable data class Search(val genre: String? = null) : Route
}

@Composable
fun AppNavigation(initialMovieJson: String? = null) {
    val backStack = rememberNavBackStack(Route.Home)

    // Handle Deep Link / Notification navigation
    LaunchedEffect(initialMovieJson) {
        initialMovieJson?.let {
            backStack.add(Route.Detail(it))
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            // Use the specific subclass in the entry definition
            entry<Route.Home> {
                val viewModel: HomeViewModel = hiltViewModel()
                HomeScreen(
                    viewModel = viewModel,
                    onMovieClick = { movie ->
                        val movieJson = Json.encodeToString(Movie.serializer(), movie)
                        backStack.add(Route.Detail(movieJson))
                    },
                    onSettingsClick = { backStack.add(Route.Settings) },
                    onSearchClick = { backStack.add(Route.Search()) },
                    onGenreClick = { genre ->
                        backStack.add(Route.Search(genre))
                    }
                )
            }
            entry<Route.Detail> { detailKey ->
                val viewModel: DetailViewModel = hiltViewModel()
                val movie = Json.decodeFromString(Movie.serializer(), detailKey.movieJson)
                viewModel.setMovie(movie)
                MovieDetailScreen(viewModel = viewModel, onBackClick = { backStack.removeLastOrNull() })
            }
            entry<Route.Settings> {
                val viewModel: SettingsViewModel = hiltViewModel()
                SettingsScreen(viewModel = viewModel, onBackClick = { backStack.removeLastOrNull() })
            }
            entry<Route.Search> { searchKey ->
                val viewModel: SearchViewModel = hiltViewModel()
                SearchScreen(
                    viewModel = viewModel,
                    initialGenre = searchKey.genre,
                    onMovieClick = { movie ->
                        val movieJson = Json.encodeToString(Movie.serializer(), movie)
                        backStack.add(Route.Detail(movieJson))
                    },
                    onBackClick = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}