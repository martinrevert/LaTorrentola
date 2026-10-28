@file:Suppress("FunctionName")

package com.martinrevert.latorrentola.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.martinrevert.latorrentola.model.TMDB.TmdbCastCredit
import com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.ui.auth.AuthViewModel
import com.martinrevert.latorrentola.ui.auth.LoginScreen
import com.martinrevert.latorrentola.ui.detail.DetailViewModel
import com.martinrevert.latorrentola.ui.detail.MovieDetailScreen
import com.martinrevert.latorrentola.ui.detail.TvDetailScreen
import com.martinrevert.latorrentola.ui.detail.TvDetailViewModel
import com.martinrevert.latorrentola.ui.detail.TvEpisodeDetailScreen
import com.martinrevert.latorrentola.ui.detail.TvEpisodeDetailViewModel
import com.martinrevert.latorrentola.ui.downloads.TorrentDownloadsScreen
import com.martinrevert.latorrentola.ui.home.HomeScreen
import com.martinrevert.latorrentola.ui.home.HomeViewModel
import com.martinrevert.latorrentola.ui.home.TvGenreResultsScreen
import com.martinrevert.latorrentola.ui.home.TvGenreResultsViewModel
import com.martinrevert.latorrentola.ui.home.TvHomeViewModel
import com.martinrevert.latorrentola.ui.media.ExternalMediaBrowserScreen
import com.martinrevert.latorrentola.ui.search.SearchScreen
import com.martinrevert.latorrentola.ui.search.SearchViewModel
import com.martinrevert.latorrentola.ui.settings.SettingsScreen
import com.martinrevert.latorrentola.ui.settings.SettingsViewModel
import com.martinrevert.latorrentola.utils.PreferenceManager
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
     * @property tmdbMovieId TMDB movie identifier for actor filmography navigation.
     * @property tmdbMovieTitle Fallback title used to find the movie in YTS.
     */
    @Serializable data class Detail(
        val movieJson: String? = null,
        val movieId: Int? = null,
        val query: String? = null,
        val tmdbMovieId: Int? = null,
        val tmdbMovieTitle: String? = null
    ) : Route
    /** User settings destination. */
    @Serializable object Settings : Route
    /** App-managed torrent download list. */
    @Serializable object Downloads : Route
    /** External and network storage media browser (USB, SMB, NFS). */
    @Serializable object ExternalMedia : Route
    /**
     * Search destination with optional genre, text query, TV mode, or Actor mode.
     *
     * @property genre Genre or collection key used to initialize the result list.
     * @property query Initial search text.
     * @property isTvMode Whether search opens in TV series mode.
     * @property isActorMode Whether search opens in TMDB Actor search mode.
     */
    @Serializable data class Search(
        val genre: String? = null,
        val query: String? = null,
        val isTvMode: Boolean = false,
        val isActorMode: Boolean = false
    ) : Route
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

/**
 * Resolves the host [Activity] from a given [Context], traversing through [ContextWrapper]s if necessary.
 */
internal fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Encapsulates safe back stack operations to prevent [IllegalArgumentException]: NavDisplay backstack cannot be empty.
 */
