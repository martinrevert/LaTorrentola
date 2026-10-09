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
}
