package com.martinrevert.latorrentola.network

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.database.DateDao
import com.martinrevert.latorrentola.database.GenreDao
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.YTS.MovieDetails
import com.martinrevert.latorrentola.model.date.DateLastVisit
import com.martinrevert.latorrentola.model.stats.GenreStats
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class YtsRepositoryTest {

    private lateinit var repository: YtsRepository
    private val ytsService: YtsService = mockk()
    private val userLibraryRepository: UserLibraryRepository = mockk()
    private val genreDao: GenreDao = mockk()
    private val dateDao: DateDao = mockk()

    @Before
    fun setUp() {
        repository = YtsRepository(ytsService, userLibraryRepository, genreDao, dateDao)
    }

    @Test
    fun `getMovies should call service`() = runTest {
        val mockDetails = MovieDetails(status = "ok")
        coEvery { 
            ytsService.getMovieDetails(any(), any(), any(), any(), any(), any(), any(), any()) 
        } returns mockDetails

        val result = repository.getMovies(1)

        assertThat(result).isEqualTo(mockDetails)
        coVerify { ytsService.getMovieDetails(any(), any(), 1, "true", "true", "year", "desc", null) }
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
}