internal object NavBackStackHandler {
    /**
     * Safely pops the top destination if there is more than 1 entry in the back stack.
     * If only 1 entry remains, it calls [onFinish] to finish the activity and exit the app instead of emptying the stack.
     */
    fun pop(backStack: MutableList<*>, onFinish: () -> Unit) {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        } else {
            onFinish()
        }
    }

    /**
     * Resets the entire back stack to [targetRoute] without ever leaving the stack empty.
     * By adding [targetRoute] first and removing preceding elements, the stack size is always >= 1.
     */
    fun <T : Any> resetTo(backStack: MutableList<in T>, targetRoute: T) {
        Snapshot.withMutableSnapshot {
            backStack.add(targetRoute)
            while (backStack.size > 1) {
                backStack.removeAt(0)
            }
        }
    }

    /**
     * Navigates to [targetRoute] while replacing an existing [Route.Detail] if present at the top,
     * ensuring the back stack is never momentarily empty.
     */
    fun <T : Any> replaceTopDetail(backStack: MutableList<in T>, targetRoute: T) {
        Snapshot.withMutableSnapshot {
            val shouldReplace = (backStack as List<*>).lastOrNull() is Route.Detail
            backStack.add(targetRoute)
            if (shouldReplace && backStack.size > 1) {
                backStack.removeAt(backStack.size - 2)
            }
        }
    }

    /**
     * Ensures [backStack] has at least one entry, adding [fallbackRoute] if empty.
     */
    fun <T : Any> ensureNotEmpty(backStack: MutableList<in T>, fallbackRoute: T) {
        if (backStack.isEmpty()) {
            backStack.add(fallbackRoute)
        }
    }
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
    val currentUser by authViewModel.currentUserFlow.collectAsState()
    val isLoggedIn = remember { authViewModel.currentUser != null }
    val context = LocalContext.current
    val preferenceManager = remember(context) { PreferenceManager(context) }
    val themeMode by preferenceManager.themeFlow.collectAsState(initial = preferenceManager.getTheme())

    val initialRoute = if (isLoggedIn) Route.Home else Route.Login
    val backStack = rememberNavBackStack(initialRoute)
    NavBackStackHandler.ensureNotEmpty(backStack, initialRoute)

    val popBackStack = remember(context, backStack) {
        {
            NavBackStackHandler.pop(backStack) {
                context.findActivity()?.finish()
            }
        }
    }

    // Handle session expiry or logout from other parts of the app
    LaunchedEffect(currentUser) {
        if (currentUser == null && backStack.firstOrNull() != Route.Login) {
            authViewModel.resetAuthState()
            NavBackStackHandler.resetTo(backStack, Route.Login)
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
        onBack = popBackStack,
        entryProvider = entryProvider {
            entry<Route.Login> {
                LoginScreen(
                    viewModel = authViewModel,
                    themeMode = themeMode,
                    onLoginSuccess = {
                        authViewModel.resetAuthState()
                        NavBackStackHandler.resetTo(backStack, Route.Home)
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
                    onSearchClick = { isTvMode ->
                        backStack.add(Route.Search(isTvMode = isTvMode))
                    },
                    onActorSearchClick = {
                        backStack.add(Route.Search(isActorMode = true))
                    },
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
                    } ?: detailKey.tmdbMovieId?.let { tmdbMovieId ->
                        viewModel.fetchAndOpenMovieByTmdbId(
                            tmdbMovieId,
                            detailKey.tmdbMovieTitle.orEmpty()
                        )
                    }
                }
                MovieDetailScreen(
                    viewModel = viewModel,
                    onBackClick = popBackStack,
                    onTvSeriesClick = { seriesId ->
                        NavBackStackHandler.replaceTopDetail(backStack, Route.TvDetail(seriesId))
                    }
                )
            }
            entry<Route.Settings> {
                val viewModel: SettingsViewModel = hiltViewModel()
                val user = authViewModel.currentUser
                SettingsScreen(
                    viewModel = viewModel,
                    userPhotoUrl = user?.photoUrl?.toString(),
                    userName = user?.displayName,
                    userEmail = user?.email,
                    themeMode = themeMode,
                    onBackClick = popBackStack,
                    onLogoutClick = {
                        authViewModel.signOut()
                        NavBackStackHandler.resetTo(backStack, Route.Login)
                    },
                    onDownloadsClick = { backStack.add(Route.Downloads) },
                    onExternalMediaClick = { backStack.add(Route.ExternalMedia) }
                )
            }
            entry<Route.Downloads> {
                TorrentDownloadsScreen(
                    onBackClick = popBackStack
                )
            }
            entry<Route.ExternalMedia> {
                ExternalMediaBrowserScreen(
                    themeMode = themeMode,
                    onBackClick = popBackStack
                )
            }
            entry<Route.Search> { searchKey ->
                val viewModel: SearchViewModel = hiltViewModel(key = searchKey.hashCode().toString())
                SearchScreen(
                    viewModel = viewModel,
                    initialGenre = searchKey.genre,
                    initialQuery = searchKey.query,
                    initialTvMode = searchKey.isTvMode,
                    initialActorMode = searchKey.isActorMode,
                    onMovieClick = { movie ->
                        val movieJson = Json.encodeToString(Movie.serializer(), movie)
                        backStack.add(Route.Detail(movieJson = movieJson))
                    },
                    onTvSeriesClick = { series ->
                        backStack.add(Route.TvDetail(series.id))
                    },
                    onActorCreditClick = { credit: TmdbCastCredit ->
                        if (credit.mediaType == "tv") {
                            backStack.add(Route.TvDetail(credit.id))
                        } else {
                            backStack.add(
                                Route.Detail(
                                    tmdbMovieId = credit.id,
                                    tmdbMovieTitle = credit.displayTitle
                                )
                            )
                        }
                    },
                    onBackClick = popBackStack
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
                    onBackClick = popBackStack
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
                    onBackClick = popBackStack,
                    onFilmographyCreditClick = { credit: TmdbCastCredit ->
                        if (credit.mediaType == "tv") {
                            backStack.add(Route.TvDetail(credit.id))
                        } else {
                            backStack.add(
                                Route.Detail(
                                    tmdbMovieId = credit.id,
                                    tmdbMovieTitle = credit.displayTitle
                                )
                            )
                        }
                    }
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
                    onBackClick = popBackStack
                )
            }
        }
    )
}
