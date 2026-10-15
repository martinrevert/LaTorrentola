package com.martinrevert.latorrentola.network

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.martinrevert.latorrentola.database.WatchHistoryDao
import com.martinrevert.latorrentola.database.WatchHistoryEntity
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UserLibraryRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val firestore: FirebaseFirestore = mockk(relaxed = true)
    private val auth: FirebaseAuth = mockk(relaxed = true)
    private val watchHistoryDao: WatchHistoryDao = mockk(relaxed = true)

    private lateinit var repository: UserLibraryRepository

    @Before
    fun setUp() {
        clearMocks(firestore, auth, watchHistoryDao)
        every { auth.currentUser } returns null
        repository = UserLibraryRepository(firestore, auth, watchHistoryDao)
    }

    @Test
    fun `getWatchHistory should observe local room watch history and map to PlaybackProgress`() = runTest {
        val entity = WatchHistoryEntity(
            mediaId = "media_1_s1_e1",
            title = "Episode 1",
            positionMs = 12000L,
            durationMs = 60000L,
            timestamp = 1000L,
            isEpisode = true
        )
        every { watchHistoryDao.observeAll() } returns flowOf(listOf(entity))

        repository.getWatchHistory().test {
            val list = awaitItem()
            assertThat(list).hasSize(1)
            val item = list.first()
            assertThat(item.mediaId).isEqualTo("media_1_s1_e1")
            assertThat(item.positionMs).isEqualTo(12000L)
            assertThat(item.progressPercent).isEqualTo(20)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getPlaybackProgress should fallback to local room entity if not signed in`() = runTest {
        val entity = WatchHistoryEntity(
            mediaId = "101",
            title = "Movie 101",
            positionMs = 5000L,
            durationMs = 10000L,
            timestamp = 2000L,
            isEpisode = false
        )
        coEvery { watchHistoryDao.get("101") } returns entity

        val progress = repository.getPlaybackProgress("101")
        assertThat(progress).isNotNull()
        assertThat(progress?.title).isEqualTo("Movie 101")
        assertThat(progress?.positionMs).isEqualTo(5000L)
    }

    @Test
    fun `markMovieAsCompleted should save completed progress to room`() = runTest {
        val movie = com.martinrevert.latorrentola.model.YTS.Movie(id = 500, title = "Test Movie")
        repository.markMovieAsCompleted(movie)

        io.mockk.coVerify {
            watchHistoryDao.upsert(
                match { entity ->
                    entity.mediaId == "500" &&
                            entity.title == "Test Movie" &&
                            entity.positionMs == 1000L &&
                            entity.durationMs == 1000L &&
                            !entity.isEpisode
                }
            )
        }
    }

    @Test
    fun `markMovieAsUncompleted should delete progress from room`() = runTest {
        val movie = com.martinrevert.latorrentola.model.YTS.Movie(id = 500, title = "Test Movie")
        repository.markMovieAsUncompleted(movie)

        io.mockk.coVerify {
            watchHistoryDao.delete("500")
        }
    }

    @Test
    fun `markEpisodeAsCompleted should save episode completed progress`() = runTest {
        repository.markEpisodeAsCompleted(
            seriesId = 10,
            seasonNumber = 1,
            episodeNumber = 2,
            episodeTitle = "Pilot Part 2",
            seriesName = "Breaking Series"
        )

        io.mockk.coVerify {
            watchHistoryDao.upsert(
                match { entity ->
                    entity.mediaId == "media_10_s1_e2" &&
                            entity.title == "Breaking Series - S1E2 - Pilot Part 2" &&
                            entity.positionMs == 1000L &&
                            entity.durationMs == 1000L &&
                            entity.isEpisode
                }
            )
        }
    }

    @Test
    fun `markEpisodeAsUncompleted should delete episode progress from room`() = runTest {
        repository.markEpisodeAsUncompleted(
            seriesId = 10,
            seasonNumber = 1,
            episodeNumber = 2
        )

        io.mockk.coVerify {
            watchHistoryDao.delete("media_10_s1_e2")
        }
    }
}
