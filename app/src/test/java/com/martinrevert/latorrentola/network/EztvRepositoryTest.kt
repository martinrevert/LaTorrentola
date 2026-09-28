package com.martinrevert.latorrentola.network

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.EZTV.EztvResponse
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class EztvRepositoryTest {

    private val eztvService: EztvService = mockk()
    private lateinit var repository: EztvRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        repository = EztvRepository(eztvService)
    }

    @Test
    fun `getTorrentsForEpisode should filter torrents by season and episode number`() = runTest {
        val torrent1 = EztvTorrent(id = 1, season = "1", episode = "1", title = "Show S01E01 720p", seeds = 10)
        val torrent2 = EztvTorrent(id = 2, season = "1", episode = "2", title = "Show S01E02 1080p", seeds = 50)
        val torrent3 = EztvTorrent(id = 3, season = "1", episode = "1", title = "Show S01E01 1080p", seeds = 30)

        val response = EztvResponse(
            imdbId = "0944947",
            torrents = listOf(torrent1, torrent2, torrent3)
        )

        coEvery { eztvService.getTorrents("0944947", 1, 100) } returns response

        val result = repository.getTorrentsForEpisode("0944947", seasonNumber = 1, episodeNumber = 1)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.id }).containsExactly(3, 1).inOrder() // Sorted by seeds descending
    }

    @Test
    fun `getTorrentsForEpisode should remove tt prefix from imdbId`() = runTest {
        val response = EztvResponse(
            imdbId = "0944947",
            torrents = emptyList()
        )

        coEvery { eztvService.getTorrents("0944947", 1, 100) } returns response

        val result = repository.getTorrentsForEpisode("tt0944947", seasonNumber = 1, episodeNumber = 1)

        assertThat(result).isEmpty()
    }

    @Test
    fun `getTorrentsForEpisode should return empty list on exception`() = runTest {
        coEvery { eztvService.getTorrents(any(), any(), any()) } throws RuntimeException("Network error")

        val result = repository.getTorrentsForEpisode("0944947", seasonNumber = 1, episodeNumber = 1)

        assertThat(result).isEmpty()
    }
}
