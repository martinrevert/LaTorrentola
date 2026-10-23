package com.martinrevert.latorrentola.network

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.martinrevert.latorrentola.database.WatchHistoryDao
import com.martinrevert.latorrentola.database.WatchHistoryEntity
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import com.martinrevert.latorrentola.utils.CanonicalMediaInfo
import com.martinrevert.latorrentola.utils.MediaMetadataResolver
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WatchHistoryMigrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val firestore: FirebaseFirestore = mockk(relaxed = true)
    private val auth: FirebaseAuth = mockk(relaxed = true)
    private val watchHistoryDao: WatchHistoryDao = mockk(relaxed = true)
    private val resolver: MediaMetadataResolver = mockk(relaxed = true)

    private lateinit var repository: UserLibraryRepository

    @Before
    fun setUp() {
        every { auth.currentUser } returns null
        repository = UserLibraryRepository(
            firestore = firestore,
            auth = auth,
            watchHistoryDao = watchHistoryDao,
            mediaMetadataResolver = Lazy { resolver }
        )
    }

    @Test
    fun `cleanupAndMigrateWatchHistory migrates non-canonical movie title to canonical ID`() = runTest {
        val badItem = WatchHistoryEntity(
            mediaId = "Gladiator.2000.1080p.mkv",
            title = "Gladiator (2000)",
            positionMs = 30000L,
            durationMs = 120000L,
            timestamp = 5000L,
            isEpisode = false
        )
        coEvery { watchHistoryDao.getAll() } returns listOf(badItem)
        coEvery { resolver.resolve(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns CanonicalMediaInfo(
            mediaId = "12230",
            title = "Gladiator",
            movieId = 12230,
            isEpisode = false
        )

        repository.cleanupAndMigrateWatchHistory()

        // Verifies new canonical record upserted
        coVerify {
            watchHistoryDao.upsert(match {
                it.mediaId == "12230" && it.positionMs == 30000L
            })
        }
        // Verifies legacy bad record deleted
        coVerify {
            watchHistoryDao.delete("Gladiator.2000.1080p.mkv")
        }
    }

    @Test
    fun `cleanupAndMigrateWatchHistory migrates non-canonical episode filename to canonical mnemonic`() = runTest {
        val badItem = WatchHistoryEntity(
            mediaId = "Severance.S01E01.mkv",
            title = "Severance S01E01",
            positionMs = 45000L,
            durationMs = 90000L,
            timestamp = 6000L,
            isEpisode = true
        )
        coEvery { watchHistoryDao.getAll() } returns listOf(badItem)
        coEvery { resolver.resolve(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns CanonicalMediaInfo(
            mediaId = "media_111803_s1_e1",
            title = "Severance - S1E1",
            seriesId = 111803,
            seasonNumber = 1,
            episodeNumber = 1,
            isEpisode = true
        )

        repository.cleanupAndMigrateWatchHistory()

        coVerify {
            watchHistoryDao.upsert(match {
                it.mediaId == "media_111803_s1_e1" && it.positionMs == 45000L
            })
        }
        coVerify {
            watchHistoryDao.delete("Severance.S01E01.mkv")
        }
    }

    @Test
    fun `cleanupAndMigrateWatchHistory purges orphan unresolvable 40-character infoHash`() = runTest {
        val orphanHash = "092ea1d9309572ad7caf0868c3a94217dad15769"
        val badItem = WatchHistoryEntity(
            mediaId = orphanHash,
            title = "",
            positionMs = 1000L,
            durationMs = 5000L,
            timestamp = 2000L,
            isEpisode = false
        )
        coEvery { watchHistoryDao.getAll() } returns listOf(badItem)
        coEvery { resolver.resolve(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns CanonicalMediaInfo(
            mediaId = orphanHash,
            title = ""
        )

        repository.cleanupAndMigrateWatchHistory()

        // Should purge the unresolvable hash document
        coVerify {
            watchHistoryDao.delete(orphanHash)
        }
    }
}
