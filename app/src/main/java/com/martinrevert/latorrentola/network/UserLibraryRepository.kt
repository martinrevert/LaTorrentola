package com.martinrevert.latorrentola.network

import android.util.Log
import com.martinrevert.latorrentola.utils.AppLogger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.martinrevert.latorrentola.database.DownloadRecordDao
import com.martinrevert.latorrentola.database.DownloadedEpisodeEntity
import com.martinrevert.latorrentola.database.DownloadedMovieEntity
import com.martinrevert.latorrentola.database.FavoriteDao
import com.martinrevert.latorrentola.database.FavoriteMovieEntity
import com.martinrevert.latorrentola.database.FavoriteTvSeriesEntity
import com.martinrevert.latorrentola.database.SyncMetadataDao
import com.martinrevert.latorrentola.database.SyncMetadataEntity
import com.martinrevert.latorrentola.database.TorrentDownloadDao
import com.martinrevert.latorrentola.database.WatchHistoryDao
import com.martinrevert.latorrentola.database.WatchHistoryEntity
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.user.DownloadedEpisode
import com.martinrevert.latorrentola.model.user.DownloadedMovie
import com.martinrevert.latorrentola.model.user.FavoriteTvSeries
import com.martinrevert.latorrentola.model.user.PlaybackProgress
import com.martinrevert.latorrentola.utils.MediaIdHelper
import com.martinrevert.latorrentola.utils.MediaMetadataResolver
import com.martinrevert.latorrentola.utils.PreferenceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for user library, favorites, downloads, and watch progress.
 *
 * Local Room database acts as the primary data store (SSOT) to eliminate recurring
 * Firestore read costs and keep usage within the Free Tier. Firestore is used strictly as a
 * remote backup and cross-device sync layer.
 */
