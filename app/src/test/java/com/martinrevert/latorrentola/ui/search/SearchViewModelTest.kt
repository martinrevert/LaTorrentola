package com.martinrevert.latorrentola.ui.search

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.TMDB.TmdbActorDetail
import com.martinrevert.latorrentola.model.TMDB.TmdbCastCredit
import com.martinrevert.latorrentola.model.TMDB.TmdbCreditsResponse
import com.martinrevert.latorrentola.model.TMDB.TmdbPersonResult
import com.martinrevert.latorrentola.model.TMDB.TmdbTvPage
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.model.YTS.Data
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.YTS.MovieDetails
import com.martinrevert.latorrentola.model.YTS.Torrent
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.network.YtsRepository
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import com.martinrevert.latorrentola.utils.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: YtsRepository = mockk(relaxed = true)
    private val tmdbRepository: TmdbRepository = mockk(relaxed = true)
    private val userLibraryRepository: UserLibraryRepository = mockk(relaxed = true)
    private val preferenceManager: com.martinrevert.latorrentola.utils.PreferenceManager = mockk(relaxed = true)
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        every { userLibraryRepository.getDownloadedMovies() } returns flowOf(emptyList())
        every { userLibraryRepository.getFavoriteTvSeries() } returns flowOf(emptyList())
        every { userLibraryRepository.getWatchHistory() } returns flowOf(emptyList())
        every { preferenceManager.getFilteredLanguages() } returns ""
        every { preferenceManager.filteredLanguagesFlow } returns MutableStateFlow("")
        every { preferenceManager.getMinimumRating() } returns 6f
        every { preferenceManager.minimumRatingFlow } returns MutableStateFlow(6f)
        viewModel = SearchViewModel(repository, tmdbRepository, userLibraryRepository, preferenceManager)
    }

    @Test
    fun `pure search should not apply minimum IMDb rating`() = runTest {
        val unratedMovie = Movie(id = 1, title = "Search Result", rating = "2.0")
        coEvery { repository.searchMovies("query", 1, null) } returns
            MovieDetails(data = Data(movies = listOf(unratedMovie)))

        viewModel.search("query")

        assertThat((viewModel.uiState.value as SearchUiState.Success).movies)
            .containsExactly(unratedMovie)
    }

    @Test
    fun `initial state should be Idle`() {
        assertThat(viewModel.uiState.value).isEqualTo(SearchUiState.Idle)
    }

    @Test
    fun `search should update state to Success when results found`() = runTest {
        val movies = listOf(Movie(id = 1, title = "Search Result"))
        coEvery { repository.searchMovies("query", 1, null) } returns MovieDetails(data = Data(movies = movies))

        viewModel.search("query")

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(SearchUiState.Success::class.java)
        assertThat((state as SearchUiState.Success).movies).hasSize(1)
    }

    @Test
    fun `search with empty results should update state to Empty`() = runTest {
        coEvery { repository.searchMovies("empty", 1, null) } returns MovieDetails(data = Data(movies = emptyList()))

        viewModel.search("empty")

        assertThat(viewModel.uiState.value).isEqualTo(SearchUiState.Empty)
    }

    @Test
    fun `searchByGenre should update state to Success`() = runTest {
        val movies = listOf(
            Movie(id = 1, title = "Genre Movie", rating = "7"),
            Movie(id = 2, title = "Low-rated genre movie", rating = "5")
        )
        coEvery { repository.searchByGenre("Action", 1, null) } returns MovieDetails(data = Data(movies = movies))

        viewModel.searchByGenre("Action")

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(SearchUiState.Success::class.java)
        val results = (state as SearchUiState.Success).movies
        assertThat(results.map { it.title }).containsExactly("Genre Movie")
    }

    @Test
    fun `showFavorites should collect and display favorites`() = runTest {
        val favorites = listOf(Movie(id = 1, title = "Favorite 1", rating = "7"))
        every { repository.getFavoriteMovies() } returns flowOf(favorites)

        viewModel.showFavorites()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(SearchUiState.Success::class.java)
        val success = state as SearchUiState.Success
        assertThat(success.isFavorites).isTrue()
        assertThat(success.movies).hasSize(1)
    }

    @Test
    fun `showFavorites should enrich series without resetting to Loading or double flickering`() = runTest {
        val movie = Movie(id = 1, title = "Fav Movie", rating = "8")
        val unenrichedSeries = TmdbTvSummary(id = 50, name = "Unenriched Series", numberOfEpisodes = null)
        val enrichedSeries = TmdbTvSummary(id = 50, name = "Unenriched Series", numberOfSeasons = 3, numberOfEpisodes = 30)

        every { repository.getFavoriteMovies() } returns flowOf(listOf(movie))
        every { userLibraryRepository.getFavoriteTvSeries() } returns flowOf(listOf(unenrichedSeries))
        coEvery { tmdbRepository.getTvDetails(50) } returns enrichedSeries

        viewModel.showFavorites()

        val state = viewModel.uiState.value as SearchUiState.Success
        assertThat(state.isFavorites).isTrue()
        assertThat(state.movies).containsExactly(movie)
        assertThat(state.favoriteSeries.single().numberOfEpisodes).isEqualTo(30)
        // Must only subscribe to getFavoriteMovies once (no recursive showFavorites(force = true) restart)
        coVerify(exactly = 1) { repository.getFavoriteMovies() }
        coVerify(exactly = 1) { tmdbRepository.getTvDetails(50) }
    }

    @Test
    fun `loadMore should deduplicate and append movies`() = runTest {
        val firstPage = listOf(Movie(id = 1, title = "Movie 1"))
        coEvery { repository.searchMovies("query", 1, null) } returns MovieDetails(data = Data(movies = firstPage))

        viewModel.search("query")
        assertThat((viewModel.uiState.value as SearchUiState.Success).movies).hasSize(1)

        val secondPage = listOf(
            Movie(id = 1, title = "Movie 1"), // Duplicate
            Movie(id = 2, title = "Movie 2")  // New
        )
        coEvery { repository.searchMovies("query", 2, null) } returns MovieDetails(data = Data(movies = secondPage))

        viewModel.loadMore()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(SearchUiState.Success::class.java)
        val success = state as SearchUiState.Success
        assertThat(success.movies).hasSize(2)
        assertThat(success.movies.map { it.id }).containsExactly(1, 2)
    }

    @Test
    fun `error should update state to Error`() = runTest {
        coEvery { repository.searchMovies(any(), any(), any()) } throws Exception("Search Error")

        viewModel.search("error")

        assertThat(viewModel.uiState.value).isInstanceOf(SearchUiState.Error::class.java)
        assertThat((viewModel.uiState.value as SearchUiState.Error).message)
            .isEqualTo(UiText.DynamicString("Search Error"))
    }

    @Test
    fun `loadMore should filter movies based on preferences`() = runTest {
        val movies = listOf(
            Movie(id = 1, title = "EN", language = "en"),
            Movie(id = 2, title = "ES", language = "es")
        )
        every { preferenceManager.getFilteredLanguages() } returns "es"
        coEvery { repository.searchMovies("query", 1, null) } returns MovieDetails(data = Data(movies = movies))

        viewModel.search("query")

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(SearchUiState.Success::class.java)
        val successState = state as SearchUiState.Success
        assertThat(successState.movies).hasSize(1)
        assertThat(successState.movies[0].language).isEqualTo("en")
    }

    @Test
    fun `setQuality should reload results with new quality`() = runTest {
        coEvery { repository.searchMovies("query", 1, null) } returns MovieDetails(
            data = Data(movies = listOf(Movie(id = 1)))
        )
        viewModel.search("query")

        coEvery { repository.searchMovies("query", 1, "720p") } returns MovieDetails(
            data = Data(movies = listOf(Movie(id = 2, torrents = listOf(Torrent(quality = "720p")))))
        )
        viewModel.setQuality("720p")

        assertThat(viewModel.selectedQuality.value).isEqualTo("720p")
        val state = viewModel.uiState.value
        assertThat((state as SearchUiState.Success).movies[0].id).isEqualTo(2)
    }

    @Test
    fun `resetSearch should return state to Idle`() = runTest {
        viewModel.search("query")
        viewModel.resetSearch()

        assertThat(viewModel.uiState.value).isEqualTo(SearchUiState.Idle)
    }

    @Test
    fun `toggleFavoriteSelection should update selected ids`() {
        viewModel.toggleFavoriteSelection(1)
        assertThat(viewModel.selectedFavoriteIds.value).contains(1)

        viewModel.toggleFavoriteSelection(1)
        assertThat(viewModel.selectedFavoriteIds.value).doesNotContain(1)
    }

    @Test
    fun `clearSelection should empty selected ids`() {
        viewModel.toggleFavoriteSelection(1)
        viewModel.clearSelection()
        assertThat(viewModel.selectedFavoriteIds.value).isEmpty()
    }

    @Test
    fun `deleteSelectedFavorites should call repository for each selected id including tv series`() = runTest {
        val movie = Movie(id = 1)
        val tvSeries = TmdbTvSummary(id = 10, name = "Series 1")
        every { repository.getFavoriteMovies() } returns flowOf(listOf(movie))
        every { userLibraryRepository.getFavoriteTvSeries() } returns flowOf(listOf(tvSeries))
        viewModel.showFavorites()

        viewModel.toggleFavoriteSelection(1)
        viewModel.toggleFavoriteSelection(10)
        viewModel.deleteSelectedFavorites()

        coVerify { repository.removeFavorite(match { it.id == 1 }) }
        coVerify { userLibraryRepository.removeFavoriteTvSeries(match { it.id == 10 }) }
        assertThat(viewModel.selectedFavoriteIds.value).isEmpty()
    }

    @Test
    fun `search with empty query should reset search`() = runTest {
        viewModel.search("query")
        viewModel.search("")

        assertThat(viewModel.uiState.value).isEqualTo(SearchUiState.Idle)
    }

    @Test
    fun `removeFavorite should call repository`() = runTest {
        val movie = Movie(id = 1)
        viewModel.removeFavorite(movie)
        coVerify { repository.removeFavorite(movie) }
    }

    @Test
    fun `setLastClickedMovieId should update state`() {
        viewModel.setLastClickedMovieId(123)
        assertThat(viewModel.lastClickedMovieId.value).isEqualTo(123)
    }

    @Test
    fun `observeFilteredLanguages should reload when favorites showing`() = runTest {
        val favorites = listOf(Movie(id = 1, title = "Fav", rating = "7"))
        every { repository.getFavoriteMovies() } returns flowOf(favorites)
        viewModel.showFavorites()

        // Trigger language change
        (preferenceManager.filteredLanguagesFlow as MutableStateFlow).value = "en"

        // Should trigger showFavorites again
        coVerify(exactly = 2) { repository.getFavoriteMovies() }
    }

    @Test
    fun `watchHistoryMap should expose progress items mapped by mediaId`() = runTest {
        val progress = com.martinrevert.latorrentola.model.user.PlaybackProgress(
            mediaId = "100",
            title = "Test Movie",
            positionMs = 300000L,
            durationMs = 600000L,
            timestamp = 1000L,
            isEpisode = false
        )
        every { userLibraryRepository.getWatchHistory() } returns flowOf(listOf(progress))

        val vm = SearchViewModel(repository, tmdbRepository, userLibraryRepository, preferenceManager)

        vm.watchHistoryMap.test {
            var map = awaitItem()
            if (map.isEmpty()) {
                map = awaitItem()
            }
            assertThat(map["100"]).isEqualTo(progress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setFavoritesFilter should update selectedFavoritesFilter state`() = runTest {
        viewModel.setFavoritesFilter(FavoritesFilter.MOVIES)
        assertThat(viewModel.selectedFavoritesFilter.value).isEqualTo(FavoritesFilter.MOVIES)
    }

    @Test
    fun `showFavorites with MOVIES filter should filter out tv series`() = runTest {
        val movie = Movie(id = 1, title = "Movie 1", rating = "8")
        val tvSeries = TmdbTvSummary(id = 10, name = "Series 1")
        every { repository.getFavoriteMovies() } returns flowOf(listOf(movie))
        every { userLibraryRepository.getFavoriteTvSeries() } returns flowOf(listOf(tvSeries))

        viewModel.setFavoritesFilter(FavoritesFilter.MOVIES)
        viewModel.showFavorites()

        val state = viewModel.uiState.value as SearchUiState.Success
        assertThat(state.movies).containsExactly(movie)
        assertThat(viewModel.favoriteTvSeries.value).isEmpty()
    }

    @Test
    fun `showFavorites with TV_SERIES filter should filter out movies`() = runTest {
        val movie = Movie(id = 1, title = "Movie 1", rating = "8")
        val tvSeries = TmdbTvSummary(id = 10, name = "Series 1")
        every { repository.getFavoriteMovies() } returns flowOf(listOf(movie))
        every { userLibraryRepository.getFavoriteTvSeries() } returns flowOf(listOf(tvSeries))

        viewModel.setFavoritesFilter(FavoritesFilter.TV_SERIES)
        viewModel.showFavorites()

        val state = viewModel.uiState.value as SearchUiState.Success
        assertThat(state.movies).isEmpty()
        assertThat(viewModel.favoriteTvSeries.value).containsExactly(tvSeries)
    }

    @Test
    fun `showFavorites with IN_PROGRESS filter should return movies and series in progress`() = runTest {
        val inProgressMovie = Movie(id = 1, title = "In Progress Movie", rating = "8")
        val watchedMovie = Movie(id = 2, title = "Watched Movie", rating = "8")
        val inProgressSeries = TmdbTvSummary(id = 10, name = "Series 1", numberOfEpisodes = 10)

        val movieProgress = com.martinrevert.latorrentola.model.user.PlaybackProgress(
            mediaId = "1", title = "In Progress Movie", positionMs = 500000L, durationMs = 1000000L, isEpisode = false
        )
        val watchedMovieProgress = com.martinrevert.latorrentola.model.user.PlaybackProgress(
            mediaId = "2", title = "Watched Movie", positionMs = 980000L, durationMs = 1000000L, isEpisode = false
        )
        val episodeProgress = com.martinrevert.latorrentola.model.user.PlaybackProgress(
            mediaId = "media_10_s1_e1", title = "Episode 1", positionMs = 500000L, durationMs = 1000000L, isEpisode = true
        )

        every { repository.getFavoriteMovies() } returns flowOf(listOf(inProgressMovie, watchedMovie))
        every { userLibraryRepository.getFavoriteTvSeries() } returns flowOf(listOf(inProgressSeries))
        every { userLibraryRepository.getWatchHistory() } returns flowOf(listOf(movieProgress, watchedMovieProgress, episodeProgress))

        val vm = SearchViewModel(repository, tmdbRepository, userLibraryRepository, preferenceManager)
        vm.setFavoritesFilter(FavoritesFilter.IN_PROGRESS)
        vm.showFavorites()

        val state = vm.uiState.value as SearchUiState.Success
        assertThat(state.movies).containsExactly(inProgressMovie)
        assertThat(vm.favoriteTvSeries.value).containsExactly(inProgressSeries)
    }

    @Test
    fun `initializeIfNeeded preserves search state and query on back navigation`() = runTest {
        val movie = Movie(id = 7, title = "Matrix")
        coEvery { repository.searchMovies("Matrix", 1, null) } returns MovieDetails(data = Data(movies = listOf(movie)))

        // First entry from magnifying glass (all nulls/false)
        viewModel.initializeIfNeeded(initialGenre = null, initialQuery = null, initialTvMode = false, initialActorMode = false)
        viewModel.search("Matrix")
        viewModel.setLastClickedMovieId(7)

        // Simulate returning via Back navigation where LaunchedEffect runs initializeIfNeeded again
        viewModel.initializeIfNeeded(initialGenre = null, initialQuery = null, initialTvMode = false, initialActorMode = false)

        assertThat(viewModel.searchQuery.value).isEqualTo("Matrix")
        assertThat(viewModel.lastClickedMovieId.value).isEqualTo(7)
        val state = viewModel.uiState.value as SearchUiState.Success
        assertThat(state.movies).containsExactly(movie)
    }

    @Test
    fun `searchActors and selectActor sort combined credits by year descending`() = runTest {
        val actor1 = TmdbPersonResult(id = 101, name = "Bryan Cranston")
        val actor2 = TmdbPersonResult(id = 102, name = "Aaron Paul")
        val creditsActor1 = listOf(
            TmdbCastCredit(id = 1, title = "Older Movie", releaseDate = "2001-03-10", mediaType = "movie"),
            TmdbCastCredit(id = 2, name = "Newest Show", firstAirDate = "2024-11-01", mediaType = "tv"),
            TmdbCastCredit(id = 3, title = "Middle Movie", releaseDate = "2015-06-20", mediaType = "movie")
        )
        val creditsActor2 = listOf(
            TmdbCastCredit(id = 4, title = "El Camino", releaseDate = "2019-10-11", mediaType = "movie"),
            TmdbCastCredit(id = 5, name = "Westworld", firstAirDate = "2020-03-15", mediaType = "tv")
        )

        coEvery { tmdbRepository.searchActors("Bryan") } returns listOf(actor1, actor2)
        coEvery { tmdbRepository.getActorDetails(101) } returns Result.success(
            TmdbActorDetail(id = 101, name = "Bryan Cranston", combinedCredits = TmdbCreditsResponse(cast = creditsActor1))
        )
        coEvery { tmdbRepository.getActorDetails(102) } returns Result.success(
            TmdbActorDetail(id = 102, name = "Aaron Paul", combinedCredits = TmdbCreditsResponse(cast = creditsActor2))
        )

        viewModel.setActorMode(true)
        viewModel.search("Bryan")

        val state1 = viewModel.actorUiState.value as SearchActorUiState.Success
        assertThat(state1.selectedActor).isEqualTo(actor1)
        assertThat(state1.credits.map { it.id }).containsExactly(2, 3, 1).inOrder()

        viewModel.selectActor(actor2)
        val state2 = viewModel.actorUiState.value as SearchActorUiState.Success
        assertThat(state2.selectedActor).isEqualTo(actor2)
        assertThat(state2.credits.map { it.id }).containsExactly(5, 4).inOrder()
    }

    @Test
    fun `searchTvSeries and loadMoreTvSeries paginate and deduplicate results`() = runTest {
        coEvery { tmdbRepository.searchTvSeries("Dark", 1) } returns TmdbTvPage(
            page = 1,
            totalPages = 2,
            results = listOf(TmdbTvSummary(id = 1, name = "Dark"))
        )
        coEvery { tmdbRepository.searchTvSeries("Dark", 2) } returns TmdbTvPage(
            page = 2,
            totalPages = 2,
            results = listOf(
                TmdbTvSummary(id = 1, name = "Dark"),
                TmdbTvSummary(id = 2, name = "1899")
            )
        )

        viewModel.setTvMode(true)
        viewModel.search("Dark")
        assertThat((viewModel.tvUiState.value as SearchTvUiState.Success).series.map { it.id })
            .containsExactly(1)

        viewModel.loadMore()
        assertThat((viewModel.tvUiState.value as SearchTvUiState.Success).series.map { it.id })
            .containsExactly(1, 2).inOrder()
    }
}
