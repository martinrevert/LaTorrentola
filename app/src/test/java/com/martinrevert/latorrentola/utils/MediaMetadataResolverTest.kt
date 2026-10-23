package com.martinrevert.latorrentola.utils

import com.martinrevert.latorrentola.database.TorrentDownloadDao
import com.martinrevert.latorrentola.model.TMDB.TmdbTvPage
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.model.YTS.Data
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.YTS.MovieDetails
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.YtsRepository
import io.mockk.coEvery
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

        assertEquals("12230", result.mediaId)
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

        val result = resolver.resolve(
            rawTitle = "Severance.S01E01.Good.News.About.Hell.1080p.mkv",
            filePath = "/storage/nas/series/Severance/Season 1/Severance.S01E01.mkv"
        )

        assertEquals("media_111803_s1_e1", result.mediaId)
        assertEquals(111803, result.seriesId)
        assertEquals(1, result.seasonNumber)
        assertEquals(1, result.episodeNumber)
        assertTrue(result.isEpisode)
        assertTrue(MediaIdHelper.isCanonical(result.mediaId))
    }

    @Test
    fun `resolve when movieId already provided returns canonical ID directly`() = runTest {
        val result = resolver.resolve(
            rawTitle = "Some Movie",
            existingMovieId = 9988
        )
        assertEquals("9988", result.mediaId)
        assertEquals(9988, result.movieId)
        assertTrue(MediaIdHelper.isCanonical(result.mediaId))
    }
}
