package com.martinrevert.latorrentola.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

    /** Provides the default Firebase Firestore instance. */
    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
}
