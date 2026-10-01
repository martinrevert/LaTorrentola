package com.martinrevert.latorrentola.di

import com.martinrevert.latorrentola.network.TmdbRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Hilt EntryPoint used to access repositories outside injected ViewModels. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepositoryEntryPoint {
    /** Returns the singleton [TmdbRepository]. */
    fun tmdbRepository(): TmdbRepository
}
