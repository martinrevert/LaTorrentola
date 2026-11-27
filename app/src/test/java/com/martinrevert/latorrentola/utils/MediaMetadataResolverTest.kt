package com.martinrevert.latorrentola.utils

import com.martinrevert.latorrentola.database.TorrentDownloadDao
import com.martinrevert.latorrentola.model.torrent.TorrentDownload
import com.martinrevert.latorrentola.model.TMDB.TmdbMoviePage
import com.martinrevert.latorrentola.model.TMDB.TmdbMovieSummary
import com.martinrevert.latorrentola.model.TMDB.TmdbTvPage
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.model.YTS.Data
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.YTS.MovieDetails
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.YtsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MediaMetadataResolverTest {

    private val tmdbRepository = mockk<TmdbRepository>(relaxed = true)
    private val ytsRepository = mockk<YtsRepository>(relaxed = true)
    private val torrentDownloadDao = mockk<TorrentDownloadDao>(relaxed = true)

    private lateinit var resolver: MediaMetadataResolver

    @Before
    fun setUp() {
        resolver = MediaMetadataResolver(
            tmdbRepository = tmdbRepository,
            ytsRepository = ytsRepository,
            torrentDownloadDao = torrentDownloadDao
        )
    }

    @Test
    fun `extractSeasonAndEpisode extracts SxxExx and NxN correctly`() {
        assertEquals(Pair(1, 1), MediaMetadataResolver.extractSeasonAndEpisode("Severance.S01E01.1080p.mkv"))
        assertEquals(Pair(2, 4), MediaMetadataResolver.extractSeasonAndEpisode("Breaking.Bad.2x04.mkv"))
        assertEquals(Pair(3, 12), MediaMetadataResolver.extractSeasonAndEpisode("Show.Name.s3.e12.WEB-DL"))
        assertEquals(Pair(0, 1), MediaMetadataResolver.extractSeasonAndEpisode("Show.S00E01.Special.1080p.mkv"))
        assertEquals(Pair(0, 2), MediaMetadataResolver.extractSeasonAndEpisode("Show.0x02.Special.mkv"))
        assertEquals(Pair(0, 0), MediaMetadataResolver.extractSeasonAndEpisode("Gladiator.2000.1080p.mkv"))
    }

    @Test
    fun `extractCleanSearchTitles strips quality, codecs, and container extensions`() {
        val titles = MediaMetadataResolver.extractCleanSearchTitles("Inception.2010.1080p.BluRay.x264.YIFY.mp4")
        assertTrue(titles.contains("Inception"))
    }

    @Test
    fun `extractYear finds 4-digit release years`() {
        assertEquals(2010, MediaMetadataResolver.extractYear("Inception.2010.1080p.mkv"))
        assertEquals(1999, MediaMetadataResolver.extractYear("The.Matrix.1999.720p.mkv"))
        assertEquals(null, MediaMetadataResolver.extractYear("Severance S01E01"))
    }

    @Test
    fun `resolve for NAS movie inverse-resolves canonical movieId via YTS`() = runTest {
        val mockMovie = Movie(
            id = 12230,
            imdbCode = "tt0172495",
            title = "Gladiator",
            year = 2000,
            largeCoverImage = "https://img.yts.mx/gladiator.jpg"
        )
        coEvery { ytsRepository.searchMovies(any(), any(), any()) } returns MovieDetails(
            status = "ok",
            data = Data(movies = listOf(mockMovie), movieCount = 1)
        )

        val result = resolver.resolve(
            rawTitle = "Gladiator.2000.1080p.BluRay.x264.mkv",
            filePath = "/storage/nas/movies/Gladiator.2000.1080p.BluRay.x264.mkv"
        )

        assertEquals("tt0172495", result.mediaId)
        assertEquals(12230, result.movieId)
        assertFalse(result.isEpisode)
        assertTrue(MediaIdHelper.isCanonical(result.mediaId))
    }

    @Test
    fun `resolve for NAS TV episode inverse-resolves canonical seriesId via TMDB`() = runTest {
        val mockTv = TmdbTvSummary(
            id = 111803,
            name = "Severance",
            posterPath = "/poster.jpg"
        )
        coEvery { tmdbRepository.searchTvSeries(any(), any()) } returns TmdbTvPage(
            page = 1,
            results = listOf(mockTv),
            totalPages = 1,
            totalResults = 1
        )
        coEvery { tmdbRepository.getTvImdbId(111803) } returns "tt11280740"

        val result = resolver.resolve(
            rawTitle = "Severance.S01E01.Good.News.About.Hell.1080p.mkv",
            filePath = "/storage/nas/series/Severance/Season 1/Severance.S01E01.mkv"
        )

        assertEquals("tt11280740_s1_e1", result.mediaId)
        assertEquals(111803, result.seriesId)
        assertEquals(1, result.seasonNumber)
        assertEquals(1, result.episodeNumber)
        assertTrue(result.isEpisode)
        assertTrue(MediaIdHelper.isCanonical(result.mediaId))
    }

    @Test
    fun `resolve when movieId already provided queries YTS to populate IMDb ID`() = runTest {
        val mockMovie = Movie(
            id = 9988,
            imdbCode = "tt1375666",
            title = "Inception",
            year = 2010
        )
        coEvery { ytsRepository.searchMovies(any(), any(), any()) } returns MovieDetails(
            status = "ok",
            data = Data(movies = listOf(mockMovie), movieCount = 1)
        )

        val result = resolver.resolve(
            rawTitle = "Inception.2010.1080p.mkv",
            existingMovieId = 9988
        )
        assertEquals("tt1375666", result.mediaId)
        assertEquals(9988, result.movieId)
        assertEquals("tt1375666", result.imdbId)
        assertTrue(MediaIdHelper.isCanonical(result.mediaId))
    }

    @Test
    fun `resolve when movieId already provided falls back to YTS movie summary instead of TMDB`() = runTest {
        coEvery { ytsRepository.searchMovies(any(), any(), any()) } returns MovieDetails(
            status = "ok",
            data = Data(movies = emptyList(), movieCount = 0)
        )
        coEvery { ytsRepository.getMovieSummary(9988) } returns MovieDetails(
            status = "ok",
            data = Data(
                movie = Movie(
                    id = 9988,
                    imdbCode = "tt1099212",
                    title = "Twilight",
                    year = 2008
                )
            )
        )

        val result = resolver.resolve(
            rawTitle = "Twilight",
            existingMovieId = 9988
        )
        assertEquals("tt1099212", result.mediaId)
        assertEquals("tt1099212", result.imdbId)
        assertEquals(9988, result.movieId)
        assertTrue(MediaIdHelper.isCanonical(result.mediaId))
    }

    @Test
    fun `resolve when movieId already provided and neither YTS nor TMDB matches returns unknown_movie`() = runTest {
        coEvery { ytsRepository.searchMovies(any(), any(), any()) } returns MovieDetails(
            status = "ok",
            data = Data(movies = emptyList(), movieCount = 0)
        )
        coEvery { ytsRepository.getMovieSummary(9988) } returns MovieDetails(
            status = "ok",
            data = Data(movie = null)
        )
        coEvery { tmdbRepository.searchMovies(any(), any(), any()) } returns TmdbMoviePage(results = emptyList())

        val result = resolver.resolve(
            rawTitle = "Some Movie",
            existingMovieId = 9988
        )
        assertEquals("unknown_movie", result.mediaId)
        assertEquals(9988, result.movieId)
        assertFalse(MediaIdHelper.isCanonical(result.mediaId))
    }

    @Test
    fun `resolve for NAS movie absent from YTS falls back to TMDB searchMovies and genuine TMDB movieId`() = runTest {
        coEvery { ytsRepository.searchMovies(any(), any(), any()) } returns MovieDetails(
            status = "ok",
            data = Data(movies = emptyList(), movieCount = 0)
        )
        val tmdbMatch = TmdbMovieSummary(
            id = 25376,
            title = "El secreto de sus ojos",
            originalTitle = "El secreto de sus ojos",
            releaseDate = "2009-08-13",
            posterPath = "/secret_poster.jpg"
        )
        coEvery { tmdbRepository.searchMovies("El secreto de sus ojos", 2009, any()) } returns TmdbMoviePage(
            results = listOf(tmdbMatch)
        )
        coEvery { tmdbRepository.getMovieImdbId(25376) } returns "tt1305806"

        val result = resolver.resolve(
            rawTitle = "El.secreto.de.sus.ojos.2009.1080p.BluRay.mkv",
            filePath = "/storage/nas/movies/El.secreto.de.sus.ojos.2009.1080p.BluRay.mkv"
        )

        assertEquals("tt1305806", result.mediaId)
        assertEquals("tt1305806", result.imdbId)
        assertEquals("https://image.tmdb.org/t/p/w500/secret_poster.jpg", result.posterUrl)
        assertFalse(result.isEpisode)
        assertTrue(MediaIdHelper.isCanonical(result.mediaId))
    }

    @Test
    fun `selectBestTmdbMovieCandidate prefers exact title or originalTitle and release year`() {
        val oldFilm = TmdbMovieSummary(
            id = 100,
            title = "Twilight",
            originalTitle = "Twilight",
            releaseDate = "1998-03-06"
        )
        val sequel = TmdbMovieSummary(
            id = 101,
            title = "The Twilight Saga: New Moon",
            originalTitle = "The Twilight Saga: New Moon",
            releaseDate = "2009-11-18"
        )
        val target = TmdbMovieSummary(
            id = 8966,
            title = "Crepúsculo",
            originalTitle = "Twilight",
            releaseDate = "2008-11-20"
        )

        val matched = MediaMetadataResolver.selectBestTmdbMovieCandidate(
            movies = listOf(oldFilm, sequel, target),
            candidateTitle = "Twilight",
            expectedYear = 2008
        )

        assertEquals(8966, matched?.id)
    }

    @Test
    fun `selectBestTmdbMovieCandidate prefers exact title with plus or minus 1 year tolerance over non-matching title with exact year`() {
        val unrelatedSameYear = TmdbMovieSummary(
            id = 500,
            title = "Relatos salvajes: Making Of",
            originalTitle = "Relatos salvajes: Making Of",
            releaseDate = "2015-01-10"
        )
        val festivalReleaseTarget = TmdbMovieSummary(
            id = 265195,
            title = "Wild Tales",
            originalTitle = "Relatos salvajes",
            releaseDate = "2014-08-21"
        )

        val matched = MediaMetadataResolver.selectBestTmdbMovieCandidate(
            movies = listOf(unrelatedSameYear, festivalReleaseTarget),
            candidateTitle = "Relatos salvajes",
            expectedYear = 2015
        )

        assertEquals(265195, matched?.id)
    }

    @Test
    fun `resolve reuses imdbId and posterUrl from TorrentDownloadDao without redundant network calls`() = runTest {
        val hash = "abcdef1234567890abcdef1234567890abcdef12"
        coEvery { torrentDownloadDao.get(hash) } returns TorrentDownload(
            infoHash = hash,
            magnetUri = "magnet:?xt=urn:btih:$hash",
            title = "Twilight",
            state = "COMPLETED",
            progressPercent = 100,
            mediaPath = "/storage/downloads/Twilight.2008.1080p.mkv",
            castWhenReady = false,
            updatedAtMillis = 1_000L,
            movieId = 9988,
            imdbId = "tt1099212",
            posterUrl = "https://img.yts.mx/twilight.jpg"
        )

        val result = resolver.resolve(
            rawTitle = "Twilight.2008.1080p.mkv",
            infoHash = hash
        )

        assertEquals("tt1099212", result.mediaId)
        assertEquals("tt1099212", result.imdbId)
        assertEquals(9988, result.movieId)
        assertEquals("https://img.yts.mx/twilight.jpg", result.posterUrl)
        coVerify(exactly = 0) { ytsRepository.searchMovies(any(), any(), any()) }
        coVerify(exactly = 0) { ytsRepository.getMovieSummary(any()) }
        coVerify(exactly = 0) { tmdbRepository.searchMovies(any(), any(), any()) }
    }
}
