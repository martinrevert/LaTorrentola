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
import com.martinrevert.latorrentola.database.TorrentDownloadDao
import com.martinrevert.latorrentola.utils.MediaIdHelper
import com.martinrevert.latorrentola.utils.PreferenceManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.martinrevert.latorrentola.utils.MediaMetadataResolver
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores user favorites, downloads, and settings in their Firestore account.
 *
 * @property firestore Firestore client used for cloud library operations.
 * @property auth Firebase authentication client used to scope data by user.
 * @property watchHistoryDao Local database access for watch progress caching.
 * @property torrentDownloadDao Local database access for torrent transfers cleanup.
 * @property mediaMetadataResolver Lazy resolver used to migrate non-canonical media IDs to canonical IDs.
 */
@Singleton
class UserLibraryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val watchHistoryDao: WatchHistoryDao,
    private val torrentDownloadDao: TorrentDownloadDao? = null,
    private val mediaMetadataResolver: dagger.Lazy<MediaMetadataResolver>? = null
) {
    /** Authenticated Firebase UID used to scope cloud library documents. */
    private val userId: String? get() = auth.currentUser?.uid

    /** Observes the signed-in user's downloads in reverse chronological order. */
    fun getDownloadedMovies(): Flow<List<DownloadedMovie>> {
        val uid = userId ?: return flowOf(emptyList())
        return callbackFlow {
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
            if (e is CancellationException) throw e
            Log.e("Firestore", "Error saving download: ${e.message}")
        }
    }

    /** Observes the signed-in user's TV episode downloads in reverse chronological order. */
    fun getDownloadedEpisodes(): Flow<List<DownloadedEpisode>> {
        val uid = userId ?: return flowOf(emptyList())
        return callbackFlow {
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
            if (e is CancellationException) throw e
            Log.e("Firestore", "Error saving TV episode download: ${e.message}")
        }
    }

    /** Observes the signed-in user's favorite movie records. */
    fun getFavoriteMovies(): Flow<List<Movie>> {
        val uid = userId ?: return flowOf(emptyList())
        return callbackFlow {
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
            if (e is CancellationException) throw e
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
            if (e is CancellationException) throw e
            Log.e("Firestore", "Error removing favorite: ${e.message}")
        }
    }

    /** Observes the signed-in user's favorite TV series. */
    fun getFavoriteTvSeries(): Flow<List<TmdbTvSummary>> {
        val uid = userId ?: return flowOf(emptyList())
        return callbackFlow {
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
                                    .mapNotNull { (it as? Number)?.toInt() },
                                numberOfSeasons = (document.get("numberOfSeasons") as? Number)?.toInt(),
                                numberOfEpisodes = (document.get("numberOfEpisodes") as? Number)?.toInt()
                            ).toTmdbTvSummary()
                        } ?: emptyList()
                    trySend(favorites)
                }

            awaitClose { subscription.remove() }
        }
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
                    "genreIds" to favorite.genreIds.orEmpty(),
                    "numberOfSeasons" to favorite.numberOfSeasons,
                    "numberOfEpisodes" to favorite.numberOfEpisodes
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
            if (e is CancellationException) throw e
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
            if (e is CancellationException) throw e
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
                val boundedRating = (kotlin.math.round(rawRating * 10.0f) / 10.0f).coerceIn(
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
            if (e is CancellationException) throw e
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
            if (e is CancellationException) throw e
            Log.e("Firestore", "Error saving playback progress: ${e.message}")
        }
    }

    private fun parsePlaybackProgress(doc: com.google.firebase.firestore.DocumentSnapshot): PlaybackProgress? {
        if (!doc.exists()) return null
        val parsed = doc.toObject(PlaybackProgress::class.java) ?: PlaybackProgress()
        val finalMediaId = parsed.mediaId.ifBlank {
            doc.getString("mediaId") ?: doc.getString("media_id") ?: doc.id
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
            if (parsed.timestamp > 0L) parsed.timestamp else (doc.getLong("timestamp") ?: System.currentTimeMillis())
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

    /** Retrieves saved playback progress locally from Room DB, falling back to or syncing with Firestore when signed in. */
    suspend fun getPlaybackProgress(mediaId: String): PlaybackProgress? {
        if (mediaId.isBlank()) return null
        val uid = userId
        var remoteProgress: PlaybackProgress? = null
        if (uid != null) {
            try {
                val doc = firestore.collection("users")
                    .document(uid)
                    .collection("watch_history")
                    .document(mediaId)
                    .get()
                    .await()
                remoteProgress = parsePlaybackProgress(doc)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("UserLibraryRepository", "Error fetching remote watch history: ${e.message}")
            }
        }

        var localEntity: WatchHistoryEntity? = null
        try {
            localEntity = watchHistoryDao.get(mediaId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("UserLibraryRepository", "Error fetching local watch history: ${e.message}")
        }

        if (remoteProgress != null && (localEntity == null || remoteProgress.timestamp > localEntity.timestamp)) {
            try {
                watchHistoryDao.upsert(
                    WatchHistoryEntity(
                        mediaId = remoteProgress.mediaId,
                        title = remoteProgress.title,
                        positionMs = remoteProgress.positionMs,
                        durationMs = remoteProgress.durationMs,
                        timestamp = remoteProgress.timestamp,
                        isEpisode = remoteProgress.isEpisode
                    )
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("UserLibraryRepository", "Error saving remote progress to Room: ${e.message}")
            }
            return remoteProgress
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

        // Fallback: If mediaId is canonical, attempt to look up and migrate matching legacy non-canonical records
        if (MediaIdHelper.isCanonical(mediaId)) {
            val legacyMatch = findAndMigrateLegacyProgress(mediaId)
            if (legacyMatch != null) return legacyMatch
        }

        return null
    }

    /**
     * Finds any legacy record in Room matching the requested canonical media ID,
     * migrates it to the canonical key, and deletes the old legacy record.
     */
    private suspend fun findAndMigrateLegacyProgress(canonicalMediaId: String): PlaybackProgress? {
        val resolver = mediaMetadataResolver?.get() ?: return null
        val uid = userId
        try {
            val allLocal = watchHistoryDao.getAll()
            val episodeTriple = MediaIdHelper.parseCanonicalEpisode(canonicalMediaId)

            for (legacy in allLocal) {
                if (MediaIdHelper.isCanonical(legacy.mediaId)) continue

                // Check episode match by season and episode numbers
                if (episodeTriple != null) {
                    val (sNum, eNum) = MediaMetadataResolver.extractSeasonAndEpisode(legacy.title, legacy.mediaId)
                    if (sNum == episodeTriple.second && eNum == episodeTriple.third) {
                        val resolved = resolver.resolve(rawTitle = legacy.title.ifBlank { legacy.mediaId })
                        if (resolved.mediaId == canonicalMediaId) {
                            val migrated = PlaybackProgress(
                                mediaId = canonicalMediaId,
                                title = resolved.title.ifBlank { legacy.title },
                                positionMs = legacy.positionMs,
                                durationMs = legacy.durationMs,
                                timestamp = legacy.timestamp,
                                isEpisode = true
                            )
                            savePlaybackProgress(migrated)
                            deletePlaybackProgress(legacy.mediaId)
                            Log.i("UserLibraryRepository", "Migrated on-demand legacy episode '${legacy.mediaId}' -> '$canonicalMediaId'")
                            return migrated
                        }
                    }
                } else if (MediaIdHelper.isCanonicalMovieId(canonicalMediaId)) {
                    // Check movie match by resolving legacy candidate
                    val resolved = resolver.resolve(rawTitle = legacy.title.ifBlank { legacy.mediaId })
                    if (resolved.mediaId == canonicalMediaId) {
                        val migrated = PlaybackProgress(
                            mediaId = canonicalMediaId,
                            title = resolved.title.ifBlank { legacy.title },
                            positionMs = legacy.positionMs,
                            durationMs = legacy.durationMs,
                            timestamp = legacy.timestamp,
                            isEpisode = false
                        )
                        savePlaybackProgress(migrated)
                        deletePlaybackProgress(legacy.mediaId)
                        Log.i("UserLibraryRepository", "Migrated on-demand legacy movie '${legacy.mediaId}' -> '$canonicalMediaId'")
                        return migrated
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("UserLibraryRepository", "Error resolving on-demand legacy progress: ${e.message}")
        }
        return null
    }

    /**
     * Scans Room and Firestore for any non-canonical watch history items
     * (e.g. 40-char torrent infoHashes, raw file names, or unformatted titles).
     * Resolves their canonical IDs via [MediaMetadataResolver], persists under the
     * canonical key, and deletes the old bad records from Room and Firestore.
     */
    suspend fun cleanupAndMigrateWatchHistory() {
        val resolver = mediaMetadataResolver?.get() ?: return
        val uid = userId

        // 1. Process local non-canonical items from Room
        try {
            val localItems = watchHistoryDao.getAll()
            for (item in localItems) {
                if (!MediaIdHelper.isCanonical(item.mediaId)) {
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
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("UserLibraryRepository", "Error during local watch history migration: ${e.message}")
        }

        // 2. Process remote non-canonical items from Firestore
        if (uid != null) {
            try {
                val snapshot = firestore.collection("users")
                    .document(uid)
                    .collection("watch_history")
                    .get()
                    .await()

                for (doc in snapshot.documents) {
                    val rawId = doc.id
                    if (!MediaIdHelper.isCanonical(rawId)) {
                        val progress = parsePlaybackProgress(doc)
                        if (progress != null) {
                            migrateSingleItem(progress, resolver, uid, rawDocId = rawId)
                        } else {
                            // Purge corrupt or empty document
                            doc.reference.delete().await()
                        }
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
        try {
            val titleToParse = item.title.ifBlank { oldId }
            val resolved = resolver.resolve(rawTitle = titleToParse)
            if (MediaIdHelper.isCanonical(resolved.mediaId) && resolved.mediaId != oldId) {
                val migratedProgress = item.copy(
                    mediaId = resolved.mediaId,
                    title = if (resolved.title.isNotBlank()) resolved.title else item.title,
                    isEpisode = resolved.isEpisode
                )
                savePlaybackProgress(migratedProgress)
                deletePlaybackProgress(oldId)
                Log.i("UserLibraryRepository", "Successfully migrated legacy watch history '$oldId' -> '${resolved.mediaId}'")
            } else if (MediaIdHelper.isInfoHash(oldId)) {
                // Purge orphan unresolvable 40-character torrent infoHash
                deletePlaybackProgress(oldId)
                Log.i("UserLibraryRepository", "Purged unresolvable infoHash document '$oldId' from watch history")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("UserLibraryRepository", "Failed migrating item '$oldId': ${e.message}")
        }
    }

    /** Observes the user's watch history from local Room cache, syncing with Firestore when signed in. */
    fun getWatchHistory(): Flow<List<PlaybackProgress>> = callbackFlow {
        val uid = userId
        var firestoreListener: com.google.firebase.firestore.ListenerRegistration? = null

        if (uid != null) {
            firestoreListener = firestore.collection("users")
                .document(uid)
                .collection("watch_history")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("UserLibraryRepository", "Error observing remote watch history: ${error.message}")
                        return@addSnapshotListener
                    }
                    val remoteItems = snapshot?.documents?.mapNotNull { doc ->
                        parsePlaybackProgress(doc)
                    } ?: emptyList()

                    if (remoteItems.isNotEmpty()) {
                        launch(Dispatchers.IO) {
                            for ((mediaId, title, positionMs, durationMs, timestamp, isEpisode) in remoteItems) {
                                try {
                                    val local = watchHistoryDao.get(mediaId)
                                    if (local == null || timestamp > local.timestamp) {
                                        watchHistoryDao.upsert(
                                            WatchHistoryEntity(
                                                mediaId = mediaId,
                                                title = title,
                                                positionMs = positionMs,
                                                durationMs = durationMs,
                                                timestamp = timestamp,
                                                isEpisode = isEpisode
                                            )
                                        )
                                    }
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Log.e("UserLibraryRepository", "Error syncing item: ${e.message}")
                                }
                            }
                            if (remoteItems.any { !MediaIdHelper.isCanonical(it.mediaId) }) {
                                cleanupAndMigrateWatchHistory()
                            }
                        }
                    }
                }
        }

        val job = launch(Dispatchers.IO) {
            watchHistoryDao.observeAll().collect { entities ->
                val list = entities.map {
                    PlaybackProgress(
                        mediaId = it.mediaId,
                        title = it.title,
                        positionMs = it.positionMs,
                        durationMs = it.durationMs,
                        timestamp = it.timestamp,
                        isEpisode = it.isEpisode
                    )
                }.sortedByDescending { it.timestamp }
                send(list)
            }
        }

        awaitClose {
            firestoreListener?.remove()
            job.cancel()
        }
    }

    /** Deletes watch history playback progress locally from Room and from Firestore. */
    suspend fun deletePlaybackProgress(mediaId: String) {
        if (mediaId.isBlank()) return
        try {
            watchHistoryDao.delete(mediaId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("UserLibraryRepository", "Error deleting watch history locally: ${e.message}")
        }

        val uid = userId ?: return
        try {
            firestore.collection("users")
                .document(uid)
                .collection("watch_history")
                .document(mediaId)
                .delete()
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("Firestore", "Error deleting playback progress remotely: ${e.message}")
        }
    }

    /** Marks a movie as 100% completed and downloaded in both Room and Firestore. */
    suspend fun markMovieAsCompleted(movie: Movie) {
        val title = movie.title.orEmpty()
        val mediaId = MediaIdHelper.forMovie(movie.id, title = title)
        val progress = PlaybackProgress(
            mediaId = mediaId,
            title = title,
            positionMs = 1000L,
            durationMs = 1000L,
            timestamp = System.currentTimeMillis(),
            isEpisode = false
        )
        savePlaybackProgress(progress)

        val downloadedMovie = DownloadedMovie(
            movieId = movie.id,
            movieTitle = title,
            quality = movie.torrents?.firstOrNull()?.quality ?: "HD",
            hash = movie.id.toString(),
            timestamp = System.currentTimeMillis(),
            movie = movie
        )
        markAsDownloaded(downloadedMovie)
    }

    /** Clears watch progress and downloads for a movie in both Room and Firestore. */
    suspend fun markMovieAsUncompleted(movie: Movie) {
        val title = movie.title.orEmpty()
        val mediaId = MediaIdHelper.forMovie(movie.id, title = title)
        deletePlaybackProgress(mediaId)
        removeMovieDownloads(movie.id)
    }

    /** Deletes download records for a specific movie in Firestore and Room. */
    suspend fun removeMovieDownloads(movieId: Int) {
        try {
            torrentDownloadDao?.deleteForMovie(movieId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("UserLibraryRepository", "Error deleting movie download local record: ${e.message}")
        }

        val uid = userId ?: return
        try {
            firestore.collection("users")
                .document(uid)
                .collection("downloads")
                .document(movieId.toString())
                .delete()
                .await()

            val snapshot = firestore.collection("users")
                .document(uid)
                .collection("downloads")
                .whereEqualTo("movieId", movieId)
                .get()
                .await()
            for (doc in snapshot.documents) {
                doc.reference.delete().await()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("Firestore", "Error removing remote movie download: ${e.message}")
        }
    }

    /** Marks a single TV episode as 100% completed and downloaded in both Room and Firestore. */
    suspend fun markEpisodeAsCompleted(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeTitle: String,
        seriesName: String,
        stillPath: String? = null
    ) {
        val mediaId = MediaIdHelper.forEpisode(seriesId, seasonNumber, episodeNumber)
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

        val docId = "tv_${seriesId}_s${seasonNumber}_e${episodeNumber}"
        val downloadedEpisode = DownloadedEpisode(
            seriesId = seriesId,
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
        episodeNumber: Int
    ) {
        val mediaId = MediaIdHelper.forEpisode(seriesId, seasonNumber, episodeNumber)
        deletePlaybackProgress(mediaId)
        removeEpisodeDownloads(seriesId, seasonNumber, episodeNumber)
    }

    /** Deletes download records for a specific episode in Firestore and Room. */
    suspend fun removeEpisodeDownloads(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int
    ) {
        try {
            torrentDownloadDao?.deleteForEpisode(seriesId, seasonNumber, episodeNumber)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("UserLibraryRepository", "Error deleting local episode download: ${e.message}")
        }

        val uid = userId ?: return
        try {
            val snapshot = firestore.collection("users")
                .document(uid)
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
            Log.e("Firestore", "Error removing remote episode download: ${e.message}")
        }
    }

    /** Marks all episodes in a season as completed and downloaded in both Room and Firestore. */
    suspend fun markSeasonAsCompleted(
        seriesId: Int,
        seasonNumber: Int,
        episodes: List<com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode>,
        seriesName: String
    ) {
        for (episode in episodes) {
            markEpisodeAsCompleted(
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                episodeNumber = episode.episodeNumber,
                episodeTitle = episode.name.orEmpty(),
                seriesName = seriesName,
                stillPath = episode.stillPath
            )
        }
    }

    /** Clears watch progress and download markers for all episodes in a season. */
    suspend fun markSeasonAsUncompleted(
        seriesId: Int,
        seasonNumber: Int,
        episodes: List<com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode>
    ) {
        for (episode in episodes) {
            markEpisodeAsUncompleted(
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                episodeNumber = episode.episodeNumber
            )
        }
    }
}
