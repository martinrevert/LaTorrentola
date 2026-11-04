package com.martinrevert.latorrentola.network

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.database.TvGenreDao
import com.martinrevert.latorrentola.model.TMDB.TmdbActorDetail
import com.martinrevert.latorrentola.model.TMDB.TmdbCastCredit
import com.martinrevert.latorrentola.model.TMDB.TmdbCreditsResponse
import com.martinrevert.latorrentola.model.TMDB.TmdbCrewCredit
import com.martinrevert.latorrentola.model.TMDB.TmdbImageItem
import com.martinrevert.latorrentola.model.TMDB.TmdbImagesResponse
import com.martinrevert.latorrentola.model.TMDB.TmdbMoviePage
import com.martinrevert.latorrentola.model.TMDB.TmdbMovieSummary
import com.martinrevert.latorrentola.model.TMDB.TmdbPersonResult
import com.martinrevert.latorrentola.model.TMDB.TmdbSearchPersonResponse
import com.martinrevert.latorrentola.model.TMDB.TmdbTvPage
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.model.TMDB.TmdbVideoItem
import com.martinrevert.latorrentola.model.TMDB.TmdbVideosResponse
import com.martinrevert.latorrentola.model.TMDB.TmdbWatchProvider
import com.martinrevert.latorrentola.model.TMDB.TmdbWatchProvidersResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class TmdbRepositoryTest {

    private lateinit var repository: TmdbRepository
    private val tmdbService: TmdbService = mockk()
    private val tvGenreDao: TvGenreDao = mockk(relaxed = true)

    @Before
    fun setUp() {
        repository = TmdbRepository(tmdbService, tvGenreDao)
    }

    @Test
    fun `getMovieWatchProviders fetches and sorts providers by display priority`() = runTest {
        val rawProviders = listOf(
            TmdbWatchProvider(providerId = 119, providerName = "Prime Video", displayPriority = 5),
            TmdbWatchProvider(providerId = 8, providerName = "Netflix", displayPriority = 1)
        )
        val mockResponse = TmdbWatchProvidersResponse(results = rawProviders)
        coEvery {
            tmdbService.getMovieWatchProviders(apiKey = any(), watchRegion = "US")
        } returns mockResponse

        val result = repository.getMovieWatchProviders("US")

        assertThat(result.map { it.providerId }).containsExactly(8, 119).inOrder()
        coVerify(exactly = 1) {
            tmdbService.getMovieWatchProviders(apiKey = any(), watchRegion = "US")
        }
    }

    @Test
    fun `getTvWatchProviders fetches and sorts providers by display priority`() = runTest {
        val rawProviders = listOf(
            TmdbWatchProvider(providerId = 337, providerName = "Disney+", displayPriority = 3),
            TmdbWatchProvider(providerId = 8, providerName = "Netflix", displayPriority = 1)
        )
        val mockResponse = TmdbWatchProvidersResponse(results = rawProviders)
        coEvery {
            tmdbService.getTvWatchProviders(apiKey = any(), watchRegion = "ES")
        } returns mockResponse

        val result = repository.getTvWatchProviders("ES")

        assertThat(result.map { it.providerId }).containsExactly(8, 337).inOrder()
        coVerify(exactly = 1) {
            tmdbService.getTvWatchProviders(apiKey = any(), watchRegion = "ES")
        }
    }

    @Test
    fun `discoverMoviesByProvider forwards parameters to tmdbService`() = runTest {
        val mockPage = TmdbMoviePage(
            page = 1,
            totalPages = 5,
            totalResults = 100,
            results = listOf(
                TmdbMovieSummary(id = 101, title = "Mock Movie", releaseDate = "2024-05-10")
            )
        )
        coEvery {
            tmdbService.discoverMoviesByProvider(
                apiKey = any(),
                providerId = 8,
                watchRegion = "US",
                sortBy = "popularity.desc",
                page = 1,
                language = any()
            )
        } returns mockPage

        val result = repository.discoverMoviesByProvider(
            providerId = 8,
            region = "US",
            sortBy = "popularity.desc",
            page = 1
        )

        assertThat(result).isEqualTo(mockPage)
        coVerify {
            tmdbService.discoverMoviesByProvider(
                apiKey = any(),
                providerId = 8,
                watchRegion = "US",
                sortBy = "popularity.desc",
                page = 1,
                language = any()
            )
        }
    }

    @Test
    fun `discoverTvByProvider forwards parameters to tmdbService`() = runTest {
        val mockPage = TmdbTvPage(
            page = 2,
            totalPages = 10,
            totalResults = 200,
            results = listOf(
                TmdbTvSummary(id = 202, name = "Mock TV Series", firstAirDate = "2023-09-15")
            )
        )
        coEvery {
            tmdbService.discoverTvByProvider(
                apiKey = any(),
                providerId = 337,
                watchRegion = "AR",
                sortBy = "first_air_date.desc",
                page = 2,
                language = any()
            )
        } returns mockPage

        val result = repository.discoverTvByProvider(
            providerId = 337,
            region = "AR",
            sortBy = "first_air_date.desc",
            page = 2
        )

        assertThat(result).isEqualTo(mockPage)
        coVerify {
            tmdbService.discoverTvByProvider(
                apiKey = any(),
                providerId = 337,
                watchRegion = "AR",
                sortBy = "first_air_date.desc",
                page = 2,
                language = any()
            )
        }
    }

    @Test
    fun `findMovieByImdbId returns cached movie result on second call`() = runTest {
        val movieResult = com.martinrevert.latorrentola.model.TMDB.TmdbMovieResult(
            id = 550,
            title = "Fight Club",
            backdropPath = "/backdrop.jpg"
        )
        val mockResponse = com.martinrevert.latorrentola.model.TMDB.TmdbFindResponse(
            movieResults = listOf(movieResult)
        )
        coEvery {
            tmdbService.findByExternalId(
                externalId = "tt0137523",
                apiKey = any(),
                externalSource = any(),
                language = any()
            )
        } returns mockResponse

        val firstCall = repository.findMovieByImdbId("0137523")
        val secondCall = repository.findMovieByImdbId("tt0137523")

        assertThat(firstCall).isEqualTo(movieResult)
        assertThat(secondCall).isEqualTo(movieResult)
        coVerify(exactly = 1) {
            tmdbService.findByExternalId(
                externalId = "tt0137523",
                apiKey = any(),
                externalSource = any(),
                language = any()
            )
        }
    }

    @Test
    fun `getMovieLogo fetches from tmdbService and caches result`() = runTest {
        val logos = listOf(
            TmdbImageItem(filePath = "/logo_es.png", iso6391 = "es"),
            TmdbImageItem(filePath = "/logo_en.png", iso6391 = "en")
        )
        val imagesResponse = TmdbImagesResponse(id = 550, logos = logos)
        coEvery {
            tmdbService.getMovieImages(
                movieId = 550,
                apiKey = any(),
                includeImageLanguage = any()
            )
        } returns imagesResponse

        val logoFirst = repository.getMovieLogo(550)
        val logoSecond = repository.getMovieLogo(550)

        assertThat(logoFirst).isNotNull()
        assertThat(logoSecond).isEqualTo(logoFirst)
        coVerify(exactly = 1) {
            tmdbService.getMovieImages(movieId = 550, apiKey = any(), includeImageLanguage = any())
        }
    }

    @Test
    fun `getMovieLogo caches negative result when no logo exists`() = runTest {
        val emptyImagesResponse = TmdbImagesResponse(id = 700, logos = emptyList())
        coEvery {
            tmdbService.getMovieImages(
                movieId = 700,
                apiKey = any(),
                includeImageLanguage = any()
            )
        } returns emptyImagesResponse

        val logoFirst = repository.getMovieLogo(700)
        val logoSecond = repository.getMovieLogo(700)

        assertThat(logoFirst).isNull()
        assertThat(logoSecond).isNull()
        coVerify(exactly = 1) {
            tmdbService.getMovieImages(movieId = 700, apiKey = any(), includeImageLanguage = any())
        }
    }

    @Test
    fun `bestLogoUrl prioritizes preferred language over fallback`() {
        val logos = listOf(
            TmdbImageItem(filePath = "/logo_es.png", iso6391 = "es"),
            TmdbImageItem(filePath = "/logo_en.png", iso6391 = "en"),
            TmdbImageItem(filePath = "/logo_neutral.png", iso6391 = null)
        )
        val imagesResponse = TmdbImagesResponse(id = 123, logos = logos)

        val esLogo = imagesResponse.getBestLogoUrl("es")
        val enLogo = imagesResponse.getBestLogoUrl("en")
        val frLogo = imagesResponse.getBestLogoUrl("fr")

        assertThat(esLogo).isEqualTo("https://image.tmdb.org/t/p/w500/logo_es.png")
        assertThat(enLogo).isEqualTo("https://image.tmdb.org/t/p/w500/logo_en.png")
        assertThat(frLogo).isEqualTo("https://image.tmdb.org/t/p/w500/logo_en.png")
    }

    @Test
    fun `bestLogoUrl selects highest voted logo within same language tier`() {
        val logos = listOf(
            TmdbImageItem(filePath = "/logo_en_low.png", iso6391 = "en", voteAverage = 3.2),
            TmdbImageItem(filePath = "/logo_en_high.png", iso6391 = "en", voteAverage = 8.9),
            TmdbImageItem(filePath = "/logo_en_mid.png", iso6391 = "en", voteAverage = 5.0)
        )
        val imagesResponse = TmdbImagesResponse(id = 456, logos = logos)

        val selectedLogo = imagesResponse.getBestLogoUrl("en")
        assertThat(selectedLogo).isEqualTo("https://image.tmdb.org/t/p/w500/logo_en_high.png")
    }

    @Test
    fun `getTvSeriesLogo caches positive result across calls`() = runTest {
        val logos = listOf(TmdbImageItem(filePath = "/tv_logo.png", iso6391 = "en"))
        val imagesResponse = TmdbImagesResponse(id = 1399, logos = logos)
        coEvery {
            tmdbService.getTvImages(
                seriesId = 1399,
                apiKey = any(),
                includeImageLanguage = any()
            )
        } returns imagesResponse

        val logoFirst = repository.getTvSeriesLogo(1399)
        val logoSecond = repository.getTvSeriesLogo(1399)

        assertThat(logoFirst).isNotNull()
        assertThat(logoSecond).isEqualTo(logoFirst)
        coVerify(exactly = 1) {
            tmdbService.getTvImages(seriesId = 1399, apiKey = any(), includeImageLanguage = any())
        }
    }

    @Test
    fun `getTvSeriesLogo caches negative result when no logo exists`() = runTest {
        val emptyImagesResponse = TmdbImagesResponse(id = 2000, logos = emptyList())
        coEvery {
            tmdbService.getTvImages(
                seriesId = 2000,
                apiKey = any(),
                includeImageLanguage = any()
            )
        } returns emptyImagesResponse

        val logoFirst = repository.getTvSeriesLogo(2000)
        val logoSecond = repository.getTvSeriesLogo(2000)

        assertThat(logoFirst).isNull()
        assertThat(logoSecond).isNull()
        coVerify(exactly = 1) {
            tmdbService.getTvImages(seriesId = 2000, apiKey = any(), includeImageLanguage = any())
        }
    }

    @Test
    fun `getTvSeriesTrailer caches positive result across calls`() = runTest {
        val videos = listOf(
            TmdbVideoItem(key = "trailer_key_1", site = "YouTube", type = "Trailer", official = true, iso6391 = "en")
        )
        val response = TmdbVideosResponse(id = 1399, results = videos)
        coEvery {
            tmdbService.getTvVideos(
                seriesId = 1399,
                apiKey = any(),
                language = any()
            )
        } returns response

        val trailerFirst = repository.getTvSeriesTrailer(1399)
        val trailerSecond = repository.getTvSeriesTrailer(1399)

        assertThat(trailerFirst).isEqualTo("trailer_key_1")
        assertThat(trailerSecond).isEqualTo("trailer_key_1")
        coVerify(exactly = 1) {
            tmdbService.getTvVideos(seriesId = 1399, apiKey = any(), language = any())
        }
    }

    @Test
    fun `getBestTrailerKey prioritizes official YouTube trailers matching language`() {
        val videos = listOf(
            TmdbVideoItem(key = "clip_es", site = "YouTube", type = "Clip", official = true, iso6391 = "es"),
            TmdbVideoItem(key = "trailer_en", site = "YouTube", type = "Trailer", official = true, iso6391 = "en"),
            TmdbVideoItem(key = "trailer_es", site = "YouTube", type = "Trailer", official = true, iso6391 = "es"),
            TmdbVideoItem(key = "vimeo_trailer", site = "Vimeo", type = "Trailer", official = true, iso6391 = "es")
        )
        val response = TmdbVideosResponse(id = 100, results = videos)

        assertThat(response.getBestTrailerKey("es")).isEqualTo("trailer_es")
        assertThat(response.getBestTrailerKey("fr")).isEqualTo("trailer_en")
    }

    @Test
    fun `getActorDetails combines directing and acting credits sorted descending`() = runTest {
        val mockDetail = TmdbActorDetail(
            id = 525,
            name = "Christopher Nolan",
            combinedCredits = TmdbCreditsResponse(
                cast = emptyList(),
                crew = listOf(
                    TmdbCrewCredit(id = 157336, title = "Interstellar", releaseDate = "2014-11-05", job = "Director", mediaType = "movie"),
                    TmdbCrewCredit(id = 872585, title = "Oppenheimer", releaseDate = "2023-07-21", job = "Director", mediaType = "movie"),
                    TmdbCrewCredit(id = 27205, title = "Inception", releaseDate = "2010-07-16", job = "Director", mediaType = "movie"),
                    TmdbCrewCredit(id = 99999, title = "Unrelated Role", releaseDate = "2000-01-01", job = "Producer", department = "Production", mediaType = "movie")
                )
            )
        )

        coEvery {
            tmdbService.getPersonDetail(
                personId = 525,
                apiKey = any(),
                appendToResponse = "combined_credits",
                language = any()
            )
        } returns mockDetail

        val result = repository.getActorDetails(525)

        assertThat(result.isSuccess).isTrue()
        val actor = result.getOrThrow()
        val castCredits = actor.combinedCredits?.cast.orEmpty()
        assertThat(castCredits.map { it.id }).containsExactly(872585, 157336, 27205).inOrder()
        assertThat(castCredits.map { it.character }).containsExactly("Director", "Director", "Director").inOrder()
    }
}

