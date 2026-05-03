package com.martinrevert.latorrentola.network

import com.martinrevert.latorrentola.database.DateDao
import com.martinrevert.latorrentola.model.date.DateLastVisit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DateRepository @Inject constructor(
    private val dateDao: DateDao
) {

    suspend fun getLastVisitDate(): DateLastVisit? {
        return dateDao.getDate().firstOrNull()
    }

    suspend fun updateLastVisitDate(date: DateLastVisit) {
        dateDao.setDate(date)
    }
}
