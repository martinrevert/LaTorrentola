package com.martinrevert.latorrentola.network

import com.martinrevert.latorrentola.database.DateDao
import com.martinrevert.latorrentola.model.date.DateLastVisit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides access to locally persisted last-visit metadata.
 *
 * @property dateDao DAO for reading and writing visit records.
 */
@Singleton
class DateRepository @Inject constructor(
    private val dateDao: DateDao
) {

    /** Returns the most recently inserted visit record, or `null` when none exists. */
    suspend fun getLastVisitDate(): DateLastVisit? {
        return dateDao.getDate().firstOrNull()
    }

    /** Persists [date] as the current visit record. */
    suspend fun updateLastVisitDate(date: DateLastVisit) {
        dateDao.setDate(date)
    }
}
