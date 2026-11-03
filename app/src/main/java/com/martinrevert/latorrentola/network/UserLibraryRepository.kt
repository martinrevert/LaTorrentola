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

            // Clean up legacy document if it was under raw hash or numeric ID
            if (download.hash.isNotBlank() && download.hash != canonicalDocId) {
                try {
                    firestore.collection("users")
                        .document(remoteUid)
                        .collection("downloads")
                        .document(download.hash)
                        .delete()
                        .await()
                } catch (_: Exception) {}
            }
            if (download.movieId > 0 && download.movieId.toString() != canonicalDocId) {
                try {
                    firestore.collection("users")
                        .document(remoteUid)
                        .collection("downloads")
                        .document(download.movieId.toString())
                        .delete()
                        .await()
                } catch (_: Exception) {}
            }
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

            // Clean up legacy document if it was under raw hash or legacy tv_ prefix
            val legacyTvKey = "tv_${download.seriesId}_s${download.seasonNumber}_e${download.episodeNumber}"
            if (legacyTvKey != canonicalDocId) {
                try {
                    firestore.collection("users")
                        .document(remoteUid)
                        .collection("tv_downloads")
                        .document(legacyTvKey)
                        .delete()
                        .await()
                } catch (_: Exception) {}
            }
            if (download.hash.isNotBlank() && download.hash != canonicalDocId && download.hash != legacyTvKey) {
                try {
                    firestore.collection("users")
                        .document(remoteUid)
                        .collection("tv_downloads")
                        .document(download.hash)
                        .delete()
                        .await()
                } catch (_: Exception) {}
            }
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

            if (targetDocId != movie.id.toString()) {
                try {
                    firestore.collection("users")
                        .document(remoteUid)
                        .collection("favorites")
                        .document(movie.id.toString())
                        .delete()
                        .await()
                } catch (_: Exception) {}
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

            if (targetDocId != movie.id.toString()) {
                try {
                    firestore.collection("users")
                        .document(remoteUid)
                        .collection("favorites")
                        .document(movie.id.toString())
                        .delete()
                        .await()
                } catch (_: Exception) {}
            }
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

            if (targetDocId != "tv_${series.id}") {
                try {
                    firestore.collection("users")
                        .document(remoteUid)
                        .collection("favorites")
                        .document("tv_${series.id}")
                        .delete()
                        .await()
                } catch (_: Exception) {}
            }
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

            if (targetDocId != "tv_${series.id}") {
                try {
                    firestore.collection("users")
                        .document(remoteUid)
                        .collection("favorites")
                        .document("tv_${series.id}")
                        .delete()
                        .await()
                } catch (_: Exception) {}
            }
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
            val exists = firestore.collection("users")
                .document(remoteUid)
                .collection("favorites")
                .document(targetDocId)
                .get()
                .await()
                .exists()
            if (exists) true else if (targetDocId != movieId.toString()) {
                firestore.collection("users")
                    .document(remoteUid)
                    .collection("favorites")
                    .document(movieId.toString())
                    .get()
                    .await()
                    .exists()
            } else false
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
            val exists = firestore.collection("users")
                .document(remoteUid)
                .collection("favorites")
                .document(targetDocId)
                .get()
                .await()
                .exists()
            if (exists) true else if (targetDocId != "tv_$seriesId") {
                firestore.collection("users")
                    .document(remoteUid)
                    .collection("favorites")
                    .document("tv_$seriesId")
                    .get()
                    .await()
                    .exists()
            } else false
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
        val finalMediaId = if (MediaIdHelper.isCanonical(doc.id)) {
            doc.id
        } else {
            parsed.mediaId.ifBlank {
                doc.getString("mediaId") ?: doc.getString("media_id") ?: doc.id
            }
        }
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
                finalMediaId.startsWith("media_") ||
                (finalMediaId.contains("_s") && finalMediaId.contains("_e"))

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
     * falling back to Firestore only if missing locally and user is signed in.
     * Supports transparent migration from legacy TMDB/composite IDs to canonical IMDb IDs.
     */
    suspend fun getPlaybackProgress(mediaId: String): PlaybackProgress? {
        if (mediaId.isBlank()) return null
        val uid = currentUserId

        val candidateIds = linkedSetOf(mediaId)
        val ep = MediaIdHelper.parseCanonicalEpisode(mediaId)
        if (ep != null) {
            val sNum = ep.second
            val eNum = ep.third
            if (ep.first > 0) {
                candidateIds.add("tmdb_${ep.first}_s${sNum}_e${eNum}")
                candidateIds.add("tv_${ep.first}_s${sNum}_e${eNum}")
                candidateIds.add("media_${ep.first}_s${sNum}_e${eNum}")
            }
            if (!ep.imdbId.isNullOrBlank()) {
                candidateIds.add("${ep.imdbId}_s${sNum}_e${eNum}")
            }
        } else {
            val normImdb = MediaIdHelper.normalizeImdbId(mediaId)
            if (normImdb != null) {
                candidateIds.add(normImdb)
            }
            val numId = mediaId.removePrefix("tmdb_").removePrefix("media_").toIntOrNull()
            if (numId != null && numId > 0) {
                candidateIds.add("tmdb_$numId")
                candidateIds.add(numId.toString())
                candidateIds.add("media_$numId")
            }
        }

        // 1. Query local Room database first with candidate IDs (dual-key lookup)
        var localEntity: WatchHistoryEntity? = null
        try {
            localEntity = watchHistoryDao.get(uid, mediaId)
                ?: watchHistoryDao.getByAnyMediaId(uid, candidateIds.toList())
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
     * Scans Room and Firestore for any legacy or non-canonical watch history items
     * and migrates them directly to canonical IMDb identifiers.
     */
    suspend fun cleanupAndMigrateWatchHistory() {
        val uid = currentUserId
        if (uid.isNotBlank()) {
            try {
                watchHistoryDao.claimUnassignedItems(uid)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("UserLibraryRepository", "Error claiming unassigned items: ${e.message}")
            }
        }
        val resolver = mediaMetadataResolver?.get() ?: return

        try {
            val legacyItems = watchHistoryDao.getLegacyItems(uid)
            for (item in legacyItems) {
                migrateSingleItem(
                    item = PlaybackProgress(
                        mediaId = item.mediaId,
                        title = item.title,
                        positionMs = item.positionMs,
                        durationMs = item.durationMs,
                        timestamp = item.timestamp,
                        isEpisode = item.isEpisode
                    ),
                    resolver = resolver,
                    uid = uid
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("UserLibraryRepository", "Error during local watch history migration: ${e.message}")
        }

        val remoteUid = userId
        if (remoteUid != null) {
            try {
                val remoteDocs = firestore.collection("users")
                    .document(remoteUid)
                    .collection("watch_history")
                    .get()
                    .await()
                for (doc in remoteDocs.documents) {
                    val progress = parsePlaybackProgress(doc)
                    if (progress != null) {
                        val docIsCanonical = MediaIdHelper.isCanonical(doc.id)
                        val progressIsCanonical = MediaIdHelper.isCanonical(progress.mediaId)

                        if (docIsCanonical && progressIsCanonical && doc.id == progress.mediaId) {
                            val local = watchHistoryDao.get(uid, doc.id)
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
                            continue
                        }

                        val canonicalDocId = if (docIsCanonical) {
                            doc.id
                        } else if (progressIsCanonical) {
                            progress.mediaId
                        } else {
                            null
                        }

                        if (canonicalDocId != null) {
                            val canonicalProgress = progress.copy(mediaId = canonicalDocId)
                            val local = watchHistoryDao.get(uid, canonicalDocId)
                            if (local == null || canonicalProgress.timestamp > local.timestamp) {
                                watchHistoryDao.upsert(
                                    WatchHistoryEntity(
                                        userId = uid,
                                        mediaId = canonicalProgress.mediaId,
                                        title = canonicalProgress.title,
                                        positionMs = canonicalProgress.positionMs,
                                        durationMs = canonicalProgress.durationMs,
                                        timestamp = canonicalProgress.timestamp,
                                        isEpisode = canonicalProgress.isEpisode
                                    )
                                )
                            }
                            if (doc.id != canonicalDocId) {
                                firestore.collection("users")
                                    .document(remoteUid)
                                    .collection("watch_history")
                                    .document(canonicalDocId)
                                    .set(canonicalProgress, SetOptions.merge())
                                    .await()
                                try { doc.reference.delete().await() } catch (_: Exception) {}
                            }
                            if (progress.mediaId != canonicalDocId) {
                                deletePlaybackProgress(progress.mediaId)
                            }
                        } else {
                            migrateSingleItem(
                                item = progress,
                                resolver = resolver,
                                uid = uid,
                                rawDocId = doc.id
                            )
                        }
                    } else {
                        try { doc.reference.delete().await() } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("UserLibraryRepository", "Error during remote watch history migration: ${e.message}")
            }
        }
    }

    private suspend fun migrateSingleItem(
        item: PlaybackProgress,
        resolver: MediaMetadataResolver,
        uid: String?,
        rawDocId: String? = null
    ) {
        val oldId = rawDocId ?: item.mediaId
        if (MediaIdHelper.isCanonical(oldId)) {
            return
        }
        try {
            var canonicalId: String? = null
            var resolvedTitle: String = item.title
            var isEpisode: Boolean = item.isEpisode

            val episodeComponents = MediaIdHelper.parseCanonicalEpisode(oldId)
            val (detectedS, detectedE) = MediaMetadataResolver.extractSeasonAndEpisode(oldId, item.title)
            val isLikelyEpisode = episodeComponents != null ||
                item.isEpisode ||
                (oldId.contains("_s", ignoreCase = true) && oldId.contains("_e", ignoreCase = true)) ||
                (detectedS >= 0 && detectedE > 0)

            if (isLikelyEpisode) {
                isEpisode = true
                val sNum = when {
                    episodeComponents != null && episodeComponents.second >= 0 -> episodeComponents.second
                    detectedS >= 0 && detectedE > 0 -> detectedS
                    else -> 1
                }
                val eNum = when {
                    episodeComponents != null && episodeComponents.third > 0 -> episodeComponents.third
                    detectedE > 0 -> detectedE
                    else -> 1
                }
                var seriesId = when {
                    episodeComponents != null && episodeComponents.first > 0 -> episodeComponents.first
                    else -> {
                        val match = Regex("""^(?:tmdb|tv|media)?_?(\d+)_s""", RegexOption.IGNORE_CASE).find(oldId)
                        match?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    }
                }
                var imdbId = episodeComponents?.imdbId

                // Direct TMDB API lookup if seriesId > 0
                if (imdbId.isNullOrBlank() && seriesId > 0) {
                    try {
                        val remote = resolver.getTvImdbId(seriesId)
                        if (!remote.isNullOrBlank()) {
                            imdbId = remote
                        }
                    } catch (_: Exception) {}
                }

                if (imdbId.isNullOrBlank() && seriesId > 0) {
                    val fav = favoriteDao?.getFavoriteTvSeries(currentUserId)?.firstOrNull { it.seriesId == seriesId }
                    imdbId = fav?.imdbId?.takeIf(String::isNotBlank)
                        ?: downloadRecordDao?.getDownloadedEpisodes(currentUserId)?.firstOrNull { it.seriesId == seriesId }?.imdbId?.takeIf(String::isNotBlank)
                        ?: torrentDownloadDao?.getAll()?.firstOrNull { it.seriesId == seriesId }?.imdbId?.takeIf(String::isNotBlank)
                }

                if (imdbId.isNullOrBlank()) {
                    val resolved = resolver.resolve(rawTitle = item.title.ifBlank { oldId }, existingSeriesId = seriesId, existingSeasonNumber = sNum, existingEpisodeNumber = eNum)
                    if (!resolved.imdbId.isNullOrBlank()) {
                        imdbId = resolved.imdbId
                    }
                    if (seriesId <= 0 && resolved.seriesId > 0) {
                        seriesId = resolved.seriesId
                    }
                    if (resolved.title.isNotBlank()) {
                        resolvedTitle = resolved.title
                    }
                }

                val normImdb = MediaIdHelper.normalizeImdbId(imdbId)
                if (normImdb != null) {
                    canonicalId = "${normImdb}_s${sNum}_e${eNum}"
                }
            } else if (MediaIdHelper.isInfoHash(oldId)) {
                val resolved = resolver.resolve(rawTitle = item.title.ifBlank { oldId })
                if (MediaIdHelper.isCanonical(resolved.mediaId) && resolved.mediaId != oldId) {
                    canonicalId = resolved.mediaId
                    resolvedTitle = resolved.title.ifBlank { item.title }
                    isEpisode = resolved.isEpisode
                }
            } else {
                isEpisode = false
                val movieId = when {
                    oldId.all { it.isDigit() } -> oldId.toIntOrNull() ?: 0
                    oldId.startsWith("tmdb_", ignoreCase = true) -> oldId.removePrefix("tmdb_").removePrefix("TMDB_").toIntOrNull() ?: 0
                    oldId.startsWith("media_", ignoreCase = true) -> oldId.removePrefix("media_").removePrefix("MEDIA_").toIntOrNull() ?: 0
                    oldId.startsWith("movie_", ignoreCase = true) -> oldId.removePrefix("movie_").removePrefix("MOVIE_").toIntOrNull() ?: 0
                    else -> 0
                }
                var imdbId: String? = null

                // Direct TMDB API lookup if movieId > 0
                if (movieId > 0) {
                    try {
                        val remote = resolver.getMovieImdbId(movieId)
                        if (!remote.isNullOrBlank()) {
                            imdbId = remote
                        }
                    } catch (_: Exception) {}
                }

                if (imdbId.isNullOrBlank() && movieId > 0) {
                    val fav = favoriteDao?.getFavoriteMovies(currentUserId)?.firstOrNull { it.movieId == movieId }
                    imdbId = fav?.imdbId?.takeIf(String::isNotBlank)
                        ?: downloadRecordDao?.getDownloadedMovies(currentUserId)?.firstOrNull { it.movieId == movieId }?.imdbId?.takeIf(String::isNotBlank)
                }
                if (imdbId.isNullOrBlank()) {
                    val resolved = resolver.resolve(rawTitle = item.title.ifBlank { oldId }, existingMovieId = movieId)
                    if (!resolved.imdbId.isNullOrBlank()) {
                        imdbId = resolved.imdbId
                    }
                    if (resolved.title.isNotBlank()) {
                        resolvedTitle = resolved.title
                    }
                }
                val normImdb = MediaIdHelper.normalizeImdbId(imdbId)
                if (normImdb != null) {
                    canonicalId = normImdb
                }
            }

            if (canonicalId != null && canonicalId != oldId && MediaIdHelper.isCanonical(canonicalId)) {
                val migratedProgress = item.copy(
                    mediaId = canonicalId,
                    title = resolvedTitle,
                    isEpisode = isEpisode
                )
                savePlaybackProgress(migratedProgress)
                deletePlaybackProgress(oldId)
                if (rawDocId != null && rawDocId != item.mediaId) {
                    deletePlaybackProgress(item.mediaId)
                }
                Log.i("UserLibraryRepository", "Successfully migrated legacy watch history '$oldId' -> '$canonicalId'")
            } else if (MediaIdHelper.isInfoHash(oldId) && item.title.isBlank()) {
                deletePlaybackProgress(oldId)
                if (rawDocId != null && rawDocId != item.mediaId) {
                    deletePlaybackProgress(item.mediaId)
                }
                Log.i("UserLibraryRepository", "Purged orphan unresolvable infoHash '$oldId'")
            } else {
                Log.w("UserLibraryRepository", "Retained unresolvable legacy watch history '$oldId'")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("UserLibraryRepository", "Failed migrating item '$oldId': ${e.message}")
        }
    }

    /**
     * Scans Room and Firestore for any legacy or non-canonical download records (movies and episodes),
     * resolves their canonical IMDb identifiers, updates Room, and pushes canonical documents to Firestore
     * while safely removing legacy keys.
     */
    suspend fun cleanupAndMigrateDownloads() {
        val uid = currentUserId
        val remoteUid = userId
        val resolver = mediaMetadataResolver?.get()

        // 1. Migrate Movie Downloads
        try {
            val localMovies = downloadRecordDao?.getDownloadedMovies(uid).orEmpty()
            for (movieRecord in localMovies) {
                if (MediaIdHelper.isCanonicalMovieId(movieRecord.imdbId)) {
                    continue
                }
                var imdbId = movieRecord.imdbId.takeIf(String::isNotBlank)
                if (imdbId.isNullOrBlank() && movieRecord.movieId > 0) {
                    imdbId = favoriteDao?.getFavoriteMovies(uid)?.firstOrNull { it.movieId == movieRecord.movieId }?.imdbId?.takeIf(String::isNotBlank)
                        ?: watchHistoryDao.get(uid, "tmdb_${movieRecord.movieId}")?.mediaId?.takeIf { it.startsWith("tt") }
                }
                if (imdbId.isNullOrBlank() && resolver != null && movieRecord.movieTitle.isNotBlank()) {
                    val resolved = resolver.resolve(movieRecord.movieTitle, existingMovieId = movieRecord.movieId)
                    if (!resolved.imdbId.isNullOrBlank()) {
                        imdbId = resolved.imdbId
                    }
                }
                val canonicalDocId = MediaIdHelper.forMovie(imdbId, movieRecord.movieId, movieRecord.movieTitle)
                val effectiveImdb = MediaIdHelper.normalizeImdbId(imdbId).orEmpty()
                val needsUpdate = movieRecord.imdbId != effectiveImdb

                if (needsUpdate || (remoteUid != null && movieRecord.hash != canonicalDocId)) {
                    val updatedEntity = movieRecord.copy(imdbId = effectiveImdb)
                    downloadRecordDao?.upsertMovie(updatedEntity)
                    if (remoteUid != null) {
                        val dlModel = updatedEntity.toDownloadedMovie()
                        firestore.collection("users")
                            .document(remoteUid)
                            .collection("downloads")
                            .document(canonicalDocId)
                            .set(dlModel)
                            .await()

                        // Delete legacy doc if stored under raw infohash, numeric movieId, or tmdb_ prefix
                        if (movieRecord.hash.isNotBlank() && movieRecord.hash != canonicalDocId) {
                            try {
                                firestore.collection("users")
                                    .document(remoteUid)
                                    .collection("downloads")
                                    .document(movieRecord.hash)
                                    .delete()
                                    .await()
                            } catch (_: Exception) {}
                        }
                        val tmdbKey = "tmdb_${movieRecord.movieId}"
                        if (tmdbKey != canonicalDocId) {
                            try {
                                firestore.collection("users")
                                    .document(remoteUid)
                                    .collection("downloads")
                                    .document(tmdbKey)
                                    .delete()
                                    .await()
                            } catch (_: Exception) {}
                        }
                        if (movieRecord.movieId > 0 && movieRecord.movieId.toString() != canonicalDocId) {
                            try {
                                firestore.collection("users")
                                    .document(remoteUid)
                                    .collection("downloads")
                                    .document(movieRecord.movieId.toString())
                                    .delete()
                                    .await()
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("UserLibraryRepository", "Error during movie downloads migration: ${e.message}")
        }

        // 2. Migrate TV Episode Downloads
        try {
            val localEpisodes = downloadRecordDao?.getDownloadedEpisodes(uid).orEmpty()
            for (epRecord in localEpisodes) {
                if (MediaIdHelper.normalizeImdbId(epRecord.imdbId) != null) {
                    continue
                }
                var imdbId = epRecord.imdbId.takeIf(String::isNotBlank)
                if (imdbId.isNullOrBlank() && epRecord.seriesId > 0) {
                    imdbId = favoriteDao?.getFavoriteTvSeries(uid)?.firstOrNull { it.seriesId == epRecord.seriesId }?.imdbId?.takeIf(String::isNotBlank)
                        ?: torrentDownloadDao?.getAll()?.firstOrNull { it.seriesId == epRecord.seriesId }?.imdbId?.takeIf(String::isNotBlank)
                }
                if (imdbId.isNullOrBlank() && resolver != null && epRecord.seriesName.isNotBlank()) {
                    val resolved = resolver.resolve(epRecord.seriesName, existingSeriesId = epRecord.seriesId, existingSeasonNumber = epRecord.seasonNumber, existingEpisodeNumber = epRecord.episodeNumber)
                    if (!resolved.imdbId.isNullOrBlank()) {
                        imdbId = resolved.imdbId
                    }
                }
                val canonicalDocId = MediaIdHelper.forEpisode(
                    imdbId = imdbId,
                    seriesId = epRecord.seriesId,
                    seasonNumber = epRecord.seasonNumber,
                    episodeNumber = epRecord.episodeNumber,
                    title = epRecord.seriesName
                )
                val effectiveImdb = MediaIdHelper.normalizeImdbId(imdbId).orEmpty()
                val legacyTvKey = "tv_${epRecord.seriesId}_s${epRecord.seasonNumber}_e${epRecord.episodeNumber}"
                val tmdbKey = "tmdb_${epRecord.seriesId}_s${epRecord.seasonNumber}_e${epRecord.episodeNumber}"
                val needsUpdate = epRecord.imdbId != effectiveImdb

                if (needsUpdate || (remoteUid != null && epRecord.hash != canonicalDocId)) {
                    val updatedEntity = epRecord.copy(imdbId = effectiveImdb)
                    downloadRecordDao?.upsertEpisode(updatedEntity)
                    if (remoteUid != null) {
                        val epModel = updatedEntity.toDownloadedEpisode()
                        firestore.collection("users")
                            .document(remoteUid)
                            .collection("tv_downloads")
                            .document(canonicalDocId)
                            .set(epModel)
                            .await()

                        // Delete legacy doc keys
                        if (legacyTvKey != canonicalDocId) {
                            try {
                                firestore.collection("users")
                                    .document(remoteUid)
                                    .collection("tv_downloads")
                                    .document(legacyTvKey)
                                    .delete()
                                    .await()
                            } catch (_: Exception) {}
                        }
                        if (tmdbKey != canonicalDocId) {
                            try {
                                firestore.collection("users")
                                    .document(remoteUid)
                                    .collection("tv_downloads")
                                    .document(tmdbKey)
                                    .delete()
                                    .await()
                            } catch (_: Exception) {}
                        }
                        if (epRecord.hash.isNotBlank() && epRecord.hash != canonicalDocId && epRecord.hash != legacyTvKey) {
                            try {
                                firestore.collection("users")
                                    .document(remoteUid)
                                    .collection("tv_downloads")
                                    .document(epRecord.hash)
                                    .delete()
                                    .await()
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("UserLibraryRepository", "Error during TV episode downloads migration: ${e.message}")
        }
    }

    /**
     * Scans Room and Firestore for any legacy or non-canonical favorite items (movies and TV series),
     * resolves their canonical IMDb identifiers, updates Room, and stores them under canonical IMDb document IDs
     * in Firestore while deleting legacy numeric/tv_ keys.
     */
    suspend fun cleanupAndMigrateFavorites() {
        val uid = currentUserId
        val remoteUid = userId
        val resolver = mediaMetadataResolver?.get() ?: return

        // 1. Migrate Remote Firestore Favorites
        if (remoteUid != null) {
            try {
                val favSnapshot = firestore.collection("users")
                    .document(remoteUid)
                    .collection("favorites")
                    .get()
                    .await()

                for (doc in favSnapshot.documents) {
                    val isTv = isTvDocument(doc)
                    val isCanonical = MediaIdHelper.isCanonicalMovieId(doc.id)

                    if (isTv) {
                        if (isCanonical) {
                            val seriesId = (doc.get("id") as? Number)?.toInt() ?: 0
                            val rawImdb = doc.getString("imdbId") ?: doc.getString("imdb_id")
                            val normImdb = MediaIdHelper.normalizeImdbId(rawImdb) ?: doc.id
                            val name = doc.getString("name").orEmpty()
                            favoriteDao?.upsertTvSeries(
                                FavoriteTvSeriesEntity(
                                    userId = uid,
                                    seriesId = seriesId,
                                    imdbId = normImdb,
                                    name = name,
                                    posterPath = doc.getString("posterPath"),
                                    firstAirDate = doc.getString("firstAirDate"),
                                    voteAverage = (doc.get("voteAverage") as? Number)?.toDouble(),
                                    genreIds = (doc.get("genreIds") as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toInt() }.joinToString(","),
                                    numberOfSeasons = (doc.get("numberOfSeasons") as? Number)?.toInt(),
                                    numberOfEpisodes = (doc.get("numberOfEpisodes") as? Number)?.toInt(),
                                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                                )
                            )
                            continue
                        }
                        val seriesId = (doc.get("id") as? Number)?.toInt() ?: 0
                        val rawImdb = doc.getString("imdbId") ?: doc.getString("imdb_id")
                        var normImdb = MediaIdHelper.normalizeImdbId(rawImdb)
                        val name = doc.getString("name").orEmpty()

                        if (normImdb.isNullOrBlank() && seriesId > 0) {
                            try {
                                normImdb = resolver.getTvImdbId(seriesId)?.let { MediaIdHelper.normalizeImdbId(it) }
                            } catch (_: Exception) {}
                        }
                        if (normImdb.isNullOrBlank() && name.isNotBlank()) {
                            try {
                                normImdb = resolver.resolve(name, existingSeriesId = seriesId).imdbId?.let { MediaIdHelper.normalizeImdbId(it) }
                            } catch (_: Exception) {}
                        }

                        if (!normImdb.isNullOrBlank()) {
                            val data = mapOf(
                                "id" to seriesId,
                                "imdbId" to normImdb,
                                "name" to name,
                                "posterPath" to doc.getString("posterPath"),
                                "firstAirDate" to doc.getString("firstAirDate"),
                                "voteAverage" to (doc.get("voteAverage") as? Number)?.toDouble(),
                                "genreIds" to (doc.get("genreIds") as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toInt() },
                                "numberOfSeasons" to (doc.get("numberOfSeasons") as? Number)?.toInt(),
                                "numberOfEpisodes" to (doc.get("numberOfEpisodes") as? Number)?.toInt(),
                                "updatedAt" to (doc.getLong("updatedAt") ?: System.currentTimeMillis())
                            )
                            firestore.collection("users")
                                .document(remoteUid)
                                .collection("favorites")
                                .document(normImdb)
                                .set(data)
                                .await()

                            favoriteDao?.upsertTvSeries(
                                FavoriteTvSeriesEntity(
                                    userId = uid,
                                    seriesId = seriesId,
                                    imdbId = normImdb,
                                    name = name,
                                    posterPath = doc.getString("posterPath"),
                                    firstAirDate = doc.getString("firstAirDate"),
                                    voteAverage = (doc.get("voteAverage") as? Number)?.toDouble(),
                                    genreIds = (doc.get("genreIds") as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toInt() }.joinToString(","),
                                    numberOfSeasons = (doc.get("numberOfSeasons") as? Number)?.toInt(),
                                    numberOfEpisodes = (doc.get("numberOfEpisodes") as? Number)?.toInt(),
                                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                                )
                            )

                            if (doc.id != normImdb) {
                                try { doc.reference.delete().await() } catch (_: Exception) {}
                            }
                            if (seriesId > 0 && "tv_$seriesId" != normImdb) {
                                try {
                                    firestore.collection("users")
                                        .document(remoteUid)
                                        .collection("favorites")
                                        .document("tv_$seriesId")
                                        .delete()
                                        .await()
                                } catch (_: Exception) {}
                            }
                        }
                    } else {
                        // Movie favorite document
                        if (isCanonical) {
                            val movie = doc.toObject(Movie::class.java)
                            if (movie != null) {
                                val normImdb = MediaIdHelper.normalizeImdbId(movie.imdbCode) ?: doc.id
                                val updatedMovie = movie.copy(imdbCode = normImdb)
                                favoriteDao?.upsertMovie(FavoriteMovieEntity.from(uid, updatedMovie))
                            }
                            continue
                        }
                        val movie = doc.toObject(Movie::class.java)
                        val movieId = movie?.id ?: (doc.get("id") as? Number)?.toInt() ?: doc.id.toIntOrNull() ?: 0
                        val rawImdb = movie?.imdbCode ?: doc.getString("imdbCode") ?: doc.getString("imdb_code")
                        var normImdb = MediaIdHelper.normalizeImdbId(rawImdb)
                        val title = movie?.title ?: doc.getString("title").orEmpty()

                        if (normImdb.isNullOrBlank() && movieId > 0) {
                            try {
                                normImdb = resolver.getMovieImdbId(movieId)?.let { MediaIdHelper.normalizeImdbId(it) }
                            } catch (_: Exception) {}
                        }
                        if (normImdb.isNullOrBlank() && title.isNotBlank()) {
                            try {
                                normImdb = resolver.resolve(title, existingMovieId = movieId).imdbId?.let { MediaIdHelper.normalizeImdbId(it) }
                            } catch (_: Exception) {}
                        }

                        if (!normImdb.isNullOrBlank()) {
                            val updatedMovie = (movie ?: Movie(id = movieId, title = title)).copy(imdbCode = normImdb)
                            firestore.collection("users")
                                .document(remoteUid)
                                .collection("favorites")
                                .document(normImdb)
                                .set(updatedMovie)
                                .await()

                            favoriteDao?.upsertMovie(FavoriteMovieEntity.from(uid, updatedMovie))

                            if (doc.id != normImdb) {
                                try { doc.reference.delete().await() } catch (_: Exception) {}
                            }
                            if (movieId > 0 && movieId.toString() != normImdb) {
                                try {
                                    firestore.collection("users")
                                        .document(remoteUid)
                                        .collection("favorites")
                                        .document(movieId.toString())
                                        .delete()
                                        .await()
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.w("UserLibraryRepository", "Error during remote favorites migration: ${e.message}")
            }
        }

        // 2. Scan and backfill local Room favorites lacking canonical IMDb IDs
        try {
            val localMovies = favoriteDao?.getFavoriteMovies(uid).orEmpty()
            for (movieEntity in localMovies) {
                if (MediaIdHelper.isCanonicalMovieId(movieEntity.imdbId)) {
                    continue
                }
                if (movieEntity.imdbId.isBlank() && movieEntity.movieId > 0) {
                    val resolved = try {
                        resolver.getMovieImdbId(movieEntity.movieId)?.let { MediaIdHelper.normalizeImdbId(it) }
                    } catch (_: Exception) { null }
                    if (!resolved.isNullOrBlank()) {
                        val updated = movieEntity.copy(imdbId = resolved)
                        favoriteDao?.upsertMovie(updated)
                        if (remoteUid != null) {
                            firestore.collection("users")
                                .document(remoteUid)
                                .collection("favorites")
                                .document(resolved)
                                .set(updated.toMovie())
                                .await()
                            if (movieEntity.movieId.toString() != resolved) {
                                try {
                                    firestore.collection("users")
                                        .document(remoteUid)
                                        .collection("favorites")
                                        .document(movieEntity.movieId.toString())
                                        .delete()
                                        .await()
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }

            val localTv = favoriteDao?.getFavoriteTvSeries(uid).orEmpty()
            for (tvEntity in localTv) {
                if (MediaIdHelper.isCanonicalMovieId(tvEntity.imdbId)) {
                    continue
                }
                if (tvEntity.imdbId.isBlank() && tvEntity.seriesId > 0) {
                    val resolved = try {
                        resolver.getTvImdbId(tvEntity.seriesId)?.let { MediaIdHelper.normalizeImdbId(it) }
                    } catch (_: Exception) { null }
                    if (!resolved.isNullOrBlank()) {
                        val updated = tvEntity.copy(imdbId = resolved)
                        favoriteDao?.upsertTvSeries(updated)
                        if (remoteUid != null) {
                            val data = mapOf(
                                "id" to tvEntity.seriesId,
                                "imdbId" to resolved,
                                "name" to tvEntity.name,
                                "posterPath" to tvEntity.posterPath,
                                "firstAirDate" to tvEntity.firstAirDate,
                                "voteAverage" to tvEntity.voteAverage,
                                "genreIds" to tvEntity.genreIds.split(",").mapNotNull { it.trim().toIntOrNull() },
                                "numberOfSeasons" to tvEntity.numberOfSeasons,
                                "numberOfEpisodes" to tvEntity.numberOfEpisodes,
                                "updatedAt" to tvEntity.updatedAt
                            )
                            firestore.collection("users")
                                .document(remoteUid)
                                .collection("favorites")
                                .document(resolved)
                                .set(data)
                                .await()
                            val legacyKey = "tv_${tvEntity.seriesId}"
                            if (legacyKey != resolved) {
                                try {
                                    firestore.collection("users")
                                        .document(remoteUid)
                                        .collection("favorites")
                                        .document(legacyKey)
                                        .delete()
                                        .await()
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("UserLibraryRepository", "Error during local favorites migration: ${e.message}")
        }
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
        val effectiveImdb = MediaIdHelper.normalizeImdbId(imdbId)
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
        val effectiveImdb = MediaIdHelper.normalizeImdbId(imdbId).orEmpty()
        if (movieId <= 0 && effectiveImdb.isBlank()) return
        val uid = currentUserId
        try {
            if (movieId > 0) {
                downloadRecordDao?.deleteMoviesByMovieId(uid, movieId)
            }
            if (effectiveImdb.isNotBlank()) {
                downloadRecordDao?.deleteMoviesByImdbId(uid, effectiveImdb)
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
            if (movieId > 0) {
                firestore.collection("users")
                    .document(remoteUid)
                    .collection("downloads")
                    .document(movieId.toString())
                    .delete()
                    .await()
            }

            if (effectiveImdb.isNotBlank()) {
                firestore.collection("users")
                    .document(remoteUid)
                    .collection("downloads")
                    .document(effectiveImdb)
                    .delete()
                    .await()
            }

            if (movieId > 0) {
                val snapshot = firestore.collection("users")
                    .document(remoteUid)
                    .collection("downloads")
                    .whereEqualTo("movieId", movieId)
                    .get()
                    .await()
                for (doc in snapshot.documents) {
                    doc.reference.delete().await()
                }
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
            "tv_${seriesId}_s${seasonNumber}_e${episodeNumber}"
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
        val effectiveImdb = MediaIdHelper.normalizeImdbId(imdbId)
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
        val effectiveImdb = MediaIdHelper.normalizeImdbId(imdbId).orEmpty()
        if (seriesId <= 0 && effectiveImdb.isBlank() && (seasonNumber < 0 || episodeNumber <= 0)) return
        val uid = currentUserId
        val canonicalDocId = MediaIdHelper.forEpisode(effectiveImdb, seriesId, seasonNumber, episodeNumber)
        try {
            downloadRecordDao?.deleteEpisodeByHash(uid, canonicalDocId)
            if (seriesId > 0 && seasonNumber >= 0 && episodeNumber > 0) {
                downloadRecordDao?.deleteEpisode(uid, seriesId, seasonNumber, episodeNumber)
            }
            if (effectiveImdb.isNotBlank() && seasonNumber >= 0 && episodeNumber > 0) {
                downloadRecordDao?.deleteEpisodeByAnyId(uid, seriesId, effectiveImdb, seasonNumber, episodeNumber)
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

            val snapshot = firestore.collection("users")
                .document(remoteUid)
                .collection("tv_downloads")
                .whereEqualTo("seriesId", seriesId)
                .whereEqualTo("seasonNumber", seasonNumber)
                .whereEqualTo("episodeNumber", episodeNumber)
                .get()
                .await()
            for (doc in snapshot.documents) {
                doc.reference.delete().await()
            }
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
        for (episode in episodes) {
            markEpisodeAsUncompleted(
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                episodeNumber = episode.episodeNumber,
                imdbId = imdbId
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

            // Migration to canonical IMDb identifiers is executed only once per user account
            val migrationTimestamp = metadataDao.getLastSyncTimestamp(uid, "canonical_migration_v1") ?: 0L
            val migrationCompleted = migrationTimestamp > 0L

            if (!migrationCompleted || forceFull) {
                cleanupAndMigrateWatchHistory()
                cleanupAndMigrateDownloads()
                cleanupAndMigrateFavorites()
                metadataDao.setLastSyncTimestamp(
                    SyncMetadataEntity(
                        userId = uid,
                        collectionKey = "canonical_migration_v1",
                        lastSyncTimestamp = now
                    )
                )
                Log.i("UserLibraryRepository", "Completed one-time canonical migration for $uid")
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