@Singleton
class UserLibraryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val watchHistoryDao: WatchHistoryDao,
    private val favoriteDao: FavoriteDao? = null,
    private val downloadRecordDao: DownloadRecordDao? = null,
    private val syncMetadataDao: SyncMetadataDao? = null,
    private val torrentDownloadDao: TorrentDownloadDao? = null,
    private val mediaMetadataResolver: dagger.Lazy<MediaMetadataResolver>? = null,
    private val preferenceManager: dagger.Lazy<PreferenceManager>? = null
) {
    /** Authenticated Firebase UID used to scope cloud and local library records. */
    val userId: String? get() = auth.currentUser?.uid

    /** Returns the active user ID for local Room scoping, fallback to empty string for guest mode. */
    private val currentUserId: String get() = userId.orEmpty()

    /** Observes the active user's movie downloads from Room cache. */
    fun getDownloadedMovies(): Flow<List<DownloadedMovie>> {
        if (downloadRecordDao != null) {
            return downloadRecordDao.observeDownloadedMovies(currentUserId).map { list ->
                list.map { it.toDownloadedMovie() }
            }
        }
        val uid = userId ?: return flowOf(emptyList())
        return callbackFlow {
            val subscription = firestore.collection("users")
                .document(uid)
                .collection("downloads")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        AppLogger.e("Firestore", "Error fetching downloads: ${error.message}", error)
                        trySend(emptyList())
                        return@addSnapshotListener
                    }

                    val downloads = snapshot?.documents?.mapNotNull {
                        it.toObject(DownloadedMovie::class.java)
                    }?.sortedByDescending { it.timestamp } ?: emptyList()

                    trySend(downloads)
                }

            awaitClose { subscription.remove() }
        }
    }

    /** Saves or replaces a download record in local Room and syncs to Firestore under canonical IMDb format. */
    suspend fun markAsDownloaded(download: DownloadedMovie) {
        val uid = currentUserId
        val effectiveImdb = MediaIdHelper.normalizeImdbId(download.imdbId ?: download.movie?.imdbCode)
        val canonicalDocId = MediaIdHelper.forMovie(effectiveImdb, download.movieId, download.movieTitle)
        val effectiveHash = download.hash.ifBlank { canonicalDocId }
        val sanitizedDownload = download.copy(
            imdbId = effectiveImdb,
            hash = effectiveHash
        )

        try {
            downloadRecordDao?.upsertMovie(DownloadedMovieEntity.from(uid, sanitizedDownload))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("UserLibraryRepository", "Error saving movie download locally: ${e.message}", e)
        }

        val remoteUid = userId ?: return
        try {
            firestore.collection("users")
                .document(remoteUid)
                .collection("downloads")
                .document(canonicalDocId)
                .set(sanitizedDownload)
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("Firestore", "Error saving download: ${e.message}", e)
        }
    }

    /** Observes the active user's TV episode downloads from Room cache. */
    fun getDownloadedEpisodes(): Flow<List<DownloadedEpisode>> {
        if (downloadRecordDao != null) {
            return downloadRecordDao.observeDownloadedEpisodes(currentUserId).map { list ->
                list.map { it.toDownloadedEpisode() }
            }
        }
        val uid = userId ?: return flowOf(emptyList())
        return callbackFlow {
            val subscription = firestore.collection("users")
                .document(uid)
                .collection("tv_downloads")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        AppLogger.e("Firestore", "Error fetching TV downloads: ${error.message}", error)
                        trySend(emptyList())
                        return@addSnapshotListener
                    }

                    val downloads = snapshot?.documents?.mapNotNull {
                        it.toObject(DownloadedEpisode::class.java)
                    }?.sortedByDescending { it.timestamp } ?: emptyList()

                    trySend(downloads)
                }

            awaitClose { subscription.remove() }
        }
    }

    /** Saves or replaces a TV episode download record in Room and syncs to Firestore under canonical IMDb format. */
    suspend fun markEpisodeAsDownloaded(download: DownloadedEpisode) {
        val uid = currentUserId
        val effectiveImdb = MediaIdHelper.normalizeImdbId(download.imdbId)
        val canonicalDocId = MediaIdHelper.forEpisode(
            imdbId = effectiveImdb,
            seriesId = download.seriesId,
            seasonNumber = download.seasonNumber,
            episodeNumber = download.episodeNumber,
            title = download.seriesName
        )
        val effectiveHash = download.hash.ifBlank { canonicalDocId }
        val sanitizedDownload = download.copy(
            imdbId = effectiveImdb,
            hash = effectiveHash
        )

        try {
            downloadRecordDao?.upsertEpisode(DownloadedEpisodeEntity.from(uid, sanitizedDownload))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("UserLibraryRepository", "Error saving TV episode download locally: ${e.message}", e)
        }

        val remoteUid = userId ?: return
        try {
            firestore.collection("users")
                .document(remoteUid)
                .collection("tv_downloads")
                .document(canonicalDocId)
                .set(sanitizedDownload)
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("Firestore", "Error saving TV episode download: ${e.message}", e)
        }
    }

    /** Removes a movie download record by its torrent hash locally and remotely. */
    suspend fun removeDownloadedMovieByHash(hash: String) {
        if (hash.isBlank()) return
        val uid = currentUserId
        try {
            downloadRecordDao?.deleteMovieByHash(uid, hash)
            torrentDownloadDao?.delete(hash)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("UserLibraryRepository", "Error deleting local movie download by hash: ${e.message}")
        }

        val remoteUid = userId ?: return
        try {
            firestore.collection("users")
                .document(remoteUid)
                .collection("downloads")
                .document(hash)
                .delete()
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("Firestore", "Error removing remote movie download by hash: ${e.message}", e)
        }
    }

    /** Removes a TV episode download record by its torrent hash locally and remotely. */
    suspend fun removeDownloadedEpisodeByHash(hash: String) {
        if (hash.isBlank()) return
        val uid = currentUserId
        try {
            downloadRecordDao?.deleteEpisodeByHash(uid, hash)
            torrentDownloadDao?.delete(hash)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("UserLibraryRepository", "Error deleting local episode download by hash: ${e.message}")
        }

        val remoteUid = userId ?: return
        try {
            firestore.collection("users")
                .document(remoteUid)
                .collection("tv_downloads")
                .document(hash)
                .delete()
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("Firestore", "Error removing remote episode download by hash: ${e.message}", e)
        }
    }

    /** Observes the active user's favorite movie records from Room cache. */
    fun getFavoriteMovies(): Flow<List<Movie>> {
        if (favoriteDao != null) {
            return favoriteDao.observeFavoriteMovies(currentUserId).map { list ->
                list.map { it.toMovie() }
            }
        }
        val uid = userId ?: return flowOf(emptyList())
        return callbackFlow {
            val subscription = firestore.collection("users")
                .document(uid)
                .collection("favorites")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        AppLogger.e("Firestore", "Error fetching favorites: ${error.message}", error)
                        trySend(emptyList())
                        return@addSnapshotListener
                    }

                    val favorites = snapshot?.documents?.filterNot {
                        isTvDocument(it)
                    }?.mapNotNull {
                        it.toObject(Movie::class.java)
                    } ?: emptyList()

                    trySend(favorites)
                }

            awaitClose { subscription.remove() }
        }
    }

    /** Saves or replaces [movie] in the user's favorites in Room and Firestore. */
    suspend fun addFavorite(movie: Movie) {
        val uid = currentUserId
        // Room-first: persist locally immediately for instant UI response
        try {
            favoriteDao?.upsertMovie(FavoriteMovieEntity.from(uid, movie))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("UserLibraryRepository", "Error saving favorite movie locally: ${e.message}", e)
        }

        var normalizedImdb = MediaIdHelper.normalizeImdbId(movie.imdbCode)
        if (normalizedImdb.isNullOrBlank() && movie.id > 0) {
            try {
                normalizedImdb = mediaMetadataResolver?.get()?.getMovieImdbId(movie.id)
                    ?.let { MediaIdHelper.normalizeImdbId(it) }
            } catch (_: Exception) {}
        }
        if (normalizedImdb.isNullOrBlank() && !movie.title.isNullOrBlank()) {
            try {
                normalizedImdb = mediaMetadataResolver?.get()?.resolve(movie.title, existingMovieId = movie.id)?.imdbId
                    ?.let { MediaIdHelper.normalizeImdbId(it) }
            } catch (_: Exception) {}
        }
        val finalMovie = if (!normalizedImdb.isNullOrBlank() && movie.imdbCode != normalizedImdb) {
            val updated = movie.copy(imdbCode = normalizedImdb)
            try {
                favoriteDao?.upsertMovie(FavoriteMovieEntity.from(uid, updated))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            updated
        } else {
            movie
        }

        val remoteUid = userId ?: return
        val targetDocId = normalizedImdb?.takeIf { it.isNotBlank() } ?: movie.id.toString()
        try {
            firestore.collection("users")
                .document(remoteUid)
                .collection("favorites")
                .document(targetDocId)
                .set(finalMovie)
                .await()

            try {
                firestore.collection("users")
                    .document(remoteUid)
                    .collection("favorites")
                    .document(targetDocId)
                    .set(mapOf("updatedAt" to System.currentTimeMillis()), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("Firestore", "Error adding favorite: ${e.message}", e)
        }
    }

    /** Removes [movie] from the user's favorites in Room and Firestore. */
    suspend fun removeFavorite(movie: Movie) {
        val uid = currentUserId
        val normalizedImdb = MediaIdHelper.normalizeImdbId(movie.imdbCode).orEmpty()
        try {
            favoriteDao?.deleteMovie(uid, movie.id)
            if (normalizedImdb.isNotBlank()) {
                favoriteDao?.deleteMovieByImdbId(uid, normalizedImdb)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("UserLibraryRepository", "Error removing favorite movie locally: ${e.message}", e)
        }

        val remoteUid = userId ?: return
        val targetDocId = normalizedImdb.ifBlank { movie.id.toString() }
        try {
            firestore.collection("users")
                .document(remoteUid)
                .collection("favorites")
                .document(targetDocId)
                .delete()
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("Firestore", "Error removing favorite: ${e.message}", e)
        }
    }

    /** Observes the active user's favorite TV series from Room cache. */
    fun getFavoriteTvSeries(): Flow<List<TmdbTvSummary>> {
        if (favoriteDao != null) {
            return favoriteDao.observeFavoriteTvSeries(currentUserId).map { list ->
                list.map { it.toTmdbTvSummary() }
            }
        }
        val uid = userId ?: return flowOf(emptyList())
        return callbackFlow {
            val subscription = firestore.collection("users")
                .document(uid)
                .collection("favorites")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        AppLogger.e("Firestore", "Error fetching favorite TV series: ${error.message}", error)
                        trySend(emptyList())
                        return@addSnapshotListener
                    }

                    val favorites = snapshot?.documents
                        ?.filter { isTvDocument(it) }
                        ?.mapNotNull { parseTvDocument(it) } ?: emptyList()
                    trySend(favorites)
                }

            awaitClose { subscription.remove() }
        }
    }

    private fun isTvDocument(doc: com.google.firebase.firestore.DocumentSnapshot): Boolean {
        return doc.id.startsWith("tv_") ||
            doc.contains("numberOfSeasons") ||
            doc.contains("firstAirDate") ||
            (doc.contains("name") && !doc.contains("title"))
    }

    private fun parseTvDocument(document: com.google.firebase.firestore.DocumentSnapshot): TmdbTvSummary? {
        val id = (document.get("id") as? Number)?.toInt() ?: return null
        val imdb = MediaIdHelper.normalizeImdbId(
            document.getString("imdbId") ?: document.getString("imdb_id") ?: if (MediaIdHelper.isCanonicalMovieId(document.id)) document.id else null
        )
        return FavoriteTvSeries(
            id = id,
            imdbId = imdb,
            name = document.getString("name"),
            posterPath = document.getString("posterPath"),
            firstAirDate = document.getString("firstAirDate"),
            voteAverage = (document.get("voteAverage") as? Number)?.toDouble(),
            genreIds = (document.get("genreIds") as? List<*>)
                .orEmpty()
                .mapNotNull { (it as? Number)?.toInt() },
            numberOfSeasons = (document.get("numberOfSeasons") as? Number)?.toInt(),
            numberOfEpisodes = (document.get("numberOfEpisodes") as? Number)?.toInt()
        ).toTmdbTvSummary()
    }

    /** Saves or replaces [series] in the user's TV favorites in Room and Firestore. */
    suspend fun addFavoriteTvSeries(series: TmdbTvSummary) {
        val uid = currentUserId
        // Room-first: persist locally immediately for instant UI response
        try {
            favoriteDao?.upsertTvSeries(FavoriteTvSeriesEntity.from(uid, series))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("UserLibraryRepository", "Error saving TV favorite locally: ${e.message}", e)
        }

        var normalizedImdb = series.effectiveImdbId
        if (normalizedImdb.isNullOrBlank() && series.id > 0) {
            try {
                normalizedImdb = mediaMetadataResolver?.get()?.getTvImdbId(series.id)
                    ?.let { MediaIdHelper.normalizeImdbId(it) }
            } catch (_: Exception) {}
        }
        if (normalizedImdb.isNullOrBlank() && !series.name.isNullOrBlank()) {
            try {
                normalizedImdb = mediaMetadataResolver?.get()?.resolve(series.name, existingSeriesId = series.id)?.imdbId
                    ?.let { MediaIdHelper.normalizeImdbId(it) }
            } catch (_: Exception) {}
        }
        val finalSeries = if (!normalizedImdb.isNullOrBlank() && series.effectiveImdbId != normalizedImdb) {
            val updated = series.copy(imdbId = normalizedImdb)
            try {
                favoriteDao?.upsertTvSeries(FavoriteTvSeriesEntity.from(uid, updated))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            updated
        } else {
            series
        }

        val remoteUid = userId ?: return
        val favorite = FavoriteTvSeries.from(finalSeries)
        val targetDocId = normalizedImdb?.takeIf { it.isNotBlank() } ?: "tv_${finalSeries.id}"
        try {
            firestore.collection("users")
                .document(remoteUid)
                .collection("favorites")
                .document(targetDocId)
                .set(
                    mapOf(
                        "id" to favorite.id,
                        "imdbId" to (favorite.imdbId ?: ""),
                        "name" to favorite.name,
                        "posterPath" to favorite.posterPath,
                        "firstAirDate" to favorite.firstAirDate,
                        "voteAverage" to favorite.voteAverage,
                        "genreIds" to favorite.genreIds.orEmpty(),
                        "numberOfSeasons" to favorite.numberOfSeasons,
                        "numberOfEpisodes" to favorite.numberOfEpisodes,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("Firestore", "Error saving TV favorite remotely: ${e.message}", e)
        }
    }

    /** Removes [series] from the user's TV favorites in Room and Firestore. */
    suspend fun removeFavoriteTvSeries(series: TmdbTvSummary) {
        val uid = currentUserId
        val normalizedImdb = series.effectiveImdbId.orEmpty()
        try {
            favoriteDao?.deleteTvSeries(uid, series.id)
            if (normalizedImdb.isNotBlank()) {
                favoriteDao?.deleteTvSeriesByImdbId(uid, normalizedImdb)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("UserLibraryRepository", "Error removing TV favorite locally: ${e.message}", e)
        }

        val remoteUid = userId ?: return
        val targetDocId = normalizedImdb.ifBlank { "tv_${series.id}" }
        try {
            firestore.collection("users")
                .document(remoteUid)
                .collection("favorites")
                .document(targetDocId)
                .delete()
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("Firestore", "Error removing TV favorite remotely: ${e.message}", e)
        }
    }

    /** Checks whether the active user's library contains [movieId], querying Room locally (0 Firestore reads). */
    suspend fun isFavorite(movieId: Int, imdbCode: String? = null): Boolean {
        val uid = currentUserId
        val normImdb = MediaIdHelper.normalizeImdbId(imdbCode).orEmpty()
        if (favoriteDao != null) {
            return if (normImdb.isNotBlank()) {
                favoriteDao.isMovieFavoriteByAnyId(uid, movieId, normImdb)
            } else {
                favoriteDao.isMovieFavorite(uid, movieId)
            }
        }
        val remoteUid = userId ?: return false
        val targetDocId = normImdb.ifBlank { movieId.toString() }
        return try {
            firestore.collection("users")
                .document(remoteUid)
                .collection("favorites")
                .document(targetDocId)
                .get()
                .await()
                .exists()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            false
        }
    }

    /** Checks whether the active user's library contains [seriesId], querying Room locally (0 Firestore reads). */
    suspend fun isTvFavorite(seriesId: Int, imdbId: String? = null): Boolean {
        val uid = currentUserId
        val normImdb = MediaIdHelper.normalizeImdbId(imdbId).orEmpty()
        if (favoriteDao != null) {
            return if (normImdb.isNotBlank()) {
                favoriteDao.isTvSeriesFavoriteByAnyId(uid, seriesId, normImdb)
            } else {
                favoriteDao.isTvSeriesFavorite(uid, seriesId)
            }
        }
        val remoteUid = userId ?: return false
        val targetDocId = normImdb.ifBlank { "tv_$seriesId" }
        return try {
            firestore.collection("users")
                .document(remoteUid)
                .collection("favorites")
                .document(targetDocId)
                .get()
                .await()
                .exists()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            false
        }
    }

    /** Observes whether the active user's library contains [movieId]. */
    fun isFavoriteFlow(movieId: Int, imdbCode: String? = null): Flow<Boolean> {
        val uid = currentUserId
        val normImdb = MediaIdHelper.normalizeImdbId(imdbCode).orEmpty()
        return if (normImdb.isNotBlank()) {
            favoriteDao?.isMovieFavoriteByAnyIdFlow(uid, movieId, normImdb) ?: flowOf(false)
        } else {
            favoriteDao?.isMovieFavoriteFlow(uid, movieId) ?: flowOf(false)
        }
    }

    /** Observes whether the active user's library contains [seriesId]. */
    fun isTvFavoriteFlow(seriesId: Int, imdbId: String? = null): Flow<Boolean> {
        val uid = currentUserId
        val normImdb = MediaIdHelper.normalizeImdbId(imdbId).orEmpty()
        return if (normImdb.isNotBlank()) {
            favoriteDao?.isTvSeriesFavoriteByAnyIdFlow(uid, seriesId, normImdb) ?: flowOf(false)
        } else {
            favoriteDao?.isTvSeriesFavoriteFlow(uid, seriesId) ?: flowOf(false)
        }
    }

    /** Saves the user's serialized language filter setting to Firestore. */
    suspend fun saveFilteredLanguages(languages: String) {
        val uid = userId ?: return
        Log.d("FirestoreSync", "Saving filtered languages for $uid: $languages")
        try {
            firestore.collection("users")
                .document(uid)
                .collection("settings")
                .document("config")
                .set(mapOf("filteredLanguages" to languages), SetOptions.merge())
                .await()
            Log.d("FirestoreSync", "Settings saved successfully in settings/config")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("FirestoreSync", "Error saving settings: ${e.message}")
            throw e
        }
    }

    /** Saves a bounded minimum rating preference to Firestore. */
    suspend fun saveMinimumRating(rating: Float) {
        val uid = userId ?: return
        val boundedRating = (kotlin.math.round(rating * 10.0f) / 10.0f).coerceIn(
            PreferenceManager.MINIMUM_RATING_MIN,
            PreferenceManager.MINIMUM_RATING_MAX
        )
        try {
            firestore.collection("users")
                .document(uid)
                .collection("settings")
                .document("config")
                .set(mapOf("minimumRating" to boundedRating), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("FirestoreSync", "Error saving minimum rating: ${e.message}")
            throw e
        }
    }

    /** Fetches the user's remotely stored language filter, if available. */
    suspend fun getRemoteFilteredLanguages(): String? {
        val uid = userId ?: return null
        Log.d("FirestoreSync", "Fetching remote settings for $uid")
        return try {
            val document = firestore.collection("users")
                .document(uid)
                .collection("settings")
                .document("config")
                .get()
                .await()
            val lang = document.getString("filteredLanguages")
            Log.d("FirestoreSync", "Fetched remote settings: $lang")
            lang
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("FirestoreSync", "Error fetching settings: ${e.message}")
            null
        }
    }

    /**
     * Saves or updates watch history playback progress.
     * Always writes immediately to the local Room database (SSOT).
     * Remote Firestore write is only performed when [syncRemote] is true (e.g. on pause, stop, player exit, or completion),
     * preventing recurring network write bills during active 10-second playback ticks.
     */
    suspend fun savePlaybackProgress(progress: PlaybackProgress, syncRemote: Boolean = true) {
        if (progress.mediaId.isBlank() || progress.mediaId == "unknown_movie" || progress.mediaId == "unknown_episode") {
            AppLogger.w("UserLibraryRepository", "Dropping attempt to save playback progress with invalid sentinel mediaId: '${progress.mediaId}'")
            return
        }
        val uid = currentUserId
        try {
            watchHistoryDao.upsert(
                WatchHistoryEntity(
                    userId = uid,
                    mediaId = progress.mediaId,
                    title = progress.title,
                    positionMs = progress.positionMs,
                    durationMs = progress.durationMs,
                    timestamp = progress.timestamp,
                    isEpisode = progress.isEpisode
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("UserLibraryRepository", "Error saving watch history locally: ${e.message}", e)
        }

        if (!syncRemote) return

        val remoteUid = userId ?: return
        try {
            firestore.collection("users")
                .document(remoteUid)
                .collection("watch_history")
                .document(progress.mediaId)
                .set(progress, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("Firestore", "Error saving playback progress: ${e.message}", e)
        }
    }

    private fun parsePlaybackProgress(doc: com.google.firebase.firestore.DocumentSnapshot): PlaybackProgress? {
        if (!doc.exists()) return null
        val parsed = doc.toObject(PlaybackProgress::class.java) ?: PlaybackProgress()
        val finalMediaId = doc.id.ifBlank { parsed.mediaId }
        if (finalMediaId.isBlank()) return null

        val finalTitle = parsed.title.ifBlank {
            doc.getString("title") ?: ""
        }
        val finalPos =
            if (parsed.positionMs > 0L) parsed.positionMs else (doc.getLong("positionMs") ?: doc.getLong("position_ms")
            ?: 0L)
        val finalDur =
            if (parsed.durationMs > 0L) parsed.durationMs else (doc.getLong("durationMs") ?: doc.getLong("duration_ms")
            ?: 0L)
        val finalTs =
            if (parsed.timestamp > 0L) parsed.timestamp else (doc.getLong("timestamp") ?: doc.getLong("updatedAt") ?: System.currentTimeMillis())
        val finalIsEpisode = parsed.isEpisode ||
                (doc.getBoolean("isEpisode") ?: doc.getBoolean("is_episode") ?: false) ||
                MediaIdHelper.isCanonicalEpisodeId(finalMediaId)

        return PlaybackProgress(
            mediaId = finalMediaId,
            title = finalTitle,
            positionMs = finalPos,
            durationMs = finalDur,
            timestamp = finalTs,
            isEpisode = finalIsEpisode
        )
    }

    /**
     * Retrieves saved playback progress locally from Room DB (0 Firestore reads in normal flow),
     * querying strictly by canonical IMDb ID.
     */
    suspend fun getPlaybackProgress(mediaId: String): PlaybackProgress? {
        if (mediaId.isBlank()) return null
        val uid = currentUserId
        val effectiveMediaId = MediaIdHelper.normalizeImdbId(mediaId) ?: mediaId

        var localEntity: WatchHistoryEntity? = null
        try {
            localEntity = watchHistoryDao.get(uid, effectiveMediaId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("UserLibraryRepository", "Error fetching local watch history: ${e.message}", e)
        }

        if (localEntity != null) {
            return PlaybackProgress(
                mediaId = localEntity.mediaId,
                title = localEntity.title,
                positionMs = localEntity.positionMs,
                durationMs = localEntity.durationMs,
                timestamp = localEntity.timestamp,
                isEpisode = localEntity.isEpisode
            )
        }
        return null
    }

    /**
     * Observes the user's watch history exclusively from local Room cache (0 continuous Firestore reads).
     */
    fun getWatchHistory(): Flow<List<PlaybackProgress>> {
        val uid = currentUserId
        val flow = if (uid.isNotBlank()) {
            watchHistoryDao.observeAll(uid)
        } else {
            watchHistoryDao.observeAll("")
        }

        return flow.map { entities ->
            entities.map {
                PlaybackProgress(
                    mediaId = it.mediaId,
                    title = it.title,
                    positionMs = it.positionMs,
                    durationMs = it.durationMs,
                    timestamp = it.timestamp,
                    isEpisode = it.isEpisode
                )
            }.sortedByDescending { it.timestamp }
        }
    }

    /** Deletes watch history playback progress locally from Room and from Firestore. */
    suspend fun deletePlaybackProgress(mediaId: String) {
        if (mediaId.isBlank()) return
        val uid = currentUserId
        try {
            watchHistoryDao.delete(uid, mediaId)
            if (uid.isNotBlank()) {
                watchHistoryDao.delete("", mediaId)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("UserLibraryRepository", "Error deleting watch history locally: ${e.message}", e)
        }

        val remoteUid = userId ?: return
        try {
            firestore.collection("users")
                .document(remoteUid)
                .collection("watch_history")
                .document(mediaId)
                .delete()
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("Firestore", "Error deleting playback progress remotely: ${e.message}", e)
        }
    }

    /** Marks a movie as 100% completed and downloaded in both Room and Firestore. */
    suspend fun markMovieAsCompleted(
        movieId: Int,
        movieTitle: String,
        posterUrl: String? = null,
        movie: Movie? = null,
        imdbId: String? = null
    ) {
        val title = movieTitle.ifBlank { movie?.title.orEmpty() }
        var effectiveImdb = MediaIdHelper.normalizeImdbId(imdbId ?: movie?.imdbCode)
        if (effectiveImdb.isNullOrBlank() && movieId > 0) {
            try {
                effectiveImdb = mediaMetadataResolver?.get()?.getMovieImdbId(movieId)?.let { MediaIdHelper.normalizeImdbId(it) }
            } catch (_: Exception) {}
        }
        if (effectiveImdb.isNullOrBlank() && title.isNotBlank()) {
            try {
                effectiveImdb = mediaMetadataResolver?.get()?.resolve(title, existingMovieId = movieId)?.imdbId?.let { MediaIdHelper.normalizeImdbId(it) }
            } catch (_: Exception) {}
        }
        val mediaId = MediaIdHelper.forMovie(effectiveImdb, movieId, title)
        val progress = PlaybackProgress(
            mediaId = mediaId,
            title = title,
            positionMs = 1000L,
            durationMs = 1000L,
            timestamp = System.currentTimeMillis(),
            isEpisode = false
        )
        savePlaybackProgress(progress, syncRemote = true)

        val downloadedMovie = DownloadedMovie(
            movieId = movieId,
            imdbId = effectiveImdb,
            movieTitle = title,
            quality = movie?.torrents?.firstOrNull()?.quality ?: "HD",
            hash = effectiveImdb?.ifBlank { null } ?: movieId.toString(),
            timestamp = System.currentTimeMillis(),
            movie = movie ?: Movie(id = movieId, imdbCode = effectiveImdb, title = title, mediumCoverImage = posterUrl)
        )
        markAsDownloaded(downloadedMovie)
    }

    /** Marks a movie as 100% completed and downloaded in both Room and Firestore. */
    suspend fun markMovieAsCompleted(movie: Movie) {
        markMovieAsCompleted(
            movieId = movie.id,
            movieTitle = movie.title.orEmpty(),
            posterUrl = movie.mediumCoverImage,
            movie = movie,
            imdbId = movie.imdbCode
        )
    }

    /** Clears watch progress and downloads for a movie in both Room and Firestore. */
    suspend fun markMovieAsUncompleted(movieId: Int, movieTitle: String? = null, imdbId: String? = null) {
        var effectiveImdb = MediaIdHelper.normalizeImdbId(imdbId)
        if (effectiveImdb.isNullOrBlank() && movieId > 0) {
            try {
                effectiveImdb = mediaMetadataResolver?.get()?.getMovieImdbId(movieId)?.let { MediaIdHelper.normalizeImdbId(it) }
            } catch (_: Exception) {}
        }
        if (effectiveImdb.isNullOrBlank() && !movieTitle.isNullOrBlank()) {
            try {
                effectiveImdb = mediaMetadataResolver?.get()?.resolve(movieTitle, existingMovieId = movieId)?.imdbId?.let { MediaIdHelper.normalizeImdbId(it) }
            } catch (_: Exception) {}
        }
        val mediaId = MediaIdHelper.forMovie(effectiveImdb, movieId, movieTitle)
        deletePlaybackProgress(mediaId)
        if (effectiveImdb != null && effectiveImdb != movieId.toString()) {
            deletePlaybackProgress(movieId.toString())
        }
        removeMovieDownloads(movieId, effectiveImdb)
    }

    /** Clears watch progress and downloads for a movie in both Room and Firestore. */
    suspend fun markMovieAsUncompleted(movie: Movie) {
        markMovieAsUncompleted(movie.id, movie.title, movie.imdbCode)
    }

    /** Deletes download records for a specific movie in Firestore and Room. */
    suspend fun removeMovieDownloads(movieId: Int, imdbId: String? = null) {
        var effectiveImdb = MediaIdHelper.normalizeImdbId(imdbId)
        if (effectiveImdb.isNullOrBlank() && movieId > 0) {
            try {
                effectiveImdb = mediaMetadataResolver?.get()?.getMovieImdbId(movieId)?.let { MediaIdHelper.normalizeImdbId(it) }
            } catch (_: Exception) {}
        }
        val safeImdb = effectiveImdb.orEmpty()
        if (movieId <= 0 && safeImdb.isBlank()) return
        val uid = currentUserId
        try {
            if (movieId > 0) {
                downloadRecordDao?.deleteMoviesByMovieId(uid, movieId)
            }
            if (safeImdb.isNotBlank()) {
                downloadRecordDao?.deleteMoviesByImdbId(uid, safeImdb)
            }
            if (movieId > 0) {
                torrentDownloadDao?.deleteForMovie(movieId)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("UserLibraryRepository", "Error deleting movie download local record: ${e.message}", e)
        }

        val remoteUid = userId ?: return
        try {
            if (safeImdb.isNotBlank()) {
                firestore.collection("users")
                    .document(remoteUid)
                    .collection("downloads")
                    .document(safeImdb)
                    .delete()
                    .await()
            } else if (movieId > 0) {
                firestore.collection("users")
                    .document(remoteUid)
                    .collection("downloads")
                    .document(movieId.toString())
                    .delete()
                    .await()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("Firestore", "Error removing remote movie download: ${e.message}", e)
        }
    }

    /** Marks a single TV episode as 100% completed and downloaded in both Room and Firestore. */
    suspend fun markEpisodeAsCompleted(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeTitle: String,
        seriesName: String,
        stillPath: String? = null,
        imdbId: String? = null
    ) {
        var effectiveImdb = MediaIdHelper.normalizeImdbId(imdbId)
        if (effectiveImdb.isNullOrBlank() && seriesId > 0) {
            try {
                effectiveImdb = mediaMetadataResolver?.get()?.getTvImdbId(seriesId)?.let { MediaIdHelper.normalizeImdbId(it) }
            } catch (_: Exception) {}
        }
        if (effectiveImdb.isNullOrBlank() && seriesName.isNotBlank()) {
            try {
                effectiveImdb = mediaMetadataResolver?.get()?.resolve(seriesName, existingSeriesId = seriesId)?.imdbId?.let { MediaIdHelper.normalizeImdbId(it) }
            } catch (_: Exception) {}
        }
        val mediaId = MediaIdHelper.forEpisode(effectiveImdb, seriesId, seasonNumber, episodeNumber, seriesName)
        val title = if (seriesName.isNotBlank()) {
            "$seriesName - S${seasonNumber}E${episodeNumber} - $episodeTitle"
        } else {
            episodeTitle
        }
        val progress = PlaybackProgress(
            mediaId = mediaId,
            title = title,
            positionMs = 1000L,
            durationMs = 1000L,
            timestamp = System.currentTimeMillis(),
            isEpisode = true
        )
        savePlaybackProgress(progress)

        val docId = if (!effectiveImdb.isNullOrBlank()) {
            "${effectiveImdb}_s${seasonNumber}_e${episodeNumber}"
        } else {
            "${seriesId}_s${seasonNumber}_e${episodeNumber}"
        }
        val downloadedEpisode = DownloadedEpisode(
            seriesId = seriesId,
            imdbId = effectiveImdb,
            seriesName = seriesName,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeName = episodeTitle,
            releaseTitle = if (seriesName.isNotBlank()) "$seriesName S${seasonNumber}E${episodeNumber}" else episodeTitle,
            quality = "HD",
            hash = docId,
            timestamp = System.currentTimeMillis(),
            stillPath = stillPath
        )
        markEpisodeAsDownloaded(downloadedEpisode)
    }

    /** Clears watch progress and download markers for an episode in both Room and Firestore. */
    suspend fun markEpisodeAsUncompleted(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        imdbId: String? = null
    ) {
        var effectiveImdb = MediaIdHelper.normalizeImdbId(imdbId)
        if (effectiveImdb.isNullOrBlank() && seriesId > 0) {
            try {
                effectiveImdb = mediaMetadataResolver?.get()?.getTvImdbId(seriesId)?.let { MediaIdHelper.normalizeImdbId(it) }
            } catch (_: Exception) {}
        }
        val mediaId = MediaIdHelper.forEpisode(effectiveImdb, seriesId, seasonNumber, episodeNumber)
        deletePlaybackProgress(mediaId)
        removeEpisodeDownloads(seriesId, seasonNumber, episodeNumber, effectiveImdb)
    }

    /** Deletes download records for a specific episode in Firestore and Room. */
    suspend fun removeEpisodeDownloads(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        imdbId: String? = null
    ) {
        var effectiveImdb = MediaIdHelper.normalizeImdbId(imdbId)
        if (effectiveImdb.isNullOrBlank() && seriesId > 0) {
            try {
                effectiveImdb = mediaMetadataResolver?.get()?.getTvImdbId(seriesId)?.let { MediaIdHelper.normalizeImdbId(it) }
            } catch (_: Exception) {}
        }
        val safeImdb = effectiveImdb.orEmpty()
        if (seriesId <= 0 && safeImdb.isBlank() && (seasonNumber < 0 || episodeNumber <= 0)) return
        val uid = currentUserId
        val canonicalDocId = MediaIdHelper.forEpisode(safeImdb, seriesId, seasonNumber, episodeNumber)
        try {
            downloadRecordDao?.deleteEpisodeByHash(uid, canonicalDocId)
            if (seriesId > 0 && seasonNumber >= 0 && episodeNumber > 0) {
                downloadRecordDao?.deleteEpisode(uid, seriesId, seasonNumber, episodeNumber)
            }
            if (safeImdb.isNotBlank() && seasonNumber >= 0 && episodeNumber > 0) {
                downloadRecordDao?.deleteEpisodeByAnyId(uid, seriesId, safeImdb, seasonNumber, episodeNumber)
            }
            if (seriesId > 0 && seasonNumber >= 0 && episodeNumber > 0) {
                torrentDownloadDao?.deleteForEpisode(seriesId, seasonNumber, episodeNumber)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("UserLibraryRepository", "Error deleting local episode download: ${e.message}", e)
        }

        val remoteUid = userId ?: return
        try {
            firestore.collection("users")
                .document(remoteUid)
                .collection("tv_downloads")
                .document(canonicalDocId)
                .delete()
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("Firestore", "Error removing remote episode download: ${e.message}", e)
        }
    }

    /** Marks all episodes in a season as completed and downloaded in both Room and Firestore. */
    suspend fun markSeasonAsCompleted(
        seriesId: Int,
        seasonNumber: Int,
        episodes: List<com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode>,
        seriesName: String,
        imdbId: String? = null
    ) {
        for (episode in episodes) {
            markEpisodeAsCompleted(
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                episodeNumber = episode.episodeNumber,
                episodeTitle = episode.name.orEmpty(),
                seriesName = seriesName,
                stillPath = episode.stillPath,
                imdbId = imdbId
            )
        }
    }

    /** Clears watch progress and download markers for all episodes in a season. */
    suspend fun markSeasonAsUncompleted(
        seriesId: Int,
        seasonNumber: Int,
        episodes: List<com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode>,
        imdbId: String? = null
    ) {
        var effectiveImdb = MediaIdHelper.normalizeImdbId(imdbId)
        if (effectiveImdb.isNullOrBlank() && seriesId > 0) {
            try {
                effectiveImdb = mediaMetadataResolver?.get()?.getTvImdbId(seriesId)?.let { MediaIdHelper.normalizeImdbId(it) }
            } catch (_: Exception) {}
        }
        for (episode in episodes) {
            markEpisodeAsUncompleted(
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                episodeNumber = episode.episodeNumber,
                imdbId = effectiveImdb
            )
        }
    }

    /**
     * Performs a one-shot bootstrap or delta sync from Firestore into local Room cache.
     * Guaranteed to use minimal reads:
     * - If full sync: reads collections once and inserts all into Room.
     * - If delta sync: reads only docs where updatedAt > lastSyncTimestamp.
     * - Respects a 15-minute throttle unless [forceFull] is true.
     */
    suspend fun syncFromCloud(forceFull: Boolean = false) {
        val uid = userId ?: return
        try {
            watchHistoryDao.claimUnassignedItems(uid)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("UserLibraryRepository", "Error claiming unassigned watch history: ${e.message}")
        }
        val metadataDao = syncMetadataDao ?: return
        val now = System.currentTimeMillis()

        val lastSync = metadataDao.getLastSyncTimestamp(uid, "library") ?: 0L
        val isLocalEmpty = (favoriteDao?.getFavoriteMovies(uid)?.isEmpty() ?: true) &&
            (watchHistoryDao.getAll(uid).isEmpty())
        if (!forceFull && !isLocalEmpty && lastSync > 0L && (now - lastSync) < 15 * 60 * 1000L) {
            // Throttled: synced recently, no need to read Firestore
            return
        }

        try {
            // 1. Sync Favorites
            val favRef = firestore.collection("users").document(uid).collection("favorites")
            val favQuery = if (lastSync > 0L && !forceFull && !isLocalEmpty) {
                favRef.whereGreaterThan("updatedAt", lastSync)
            } else {
                favRef
            }
            val favSnapshot = favQuery.get().await()
            val movieEntities = mutableListOf<FavoriteMovieEntity>()
            val tvEntities = mutableListOf<FavoriteTvSeriesEntity>()

            for (doc in favSnapshot.documents) {
                if (isTvDocument(doc)) {
                    val id = (doc.get("id") as? Number)?.toInt() ?: 0
                    val imdb = MediaIdHelper.normalizeImdbId(
                        doc.getString("imdbId") ?: doc.getString("imdb_id") ?: if (MediaIdHelper.isCanonicalMovieId(doc.id)) doc.id else null
                    ) ?: ""
                    val name = doc.getString("name").orEmpty()
                    val posterPath = doc.getString("posterPath")
                    val firstAirDate = doc.getString("firstAirDate")
                    val voteAverage = (doc.get("voteAverage") as? Number)?.toDouble()
                    val genreIds = (doc.get("genreIds") as? List<*>)
                        .orEmpty()
                        .mapNotNull { (it as? Number)?.toInt() }
                        .joinToString(",")
                    val numberOfSeasons = (doc.get("numberOfSeasons") as? Number)?.toInt()
                    val numberOfEpisodes = (doc.get("numberOfEpisodes") as? Number)?.toInt()
                    val updatedAt = doc.getLong("updatedAt") ?: now

                    tvEntities.add(
                        FavoriteTvSeriesEntity(
                            userId = uid,
                            seriesId = id,
                            imdbId = imdb,
                            name = name,
                            posterPath = posterPath,
                            firstAirDate = firstAirDate,
                            voteAverage = voteAverage,
                            genreIds = genreIds,
                            numberOfSeasons = numberOfSeasons,
                            numberOfEpisodes = numberOfEpisodes,
                            updatedAt = updatedAt
                        )
                    )
                } else {
                    val movie = doc.toObject(Movie::class.java)
                    if (movie != null && (movie.id > 0 || !movie.imdbCode.isNullOrBlank())) {
                        val normalizedImdb = MediaIdHelper.normalizeImdbId(
                            movie.imdbCode ?: if (MediaIdHelper.isCanonicalMovieId(doc.id)) doc.id else null
                        ) ?: ""
                        val enrichedMovie = if (movie.imdbCode.isNullOrBlank() && normalizedImdb.isNotBlank()) {
                            movie.copy(imdbCode = normalizedImdb)
                        } else {
                            movie
                        }
                        movieEntities.add(FavoriteMovieEntity.from(uid, enrichedMovie))
                    }
                }
            }

            if (movieEntities.isNotEmpty()) favoriteDao?.upsertMovies(movieEntities)
            if (tvEntities.isNotEmpty()) favoriteDao?.upsertTvSeriesList(tvEntities)

            // 2. Sync Movie Downloads
            val dlSnapshot = firestore.collection("users").document(uid).collection("downloads").get().await()
            val dlMovies = dlSnapshot.documents.mapNotNull { doc ->
                val dl = doc.toObject(DownloadedMovie::class.java) ?: return@mapNotNull null
                val normalizedImdb = MediaIdHelper.normalizeImdbId(
                    dl.imdbId ?: dl.movie?.imdbCode ?: if (MediaIdHelper.isCanonicalMovieId(doc.id)) doc.id else null
                ) ?: ""
                val finalDl = if (dl.imdbId.isNullOrBlank() && normalizedImdb.isNotBlank()) dl.copy(imdbId = normalizedImdb) else dl
                DownloadedMovieEntity.from(uid, finalDl)
            }
            if (dlMovies.isNotEmpty()) downloadRecordDao?.upsertMovies(dlMovies)

            // 3. Sync TV Downloads
            val tvDlSnapshot = firestore.collection("users").document(uid).collection("tv_downloads").get().await()
            val dlEpisodes = tvDlSnapshot.documents.mapNotNull { doc ->
                val ep = doc.toObject(DownloadedEpisode::class.java) ?: return@mapNotNull null
                val episodeTriple = MediaIdHelper.parseCanonicalEpisode(doc.id)
                val normalizedImdb = MediaIdHelper.normalizeImdbId(ep.imdbId ?: episodeTriple?.imdbId) ?: ""
                val finalEp = if (ep.imdbId.isNullOrBlank() && normalizedImdb.isNotBlank()) ep.copy(imdbId = normalizedImdb) else ep
                DownloadedEpisodeEntity.from(uid, finalEp)
            }
            if (dlEpisodes.isNotEmpty()) downloadRecordDao?.upsertEpisodes(dlEpisodes)

            // 4. Sync Watch History
            val historySnapshot = firestore.collection("users")
                .document(uid)
                .collection("watch_history")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(if (forceFull || isLocalEmpty) 500 else 100)
                .get()
                .await()

            for (doc in historySnapshot.documents) {
                val progress = parsePlaybackProgress(doc)
                if (progress != null) {
                    val local = watchHistoryDao.get(uid, progress.mediaId)
                    if (local == null || progress.timestamp > local.timestamp) {
                        watchHistoryDao.upsert(
                            WatchHistoryEntity(
                                userId = uid,
                                mediaId = progress.mediaId,
                                title = progress.title,
                                positionMs = progress.positionMs,
                                durationMs = progress.durationMs,
                                timestamp = progress.timestamp,
                                isEpisode = progress.isEpisode
                            )
                        )
                    }
                }
            }

            // 5. Sync Remote Settings (One-time fetch of settings/config)
            try {
                val settingsDoc = firestore.collection("users")
                    .document(uid)
                    .collection("settings")
                    .document("config")
                    .get()
                    .await()
                if (settingsDoc.exists()) {
                    val remoteRating = (settingsDoc.get("minimumRating") as? Number)?.toFloat()
                    if (remoteRating != null) {
                        preferenceManager?.get()?.setMinimumRating(remoteRating)
                    }
                    val remoteLanguages = settingsDoc.getString("filteredLanguages")
                    if (!remoteLanguages.isNullOrBlank()) {
                        preferenceManager?.get()?.setFilteredLanguages(remoteLanguages)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.w("UserLibraryRepository", "Error syncing remote settings: ${e.message}")
            }

            metadataDao.setLastSyncTimestamp(
                SyncMetadataEntity(userId = uid, collectionKey = "library", lastSyncTimestamp = now)
            )
            Log.i("UserLibraryRepository", "Successfully synced user library from cloud for $uid")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("UserLibraryRepository", "Error syncing library from cloud: ${e.message}", e)
        }
    }
}
