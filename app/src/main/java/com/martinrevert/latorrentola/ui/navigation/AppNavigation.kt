package com.martinrevert.latorrentola.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.martinrevert.latorrentola.ui.auth.AuthViewModel
import com.martinrevert.latorrentola.ui.auth.LoginScreen
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
    @Serializable object Login : Route
    @Serializable object Home : Route
    @Serializable data class Detail(val movieJson: String? = null, val movieId: Int? = null) : Route
    @Serializable object Settings : Route
    @Serializable data class Search(val genre: String? = null) : Route
}

@Composable
fun AppNavigation(
    initialMovieJson: String? = null, 
    initialMovieId: Int? = null,
    onInitialDataHandled: () -> Unit = {}
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val isLoggedIn = authViewModel.currentUser != null
    
    val backStack = rememberNavBackStack(if (isLoggedIn) Route.Home else Route.Login)

    // Handle session expiry or logout from other parts of the app
    LaunchedEffect(authViewModel.currentUser) {
        if (authViewModel.currentUser == null && backStack.firstOrNull() != Route.Login) {
            while (backStack.isNotEmpty()) {
                backStack.removeLastOrNull()
            }
            backStack.add(Route.Login)
        }
    }

    // Handle Deep Link / Notification navigation
    LaunchedEffect(initialMovieJson, initialMovieId) {
        if (isLoggedIn && (initialMovieJson != null || initialMovieId != null)) {
            initialMovieJson?.let {
                backStack.add(Route.Detail(movieJson = it))
            }
            initialMovieId?.let {
                backStack.add(Route.Detail(movieId = it))
            }
            onInitialDataHandled()
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Route.Login> {
                LoginScreen(
                    viewModel = authViewModel,
                    onLoginSuccess = {
                        while (backStack.isNotEmpty()) {
                            backStack.removeLastOrNull()
                        }
                        backStack.add(Route.Home)
                    }
                )
            }
            // Use the specific subclass in the entry definition
            entry<Route.Home> {
                val viewModel: HomeViewModel = hiltViewModel()
                HomeScreen(
                    viewModel = viewModel,
                    userPhotoUrl = authViewModel.currentUser?.photoUrl?.toString(),
                    onMovieClick = { movie ->
                        val movieJson = Json.encodeToString(Movie.serializer(), movie)
                        backStack.add(Route.Detail(movieJson = movieJson))
                    },
                    onSettingsClick = { backStack.add(Route.Settings) },
                    onSearchClick = { backStack.add(Route.Search()) },
                    onFavoritesClick = { backStack.add(Route.Search("milista")) },
                    onGenreClick = { genre ->
                        backStack.add(Route.Search(genre))
                    }
                )
            }
            entry<Route.Detail> { detailKey ->
                val viewModel: DetailViewModel = hiltViewModel(key = detailKey.hashCode().toString())
                LaunchedEffect(detailKey) {
                    detailKey.movieJson?.let {
                        val movie = Json.decodeFromString(Movie.serializer(), it)
                        viewModel.setMovie(movie)
                    } ?: detailKey.movieId?.let {
                        viewModel.setMovieById(it)
                    }
                }
                MovieDetailScreen(viewModel = viewModel, onBackClick = { backStack.removeLastOrNull() })
            }
            entry<Route.Settings> {
                val viewModel: SettingsViewModel = hiltViewModel()
                val user = authViewModel.currentUser
                SettingsScreen(
                    viewModel = viewModel,
                    userPhotoUrl = user?.photoUrl?.toString(),
                    userName = user?.displayName,
                    userEmail = user?.email,
                    onBackClick = { backStack.removeLastOrNull() },
                    onLogoutClick = {
                        authViewModel.signOut()
                        while (backStack.isNotEmpty()) {
                            backStack.removeLastOrNull()
                        }
                        backStack.add(Route.Login)
                    }
                )
            }
            entry<Route.Search> { searchKey ->
                val viewModel: SearchViewModel = hiltViewModel(key = searchKey.hashCode().toString())
                SearchScreen(
                    viewModel = viewModel,
                    initialGenre = searchKey.genre,
                    onMovieClick = { movie ->
                        val movieJson = Json.encodeToString(Movie.serializer(), movie)
                        backStack.add(Route.Detail(movieJson = movieJson))
                    },
                    onBackClick = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}