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
import com.martinrevert.latorrentola.ui.detail.TvDetailScreen
import com.martinrevert.latorrentola.ui.detail.TvDetailViewModel
import com.martinrevert.latorrentola.ui.detail.TvEpisodeDetailScreen
import com.martinrevert.latorrentola.ui.detail.TvEpisodeDetailViewModel
import com.martinrevert.latorrentola.ui.home.HomeScreen
import com.martinrevert.latorrentola.ui.home.HomeViewModel
import com.martinrevert.latorrentola.ui.home.TvHomeViewModel
import com.martinrevert.latorrentola.ui.home.TvGenreResultsScreen
import com.martinrevert.latorrentola.ui.home.TvGenreResultsViewModel
import com.martinrevert.latorrentola.ui.search.SearchScreen
import com.martinrevert.latorrentola.ui.search.SearchViewModel
import com.martinrevert.latorrentola.ui.settings.SettingsScreen
import com.martinrevert.latorrentola.ui.settings.SettingsViewModel
import com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode
import com.martinrevert.latorrentola.model.YTS.Movie
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Serializable destinations supported by the app's navigation back stack. */
@Serializable
sealed interface Route : NavKey {
    /** Login destination. */
    @Serializable object Login : Route
    /** Home movie-feed destination. */
    @Serializable object Home : Route
    /**
     * Movie detail destination, initialized from one of the supported external payloads.
     *
     * @property movieJson Serialized YTS movie payload, when supplied.
     * @property movieId YTS movie identifier, when supplied.
     * @property query Search term such as an IMDb title identifier.
     */
    @Serializable data class Detail(
        val movieJson: String? = null, 
        val movieId: Int? = null,
        val query: String? = null
    ) : Route
    /** User settings destination. */
    @Serializable object Settings : Route
    /**
     * Search destination with optional genre or text query.
     *
     * @property genre Genre or collection key used to initialize the result list.
     * @property query Initial search text.
     */
    @Serializable data class Search(val genre: String? = null, val query: String? = null) : Route
    /**
     * TV genre discovery destination.
     *
     * @property genreId TMDB TV genre identifier.
     * @property genreName Localized genre label.
     */
    @Serializable data class TvGenre(val genreId: Int, val genreName: String) : Route
    /**
     * TV series detail destination.
     *
     * @property seriesId TMDB TV series identifier.
     */
    @Serializable data class TvDetail(val seriesId: Int) : Route
    /**
     * TV episode detail destination.
     *
     * @property seriesId TMDB TV series identifier.
     * @property seasonNumber Season number.
     * @property episodeNumber Episode number within season.
     * @property seriesName Series display name.
     * @property episodeJson Optional serialized [TmdbTvEpisode] payload.
     */
    @Serializable data class TvEpisodeDetail(
        val seriesId: Int,
        val seasonNumber: Int,
        val episodeNumber: Int,
        val seriesName: String = "",
        val episodeJson: String? = null
    ) : Route
}

