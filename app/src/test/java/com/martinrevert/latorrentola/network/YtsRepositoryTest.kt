package com.martinrevert.latorrentola.network

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.database.DateDao
import com.martinrevert.latorrentola.database.GenreDao
import com.martinrevert.latorrentola.model.YTS.Data
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.YTS.MovieDetails
import com.martinrevert.latorrentola.model.date.DateLastVisit
import com.martinrevert.latorrentola.model.stats.GenreStats
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Before
import org.junit.Test

class YtsRepositoryTest {

    private lateinit var repository: YtsRepository
    private val ytsService: YtsService = mockk()
    private val fcmService: FcmService = mockk()
    private val userLibraryRepository: UserLibraryRepository = mockk()
    private val genreDao: GenreDao = mockk()
    private val dateDao: DateDao = mockk()

    @Before
    fun setUp() {
        repository = YtsRepository(ytsService, fcmService, userLibraryRepository, genreDao, dateDao)
    }

    @Test
    fun `getMovies should call service`() = runTest {
        val mockDetails = MovieDetails(status = "ok")
        coEvery { 
            ytsService.getMovieDetails(any(), any(), any(), any(), any(), any(), any(), any()) 
        } returns mockDetails

        val result = repository.getMovies(1)

        assertThat(result).isEqualTo(mockDetails)
        coVerify { ytsService.getMovieDetails(any(), "6", 1, "true", "true", "year", "desc", null) }
    }

    @Test
    fun `getMovies should pass configured minimum rating to service`() = runTest {
        val mockDetails = MovieDetails(status = "ok")
        coEvery {
            ytsService.getMovieDetails(any(), "8", any(), any(), any(), any(), any(), any())
        } returns mockDetails

        repository.getMovies(page = 1, minimumRating = 8f)

        coVerify { ytsService.getMovieDetails(any(), "8", 1, "true", "true", "year", "desc", null) }
    }

    @Test
    fun `getFavoriteMovies should return flow from repository`() = runTest {
        val movies = listOf(Movie(id = 1, title = "Movie 1"))
        every { userLibraryRepository.getFavoriteMovies() } returns flowOf(movies)

        repository.getFavoriteMovies().test {
            assertThat(awaitItem()).isEqualTo(movies)
            awaitComplete()
        }
    }

    @Test
    fun `addFavorite should call repository`() = runTest {
        val movie = Movie(id = 1, title = "Movie 1")
        coEvery { userLibraryRepository.addFavorite(movie) } returns Unit

        repository.addFavorite(movie)

        coVerify { userLibraryRepository.addFavorite(movie) }
    }

    @Test
    fun `isFavorite should return true if movie exists`() = runTest {
        coEvery { userLibraryRepository.isFavorite(1) } returns true

        val result = repository.isFavorite(1)

        assertThat(result).isTrue()
    }

    @Test
    fun `isFavorite should return false if movie does not exist`() = runTest {
        coEvery { userLibraryRepository.isFavorite(1) } returns false

        val result = repository.isFavorite(1)

        assertThat(result).isFalse()
    }

    @Test
    fun `recordGenreVisit should call dao`() = runTest {
        coEvery { genreDao.incrementOrInsert("Action") } returns Unit

        repository.recordGenreVisit("Action")

        coVerify { genreDao.incrementOrInsert("Action") }
    }

    @Test
    fun `getLastVisitDate should return date from dao`() = runTest {
        val date = DateLastVisit(id = 1, date = java.util.Date(123456789L))
        coEvery { dateDao.getDate() } returns listOf(date)

        val result = repository.getLastVisitDate()

        assertThat(result).isEqualTo(date)
    }

