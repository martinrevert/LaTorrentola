package com.martinrevert.latorrentola.network

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.user.DownloadedMovie
import com.martinrevert.latorrentola.utils.PreferenceManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores user favorites, downloads, and settings in their Firestore account.
 *
 * @property firestore Firestore client used for cloud library operations.
 * @property auth Firebase authentication client used to scope data by user.
 */
@Singleton
class UserLibraryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
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
                
                val favorites = snapshot?.documents?.mapNotNull { 
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
    suspend fun saveMinimumRating(rating: Int) {
        val uid = userId ?: throw IllegalStateException("User not logged in")
        val boundedRating = rating.coerceIn(
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
    fun observeRemoteMinimumRating(uid: String): Flow<Int> = callbackFlow {
        val subscription = firestore.collection("users")
            .document(uid)
            .collection("settings")
            .document("config")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreSync", "Error observing minimum rating: ${error.message}")
                    return@addSnapshotListener
                }
                val rating = snapshot?.getLong("minimumRating")?.toInt()
                    ?: PreferenceManager.DEFAULT_MINIMUM_RATING
                trySend(
                    rating.coerceIn(
                        PreferenceManager.MINIMUM_RATING_MIN,
                        PreferenceManager.MINIMUM_RATING_MAX
                    )
                )
            }

        awaitClose { subscription.remove() }
    }
}
