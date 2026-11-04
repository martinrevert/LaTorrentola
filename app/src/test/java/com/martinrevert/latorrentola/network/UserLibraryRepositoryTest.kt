package com.martinrevert.latorrentola.network

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.martinrevert.latorrentola.database.WatchHistoryDao
import com.martinrevert.latorrentola.database.WatchHistoryEntity
import com.martinrevert.latorrentola.model.user.PlaybackProgress
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
        every { watchHistoryDao.observeAll(any()) } returns flowOf(listOf(entity))
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
            userId = "",
            mediaId = "101",
            title = "Movie 101",
            positionMs = 5000L,
            durationMs = 10000L,
            timestamp = 2000L,
            isEpisode = false
        )
        coEvery { watchHistoryDao.get("", "101") } returns entity

        val progress = repository.getPlaybackProgress("101")
        assertThat(progress).isNotNull()
        assertThat(progress?.title).isEqualTo("Movie 101")
        assertThat(progress?.positionMs).isEqualTo(5000L)
    }

    @Test
    fun `savePlaybackProgress with syncRemote false should only write to room without calling firestore`() = runTest {
        val progress = PlaybackProgress(
            mediaId = "200",
            title = "Movie 200",
            positionMs = 50000L,
            durationMs = 100000L,
            timestamp = 3000L,
            isEpisode = false
        )

        repository.savePlaybackProgress(progress, syncRemote = false)

        io.mockk.coVerify(exactly = 1) {
            watchHistoryDao.upsert(
                match { entity ->
                    entity.mediaId == "200" && entity.positionMs == 50000L
                }
            )
        }
        io.mockk.verify(exactly = 0) {
            firestore.collection(any())
        }
    }

    @Test
    fun `markMovieAsCompleted should save completed progress to room`() = runTest {
        val movie = com.martinrevert.latorrentola.model.YTS.Movie(id = 500, imdbCode = "tt0000500", title = "Test Movie")
        repository.markMovieAsCompleted(movie)

        io.mockk.coVerify {
            watchHistoryDao.upsert(
                match { entity ->
                    entity.mediaId == "tt0000500" &&
                            entity.title == "Test Movie" &&
                            entity.positionMs == 1000L &&
                            entity.durationMs == 1000L &&
                            !entity.isEpisode
                }
            )
        }
    }

    @Test
    fun `markMovieAsCompleted with id and title should save completed progress to room`() = runTest {
        repository.markMovieAsCompleted(movieId = 600, movieTitle = "Test Movie 600", posterUrl = null, imdbId = "tt0000600")

        io.mockk.coVerify {
            watchHistoryDao.upsert(
                match { entity ->
                    entity.mediaId == "tt0000600" &&
                            entity.title == "Test Movie 600" &&
                            entity.positionMs == 1000L &&
                            entity.durationMs == 1000L &&
                            !entity.isEpisode
                }
            )
        }
    }

    @Test
    fun `markMovieAsUncompleted should delete progress from room`() = runTest {
        val movie = com.martinrevert.latorrentola.model.YTS.Movie(id = 500, imdbCode = "tt0000500", title = "Test Movie")
        repository.markMovieAsUncompleted(movie)

        io.mockk.coVerify {
            watchHistoryDao.delete("", "tt0000500")
        }
    }

    @Test
    fun `markEpisodeAsCompleted should save episode completed progress`() = runTest {
        repository.markEpisodeAsCompleted(
            seriesId = 10,
            seasonNumber = 1,
            episodeNumber = 2,
            episodeTitle = "Pilot Part 2",
            seriesName = "Breaking Series",
            imdbId = "tt0000010"
        )

        io.mockk.coVerify {
            watchHistoryDao.upsert(
                match { entity ->
                    entity.mediaId == "tt0000010_s1_e2" &&
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
            episodeNumber = 2,
            imdbId = "tt0000010"
        )

        io.mockk.coVerify {
            watchHistoryDao.delete("", "tt0000010_s1_e2")
        }
    }

    @Test
    fun `getDownloadedMovies should return empty list when user is not authenticated`() = runTest {
        every { auth.currentUser } returns null
        repository.getDownloadedMovies().test {
            val list = awaitItem()
            assertThat(list).isEmpty()
            awaitComplete()
        }
    }

    @Test
    fun `getDownloadedEpisodes should return empty list when user is not authenticated`() = runTest {
        every { auth.currentUser } returns null
        repository.getDownloadedEpisodes().test {
            val list = awaitItem()
            assertThat(list).isEmpty()
            awaitComplete()
        }
    }

    @Test
    fun `getFavoriteMovies should return empty list when user is not authenticated`() = runTest {
        every { auth.currentUser } returns null
        repository.getFavoriteMovies().test {
            val list = awaitItem()
            assertThat(list).isEmpty()
            awaitComplete()
        }
    }

    @Test
    fun `getFavoriteTvSeries should return empty list when user is not authenticated`() = runTest {
        every { auth.currentUser } returns null
        repository.getFavoriteTvSeries().test {
            val list = awaitItem()
            assertThat(list).isEmpty()
            awaitComplete()
        }
    }

    @Test
    fun `isFavorite should query favoriteDao without calling firestore when favoriteDao is present`() = runTest {
        val favoriteDao: com.martinrevert.latorrentola.database.FavoriteDao = mockk(relaxed = true)
        val repo = UserLibraryRepository(
            firestore = firestore,
            auth = auth,
            watchHistoryDao = watchHistoryDao,
            favoriteDao = favoriteDao
        )
        coEvery { favoriteDao.isMovieFavorite("", 999) } returns true

        val result = repo.isFavorite(999)

        assertThat(result).isTrue()
        io.mockk.coVerify(exactly = 1) { favoriteDao.isMovieFavorite("", 999) }
        io.mockk.verify(exactly = 0) { firestore.collection(any()) }
    }

    @Test
    fun `getFavoriteMovies should observe room favoriteDao directly with zero firestore calls`() = runTest {
        val favoriteDao: com.martinrevert.latorrentola.database.FavoriteDao = mockk(relaxed = true)
        val mockUser: com.google.firebase.auth.FirebaseUser = mockk {
            every { uid } returns "user_abc"
        }
        every { auth.currentUser } returns mockUser

        val repo = UserLibraryRepository(
            firestore = firestore,
            auth = auth,
            watchHistoryDao = watchHistoryDao,
            favoriteDao = favoriteDao
        )
        val entity = com.martinrevert.latorrentola.database.FavoriteMovieEntity(
            userId = "user_abc",
            movieId = 10,
            title = "Inception",
            year = 2010
        )
        every { favoriteDao.observeFavoriteMovies("user_abc") } returns flowOf(listOf(entity))

        repo.getFavoriteMovies().test {
            val list = awaitItem()
            assertThat(list).hasSize(1)
            assertThat(list.first().title).isEqualTo("Inception")
            cancelAndIgnoreRemainingEvents()
        }

        io.mockk.verify(exactly = 0) { firestore.collection(any()) }
    }

    @Test
    fun `savePlaybackProgress should store userId in WatchHistoryEntity for multi-user preservation`() = runTest {
        val mockUser: com.google.firebase.auth.FirebaseUser = mockk {
            every { uid } returns "user_family_member"
        }
        every { auth.currentUser } returns mockUser

        val progress = PlaybackProgress(
            mediaId = "300",
            title = "Family Movie",
            positionMs = 15000L,
            durationMs = 60000L,
            timestamp = 5000L,
            isEpisode = false
        )

        repository.savePlaybackProgress(progress, syncRemote = false)

        io.mockk.coVerify(exactly = 1) {
            watchHistoryDao.upsert(
                match { entity ->
                    entity.userId == "user_family_member" &&
                            entity.mediaId == "300" &&
                            entity.positionMs == 15000L
                }
            )
        }
        io.mockk.verify(exactly = 0) { firestore.collection(any()) }
    }

    @Test
    fun `markMovieAsCompleted with imdbCode should save canonical IMDb mediaId to room`() = runTest {
        val movie = com.martinrevert.latorrentola.model.YTS.Movie(
            id = 500,
            imdbCode = "tt0111161",
            title = "The Shawshank Redemption"
        )
        repository.markMovieAsCompleted(movie)

        io.mockk.coVerify {
            watchHistoryDao.upsert(
                match { entity ->
                    entity.mediaId == "tt0111161" &&
                            entity.title == "The Shawshank Redemption" &&
                            entity.positionMs == 1000L &&
                            entity.durationMs == 1000L &&
                            !entity.isEpisode
                }
            )
        }
    }

    @Test
    fun `markEpisodeAsCompleted with imdbId should save canonical IMDb episode mediaId`() = runTest {
        repository.markEpisodeAsCompleted(
            seriesId = 1399,
            seasonNumber = 1,
            episodeNumber = 2,
            episodeTitle = "The Kingsroad",
            seriesName = "Game of Thrones",
            imdbId = "tt0944947"
        )

        io.mockk.coVerify {
            watchHistoryDao.upsert(
                match { entity ->
                    entity.mediaId == "tt0944947_s1_e2" &&
                            entity.title == "Game of Thrones - S1E2 - The Kingsroad" &&
                            entity.positionMs == 1000L &&
                            entity.durationMs == 1000L &&
                            entity.isEpisode
                }
            )
        }
    }

    @Test
    fun `getWatchHistory in guest mode observes only records with empty userId`() = runTest {
        every { auth.currentUser } returns null
        every { watchHistoryDao.observeAll("") } returns flowOf(emptyList())

        repository.getWatchHistory().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        io.mockk.verify { watchHistoryDao.observeAll("") }
    }

    @Test
    fun `addFavorite immediately persists to Room before resolving remote metadata`() = runTest {
        val favoriteDao: com.martinrevert.latorrentola.database.FavoriteDao = mockk(relaxed = true)
        val resolver: com.martinrevert.latorrentola.utils.MediaMetadataResolver = mockk(relaxed = true)
        coEvery { resolver.getMovieImdbId(any()) } returns "tt9999999"

        val repo = UserLibraryRepository(
            firestore = firestore,
            auth = auth,
            watchHistoryDao = watchHistoryDao,
            favoriteDao = favoriteDao,
            mediaMetadataResolver = dagger.Lazy { resolver }
        )

        val movie = com.martinrevert.latorrentola.model.YTS.Movie(
            id = 550,
            title = "Fight Club",
            imdbCode = ""
        )
        repo.addFavorite(movie)

        io.mockk.coVerify {
            favoriteDao.upsertMovie(
                match { it.movieId == 550 && it.title == "Fight Club" }
            )
        }
    }

    @Test
    fun `markAsDownloaded handles DownloadedMovie with blank hash without exception`() = runTest {
        val download = com.martinrevert.latorrentola.model.user.DownloadedMovie(
            movieId = 101,
            imdbId = "tt1010101",
            hash = ""
        )
        repository.markAsDownloaded(download)
    }

    @Test
    fun `removeMovieDownloads with non-positive movieId and blank imdbId does not delete`() = runTest {
        val downloadRecordDao: com.martinrevert.latorrentola.database.DownloadRecordDao = mockk(relaxed = true)
        val repo = UserLibraryRepository(
            firestore = firestore,
            auth = auth,
            watchHistoryDao = watchHistoryDao,
            downloadRecordDao = downloadRecordDao
        )

        repo.removeMovieDownloads(0, null)

        io.mockk.coVerify(exactly = 0) {
            downloadRecordDao.deleteMoviesByMovieId(any(), any())
        }
    }

    @Test
    fun `markAsDownloaded stores movie download in Room and Firestore under canonical IMDb ID`() = runTest {
        val downloadRecordDao: com.martinrevert.latorrentola.database.DownloadRecordDao = mockk(relaxed = true)
        val mockUser = mockk<com.google.firebase.auth.FirebaseUser>()
        every { mockUser.uid } returns "user_123"
        every { auth.currentUser } returns mockUser

        val usersCol = mockk<com.google.firebase.firestore.CollectionReference>(relaxed = true)
        val userDoc = mockk<com.google.firebase.firestore.DocumentReference>(relaxed = true)
        val downloadsCol = mockk<com.google.firebase.firestore.CollectionReference>(relaxed = true)
        val docRef = mockk<com.google.firebase.firestore.DocumentReference>(relaxed = true)
        val completedTask = com.google.android.gms.tasks.Tasks.forResult<Void>(null)

        every { firestore.collection("users") } returns usersCol
        every { usersCol.document("user_123") } returns userDoc
        every { userDoc.collection("downloads") } returns downloadsCol
        every { downloadsCol.document(any()) } returns docRef
        every { docRef.set(any()) } returns completedTask
        every { docRef.delete() } returns completedTask

        val repo = UserLibraryRepository(
            firestore = firestore,
            auth = auth,
            watchHistoryDao = watchHistoryDao,
            downloadRecordDao = downloadRecordDao
        )

        val download = com.martinrevert.latorrentola.model.user.DownloadedMovie(
            movieId = 278,
            imdbId = "tt0111161",
            movieTitle = "The Shawshank Redemption",
            hash = "sample_torrent_hash_12345"
        )
        repo.markAsDownloaded(download)

        io.mockk.coVerify {
            downloadRecordDao.upsertMovie(
                match { it.imdbId == "tt0111161" && it.movieId == 278 }
            )
        }
        io.mockk.verify {
            downloadsCol.document("tt0111161")
        }
    }

    @Test
    fun `markEpisodeAsDownloaded stores TV episode in Room and Firestore under canonical IMDb ID`() = runTest {
        val downloadRecordDao: com.martinrevert.latorrentola.database.DownloadRecordDao = mockk(relaxed = true)
        val mockUser = mockk<com.google.firebase.auth.FirebaseUser>()
        every { mockUser.uid } returns "user_123"
        every { auth.currentUser } returns mockUser

        val usersCol = mockk<com.google.firebase.firestore.CollectionReference>(relaxed = true)
        val userDoc = mockk<com.google.firebase.firestore.DocumentReference>(relaxed = true)
        val tvDownloadsCol = mockk<com.google.firebase.firestore.CollectionReference>(relaxed = true)
        val docRef = mockk<com.google.firebase.firestore.DocumentReference>(relaxed = true)
        val completedTask = com.google.android.gms.tasks.Tasks.forResult<Void>(null)

        every { firestore.collection("users") } returns usersCol
        every { usersCol.document("user_123") } returns userDoc
        every { userDoc.collection("tv_downloads") } returns tvDownloadsCol
        every { tvDownloadsCol.document(any()) } returns docRef
        every { docRef.set(any()) } returns completedTask
        every { docRef.delete() } returns completedTask

        val repo = UserLibraryRepository(
            firestore = firestore,
            auth = auth,
            watchHistoryDao = watchHistoryDao,
            downloadRecordDao = downloadRecordDao
        )

        val download = com.martinrevert.latorrentola.model.user.DownloadedEpisode(
            seriesId = 1399,
            imdbId = "tt0944947",
            seriesName = "Game of Thrones",
            seasonNumber = 1,
            episodeNumber = 1,
            hash = "sample_ep_hash_5678"
        )
        repo.markEpisodeAsDownloaded(download)

        io.mockk.coVerify {
            downloadRecordDao.upsertEpisode(
                match { it.imdbId == "tt0944947" && it.seriesId == 1399 && it.seasonNumber == 1 && it.episodeNumber == 1 }
            )
        }
        io.mockk.verify {
            tvDownloadsCol.document("tt0944947_s1_e1")
        }
    }

    @Test
    fun `markMovieAsUncompleted with null imdbId resolves IMDb ID and deletes progress`() = runTest {
        val resolver: com.martinrevert.latorrentola.utils.MediaMetadataResolver = mockk(relaxed = true)
        coEvery { resolver.getMovieImdbId(278) } returns "tt0111161"
        val repo = UserLibraryRepository(
            firestore = firestore,
            auth = auth,
            watchHistoryDao = watchHistoryDao,
            mediaMetadataResolver = dagger.Lazy { resolver }
        )

        repo.markMovieAsUncompleted(movieId = 278, imdbId = null)

        io.mockk.coVerify {
            watchHistoryDao.delete("", "tt0111161")
        }
    }

    @Test
    fun `markEpisodeAsUncompleted with null imdbId resolves IMDb ID and deletes episode progress`() = runTest {
        val resolver: com.martinrevert.latorrentola.utils.MediaMetadataResolver = mockk(relaxed = true)
        coEvery { resolver.getTvImdbId(1399) } returns "tt0944947"
        val repo = UserLibraryRepository(
            firestore = firestore,
            auth = auth,
            watchHistoryDao = watchHistoryDao,
            mediaMetadataResolver = dagger.Lazy { resolver }
        )

        repo.markEpisodeAsUncompleted(seriesId = 1399, seasonNumber = 1, episodeNumber = 2, imdbId = null)

        io.mockk.coVerify {
            watchHistoryDao.delete("", "tt0944947_s1_e2")
        }
    }

    @Test
    fun `savePlaybackProgress drops sentinel keys unknown_movie and unknown_episode`() = runTest {
        val movieProgress = PlaybackProgress(
            mediaId = "unknown_movie",
            title = "Unknown",
            positionMs = 100L,
            durationMs = 200L
        )
        val epProgress = PlaybackProgress(
            mediaId = "unknown_episode",
            title = "Unknown Ep",
            positionMs = 100L,
            durationMs = 200L
        )
        val blankProgress = PlaybackProgress(
            mediaId = "   ",
            title = "Blank",
            positionMs = 100L,
            durationMs = 200L
        )

        repository.savePlaybackProgress(movieProgress)
        repository.savePlaybackProgress(epProgress)
        repository.savePlaybackProgress(blankProgress)

        io.mockk.coVerify(exactly = 0) {
            watchHistoryDao.upsert(any())
        }
    }

    @Test
    fun `markEpisodeAsCompleted without imdbId does not use tv_ prefix for fallback document ID`() = runTest {
        val downloadRecordDao: com.martinrevert.latorrentola.database.DownloadRecordDao = mockk(relaxed = true)
        val repo = UserLibraryRepository(
            firestore = firestore,
            auth = auth,
            watchHistoryDao = watchHistoryDao,
            downloadRecordDao = downloadRecordDao
        )

        repo.markEpisodeAsCompleted(
            seriesId = 1234,
            seasonNumber = 1,
            episodeNumber = 1,
            episodeTitle = "Pilot",
            seriesName = "No IMDb Show",
            imdbId = null
        )

        io.mockk.coVerify {
            downloadRecordDao.upsertEpisode(
                match { it.hash == "1234_s1_e1" && !it.hash.startsWith("tv_") }
            )
        }
    }
}



