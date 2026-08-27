package com.martinrevert.latorrentola.network

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.martinrevert.latorrentola.model.user.DownloadedMovie
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserLibraryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val userId: String? get() = auth.currentUser?.uid

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
                    trySend(emptyList()) // Provide empty list instead of closing with error
                    return@addSnapshotListener
                }
                
                val downloads = snapshot?.documents?.mapNotNull { 
                    it.toObject(DownloadedMovie::class.java) 
                } ?: emptyList()
                
                trySend(downloads)
            }

        awaitClose { subscription.remove() }
    }

    suspend fun markAsDownloaded(download: DownloadedMovie) {
        val uid = userId ?: return
        
        try {
            // Use hash as document ID to avoid duplicates
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
}
