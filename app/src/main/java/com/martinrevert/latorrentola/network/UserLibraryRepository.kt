package com.martinrevert.latorrentola.network

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.model.user.FavoriteTvSeries
import com.martinrevert.latorrentola.model.user.DownloadedEpisode
import com.martinrevert.latorrentola.model.user.DownloadedMovie
import com.martinrevert.latorrentola.model.user.PlaybackProgress
import com.martinrevert.latorrentola.database.WatchHistoryDao
import com.martinrevert.latorrentola.database.WatchHistoryEntity
import com.martinrevert.latorrentola.utils.PreferenceManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores user favorites, downloads, and settings in their Firestore account.
 *
 * @property firestore Firestore client used for cloud library operations.
 * @property auth Firebase authentication client used to scope data by user.
 * @property watchHistoryDao Local database access for watch progress caching.
 */
@Singleton
class UserLibraryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val watchHistoryDao: WatchHistoryDao
) {
    /** Authenticated Firebase UID used to scope cloud library documents. */
    private val userId: String? get() = auth.currentUser?.uid

    /** Observes the signed-in user's downloads in reverse chronological order. */
    fun getDownloadedMovies(): Flow<List<DownloadedMovie>> = callbackFlow {
        val uid = userId
        if (uid == null) {
            trySend(emptyList())
            return@callbackFlow
        }

        val subscription = firestore.collection("users")
            .document(uid)
            .collection("downloads")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Firestore", "Error fetching downloads: ${error.message}")
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

    /** Saves or replaces a download record, keyed by its torrent hash. */
    suspend fun markAsDownloaded(download: DownloadedMovie) {
        val uid = userId ?: return
        try {
            firestore.collection("users")
                .document(uid)
                .collection("downloads")
                .document(download.hash)
                .set(download)
                .await()
        } catch (e: Exception) {
            Log.e("Firestore", "Error saving download: ${e.message}")
        }
    }

    /** Observes the signed-in user's TV episode downloads in reverse chronological order. */
    fun getDownloadedEpisodes(): Flow<List<DownloadedEpisode>> = callbackFlow {
        val uid = userId
        if (uid == null) {
            trySend(emptyList())
            return@callbackFlow
        }

        val subscription = firestore.collection("users")
            .document(uid)
            .collection("tv_downloads")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Firestore", "Error fetching TV downloads: ${error.message}")
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

    /** Saves or replaces a TV episode download record, keyed by its torrent hash or fallback document ID. */
    suspend fun markEpisodeAsDownloaded(download: DownloadedEpisode) {
        val uid = userId ?: return
        val docId = download.hash.ifBlank {
            "tv_${download.seriesId}_s${download.seasonNumber}_e${download.episodeNumber}"
        }
        val sanitizedDownload = download.copy(hash = docId)
        try {
            firestore.collection("users")
                .document(uid)
                .collection("tv_downloads")
                .document(docId)
                .set(sanitizedDownload)
                .await()
        } catch (e: Exception) {
            Log.e("Firestore", "Error saving TV episode download: ${e.message}")
        }
    }

    /** Observes the signed-in user's favorite movie records. */
    fun getFavoriteMovies(): Flow<List<Movie>> = callbackFlow {
        val uid = userId
        if (uid == null) {
            trySend(emptyList())
            return@callbackFlow
        }

        val subscription = firestore.collection("users")
            .document(uid)
            .collection("favorites")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Firestore", "Error fetching favorites: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val favorites = snapshot?.documents?.filterNot {
                    it.id.startsWith("tv_")
                }?.mapNotNull {
                    it.toObject(Movie::class.java) 
                } ?: emptyList()
                
                trySend(favorites)
            }

        awaitClose { subscription.remove() }
    }

    /** Saves or replaces [movie] in the signed-in user's favorites. */
    suspend fun addFavorite(movie: Movie) {
        val uid = userId ?: return
        try {
            firestore.collection("users")
                .document(uid)
                .collection("favorites")
                .document(movie.id.toString())
                .set(movie)
                .await()
        } catch (e: Exception) {
            Log.e("Firestore", "Error adding favorite: ${e.message}")
        }
    }

    /** Removes [movie] from the signed-in user's favorites. */
    suspend fun removeFavorite(movie: Movie) {
        val uid = userId ?: return
        try {
            firestore.collection("users")
                .document(uid)
                .collection("favorites")
                .document(movie.id.toString())
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e("Firestore", "Error removing favorite: ${e.message}")
        }
    }

    /** Observes the signed-in user's favorite TV series. */
    fun getFavoriteTvSeries(): Flow<List<TmdbTvSummary>> = callbackFlow {
        val uid = userId
        if (uid == null) {
            trySend(emptyList())
            return@callbackFlow
        }

        val subscription = firestore.collection("users")
            .document(uid)
            .collection("favorites")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Firestore", "Error fetching favorite TV series: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val favorites = snapshot?.documents
                    ?.filter { it.id.startsWith("tv_") }
                    ?.mapNotNull { document ->
                        val id = (document.get("id") as? Number)?.toInt() ?: return@mapNotNull null
                        FavoriteTvSeries(
                            id = id,
                            name = document.getString("name"),
                            posterPath = document.getString("posterPath"),
                            firstAirDate = document.getString("firstAirDate"),
                            voteAverage = (document.get("voteAverage") as? Number)?.toDouble(),
                            genreIds = (document.get("genreIds") as? List<*>)
                                .orEmpty()
                                .mapNotNull { (it as? Number)?.toInt() }
                        ).toTmdbTvSummary()
                    } ?: emptyList()
                trySend(favorites)
            }

        awaitClose { subscription.remove() }
    }

    /** Saves or replaces [series] in the signed-in user's TV favorites. */
    suspend fun addFavoriteTvSeries(series: TmdbTvSummary) {
        val uid = userId ?: throw IllegalStateException("User not logged in")
        val favorite = FavoriteTvSeries.from(series)
        firestore.collection("users")
            .document(uid)
            .collection("favorites")
            .document("tv_${series.id}")
            .set(
                mapOf(
                    "id" to favorite.id,
                    "name" to favorite.name,
                    "posterPath" to favorite.posterPath,
                    "firstAirDate" to favorite.firstAirDate,
                    "voteAverage" to favorite.voteAverage,
                    "genreIds" to favorite.genreIds.orEmpty()
                )
            )
            .await()
    }

    /** Removes [series] from the signed-in user's TV favorites. */
    suspend fun removeFavoriteTvSeries(series: TmdbTvSummary) {
        val uid = userId ?: throw IllegalStateException("User not logged in")
        firestore.collection("users")
            .document(uid)
            .collection("favorites")
            .document("tv_${series.id}")
            .delete()
            .await()
    }

    /** Checks whether the signed-in user's library contains [movieId]. */
    suspend fun isFavorite(movieId: Int): Boolean {
        val uid = userId ?: return false
        return try {
            firestore.collection("users")
                .document(uid)
                .collection("favorites")
                .document(movieId.toString())
                .get()
                .await()
                .exists()
        } catch (e: Exception) {
            false
        }
    }

    /** Saves the user's serialized language filter setting to Firestore. */
    suspend fun saveFilteredLanguages(languages: String) {
        val uid = userId ?: throw IllegalStateException("User not logged in")
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
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("FirestoreSync", "Error saving settings: ${e.message}")
            throw e
        }
    }

    /** Saves a bounded minimum rating preference to Firestore. */
    suspend fun saveMinimumRating(rating: Float) {
        val uid = userId ?: throw IllegalStateException("User not logged in")
        val boundedRating = (Math.round(rating * 10.0f) / 10.0f).coerceIn(
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
            if (e is kotlinx.coroutines.CancellationException) throw e
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
            Log.e("FirestoreSync", "Error fetching settings: ${e.message}")
            null
        }
    }

    /** Observes remote language-filter changes for the specified user. */
    fun observeRemoteFilteredLanguages(uid: String): Flow<String?> = callbackFlow {
        Log.d("FirestoreSync", "Observing remote settings for $uid")
        val subscription = firestore.collection("users")
            .document(uid)
            .collection("settings")
            .document("config")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreSync", "Error observing settings: ${error.message}")
                    return@addSnapshotListener
                }
                val lang = snapshot?.getString("filteredLanguages")
                Log.d("FirestoreSync", "Remote settings received: $lang")
                trySend(lang)
            }

        awaitClose { 
            Log.d("FirestoreSync", "Stopping remote settings observation for $uid")
            subscription.remove() 
        }
    }

    /** Observes the user's remote minimum rating, applying configured bounds. */
    fun observeRemoteMinimumRating(uid: String): Flow<Float> = callbackFlow {
        val subscription = firestore.collection("users")
            .document(uid)
            .collection("settings")
            .document("config")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreSync", "Error observing minimum rating: ${error.message}")
                    return@addSnapshotListener
                }
                val rawRating = snapshot?.getDouble("minimumRating")?.toFloat()
                    ?: snapshot?.getLong("minimumRating")?.toFloat()
                    ?: PreferenceManager.DEFAULT_MINIMUM_RATING
                val boundedRating = (Math.round(rawRating * 10.0f) / 10.0f).coerceIn(
                    PreferenceManager.MINIMUM_RATING_MIN,
                    PreferenceManager.MINIMUM_RATING_MAX
                )
                trySend(boundedRating)
            }

        awaitClose { subscription.remove() }
    }

    /** Saves or updates watch history playback progress locally in Room and in Firestore when signed in. */
    suspend fun savePlaybackProgress(progress: PlaybackProgress) {
        try {
            watchHistoryDao.upsert(
                WatchHistoryEntity(
                    mediaId = progress.mediaId,
                    title = progress.title,
                    positionMs = progress.positionMs,
                    durationMs = progress.durationMs,
                    timestamp = progress.timestamp,
                    isEpisode = progress.isEpisode
                )
            )
        } catch (e: Exception) {
            Log.e("UserLibraryRepository", "Error saving watch history locally: ${e.message}")
        }

        val uid = userId ?: return
        try {
            firestore.collection("users")
                .document(uid)
                .collection("watch_history")
                .document(progress.mediaId)
                .set(progress, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("Firestore", "Error saving playback progress: ${e.message}")
        }
    }

    /** Retrieves saved playback progress locally from Room DB first, falling back to Firestore when signed in. */
    suspend fun getPlaybackProgress(mediaId: String): PlaybackProgress? {
        if (mediaId.isBlank()) return null
        try {
            val localEntity = watchHistoryDao.get(mediaId)
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
        } catch (e: Exception) {
            Log.e("UserLibraryRepository", "Error fetching local watch history: ${e.message}")
        }

        val uid = userId ?: return null
        return try {
            val doc = firestore.collection("users")
                .document(uid)
                .collection("watch_history")
                .document(mediaId)
                .get()
                .await()
            doc.toObject(PlaybackProgress::class.java)
        } catch (e: Exception) {
            Log.e("UserLibraryRepository", "Error fetching remote watch history: ${e.message}")
            null
        }
    }

    /** Observes the user's watch history from local Room cache, combining with Firestore when signed in. */
    fun getWatchHistory(): Flow<List<PlaybackProgress>> {
        return watchHistoryDao.observeAll()
            .map { entities ->
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
}