    @Test
    fun `getMovies caches non-empty page within TTL and bypasses cache on forceRefresh`() = runTest {
        val page1 = MovieDetails(
            status = "ok",
            data = com.martinrevert.latorrentola.model.YTS.Data(
                movies = listOf(Movie(id = 1, title = "Cached Movie"))
            )
        )
        val refreshedPage1 = MovieDetails(
            status = "ok",
            data = com.martinrevert.latorrentola.model.YTS.Data(
                movies = listOf(Movie(id = 2, title = "Fresh Movie"))
            )
        )
        coEvery {
            ytsService.getMovieDetails(any(), "6", 1, "true", "true", "year", "desc", null)
        } returnsMany listOf(page1, refreshedPage1)

        val first = repository.getMovies(page = 1)
        val cachedSecond = repository.getMovies(page = 1)
        val forcedThird = repository.getMovies(page = 1, forceRefresh = true)

        assertThat(first).isEqualTo(page1)
        assertThat(cachedSecond).isEqualTo(page1)
        assertThat(forcedThird).isEqualTo(refreshedPage1)
        coVerify(exactly = 2) {
            ytsService.getMovieDetails(any(), "6", 1, "true", "true", "year", "desc", null)
        }
    }

    @Test
    fun `getMovies coalesces concurrent identical in-flight requests into a single service call`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<MovieDetails>()
        val expected = MovieDetails(
            status = "ok",
            data = com.martinrevert.latorrentola.model.YTS.Data(
                movies = listOf(Movie(id = 42, title = "Coalesced Movie"))
            )
        )
        coEvery {
            ytsService.getMovieDetails(any(), "6", 1, "true", "true", "year", "desc", null)
        } coAnswers { gate.await() }

        val firstCall = async { repository.getMovies(page = 1) }
        val secondCall = async { repository.getMovies(page = 1) }
        yield()

        gate.complete(expected)

        assertThat(firstCall.await()).isEqualTo(expected)
        assertThat(secondCall.await()).isEqualTo(expected)
        coVerify(exactly = 1) {
            ytsService.getMovieDetails(any(), "6", 1, "true", "true", "year", "desc", null)
        }
    }

    @Test
    fun `getMovies retries for coalesced active caller when primary in-flight caller is cancelled`() = runTest {
        val firstGate = CompletableDeferred<MovieDetails>()
        val expected = MovieDetails(
            status = "ok",
            data = Data(movies = listOf(Movie(id = 77, title = "Recovered Movie")))
        )
        var invocationCount = 0
        coEvery {
            ytsService.getMovieDetails(any(), "6", 1, "true", "true", "year", "desc", null)
        } coAnswers {
            invocationCount++
            if (invocationCount == 1) {
                firstGate.await()
            } else {
                expected
            }
        }

        val primaryCall = async { repository.getMovies(page = 1) }
        val coalescedCall = async { repository.getMovies(page = 1) }
        yield()

        primaryCall.cancelAndJoin()

        assertThat(coalescedCall.await()).isEqualTo(expected)
        coVerify(exactly = 2) {
            ytsService.getMovieDetails(any(), "6", 1, "true", "true", "year", "desc", null)
        }
    }

    @Test
    fun `getMovies with forceRefresh on page 1 invalidates cached subsequent pages`() = runTest {
        val page1 = MovieDetails(status = "ok", data = Data(movies = listOf(Movie(id = 1, title = "P1"))))
        val page2Initial = MovieDetails(status = "ok", data = Data(movies = listOf(Movie(id = 2, title = "P2 Old"))))
        val page1Refreshed = MovieDetails(status = "ok", data = Data(movies = listOf(Movie(id = 3, title = "P1 New"))))
        val page2Refreshed = MovieDetails(status = "ok", data = Data(movies = listOf(Movie(id = 4, title = "P2 New"))))

        coEvery {
            ytsService.getMovieDetails(any(), "6", 1, "true", "true", "year", "desc", null)
        } returnsMany listOf(page1, page1Refreshed)
        coEvery {
            ytsService.getMovieDetails(any(), "6", 2, "true", "true", "year", "desc", null)
        } returnsMany listOf(page2Initial, page2Refreshed)

        assertThat(repository.getMovies(page = 1)).isEqualTo(page1)
        assertThat(repository.getMovies(page = 2)).isEqualTo(page2Initial)

        assertThat(repository.getMovies(page = 1, forceRefresh = true)).isEqualTo(page1Refreshed)
        assertThat(repository.getMovies(page = 2, forceRefresh = false)).isEqualTo(page2Refreshed)

        coVerify(exactly = 2) {
            ytsService.getMovieDetails(any(), "6", 2, "true", "true", "year", "desc", null)
        }
    }
}
