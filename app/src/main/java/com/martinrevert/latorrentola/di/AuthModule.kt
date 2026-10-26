package com.martinrevert.latorrentola.di

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides Firebase authentication and Firestore singleton bindings. */
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    /** Provides the default Firebase authentication instance. */
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    /** Provides the default Firebase Firestore instance configured with in-memory persistence. */
    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        val firestore = FirebaseFirestore.getInstance()
        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
            firestore.firestoreSettings = settings
        } catch (e: Exception) {
            Log.w("AuthModule", "Unable to apply memory cache settings to Firestore: ${e.message}")
        }
        return firestore
    }
}