/** Creates the authenticated navigation graph and consumes pending deep-link data. */
@Composable
fun AppNavigation(
    initialMovieJson: String? = null, 
    initialMovieId: Int? = null,
    initialSearchQuery: String? = null,
    onInitialDataHandled: () -> Unit = {}
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val isLoggedIn = authViewModel.currentUser != null
    
    val backStack = rememberNavBackStack(if (isLoggedIn) Route.Home else Route.Login)

    // Handle session expiry or logout from other parts of the app
    LaunchedEffect(authViewModel.currentUser) {
        if (authViewModel.currentUser == null && backStack.firstOrNull() != Route.Login) {
            backStack.clear()
            backStack.add(Route.Login)
        }
    }

    // Handle Deep Link / Notification navigation
    LaunchedEffect(initialMovieJson, initialMovieId, initialSearchQuery) {
        if (isLoggedIn && (initialMovieJson != null || initialMovieId != null || initialSearchQuery != null)) {
            initialMovieJson?.let {
                backStack.add(Route.Detail(movieJson = it))
            }
            initialMovieId?.let {
                backStack.add(Route.Detail(movieId = it))
            }
            initialSearchQuery?.let {
                // Prioritize Detail screen for a better user experience as requested
                backStack.add(Route.Detail(query = it))
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
                val tvHomeViewModel: TvHomeViewModel = hiltViewModel()
                HomeScreen(
                    viewModel = viewModel,
                    tvHomeViewModel = tvHomeViewModel,
                    userPhotoUrl = authViewModel.currentUser?.photoUrl?.toString(),
                    onMovieClick = { movie ->
                        val movieJson = Json.encodeToString(Movie.serializer(), movie)
                        backStack.add(Route.Detail(movieJson = movieJson))
                    },
                    onTvSeriesClick = { series ->
                        backStack.add(Route.TvDetail(series.id))
                    },
                    onSettingsClick = { backStack.add(Route.Settings) },
                    onSearchClick = { backStack.add(Route.Search()) },
                    onFavoritesClick = { backStack.add(Route.Search("milista")) },
                    onGenreClick = { genre ->
                        backStack.add(Route.Search(genre))
                    },
                    onTvGenreClick = { genre ->
                        backStack.add(Route.TvGenre(genre.id, genre.name))
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
                    } ?: detailKey.query?.let {
                        viewModel.setMovieByQuery(it)
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
                        backStack.clear()
                        backStack.add(Route.Login)
                    }
                )
            }
            entry<Route.Search> { searchKey ->
                val viewModel: SearchViewModel = hiltViewModel(key = searchKey.hashCode().toString())
                SearchScreen(
                    viewModel = viewModel,
                    initialGenre = searchKey.genre,
                    initialQuery = searchKey.query,
                    onMovieClick = { movie ->
                        val movieJson = Json.encodeToString(Movie.serializer(), movie)
                        backStack.add(Route.Detail(movieJson = movieJson))
                    },
                    onBackClick = { backStack.removeLastOrNull() }
                )
            }
            entry<Route.TvGenre> { genreKey ->
                val viewModel: TvGenreResultsViewModel = hiltViewModel(
                    key = "tv-genre-${genreKey.genreId}"
                )
                TvGenreResultsScreen(
                    viewModel = viewModel,
                    genreId = genreKey.genreId,
                    genreName = genreKey.genreName,
                    onSeriesClick = { series ->
                        backStack.add(Route.TvDetail(series.id))
                    },
                    onBackClick = { backStack.removeLastOrNull() }
                )
            }
            entry<Route.TvDetail> { detailKey ->
                val viewModel: TvDetailViewModel = hiltViewModel(
                    key = "tv-detail-${detailKey.seriesId}"
                )
                TvDetailScreen(
                    viewModel = viewModel,
                    seriesId = detailKey.seriesId,
                    onEpisodeClick = { seriesName, episode ->
                        val episodeJson = Json.encodeToString(TmdbTvEpisode.serializer(), episode)
                        backStack.add(
                            Route.TvEpisodeDetail(
                                seriesId = detailKey.seriesId,
                                seasonNumber = episode.seasonNumber,
                                episodeNumber = episode.episodeNumber,
                                seriesName = seriesName,
                                episodeJson = episodeJson
                            )
                        )
                    },
                    onBackClick = { backStack.removeLastOrNull() }
                )
            }
            entry<Route.TvEpisodeDetail> { episodeKey ->
                val viewModel: TvEpisodeDetailViewModel = hiltViewModel(
                    key = "tv-episode-${episodeKey.seriesId}-${episodeKey.seasonNumber}-${episodeKey.episodeNumber}"
                )
                TvEpisodeDetailScreen(
                    viewModel = viewModel,
                    seriesId = episodeKey.seriesId,
                    seasonNumber = episodeKey.seasonNumber,
                    episodeNumber = episodeKey.episodeNumber,
                    seriesName = episodeKey.seriesName,
                    episodeJson = episodeKey.episodeJson,
                    onBackClick = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}