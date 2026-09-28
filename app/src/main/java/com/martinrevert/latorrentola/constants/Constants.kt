package com.martinrevert.latorrentola.constants

import com.martinrevert.latorrentola.BuildConfig

/** Shared endpoints and configuration values used by the app's service clients. */
object Constants {
    /** Base URL for the YTS movie API. */
    const val YTS_BASE_URL = "https://movies-api.accel.li/api/v2/"
    /** Base URL for the app's Firebase Cloud Messaging backend. */
    const val FCM_BASE_URL = "https://fcm.martinrevert.com.ar/"
    /** Base URL for The Movie Database API. */
    const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"
    /** Base URL for the EZTV television torrent API. */
    const val EZTV_BASE_URL = "https://eztvx.to/api/"
    /** Default page size requested from paginated movie endpoints. */
    const val PAGE_SIZE = 50
    /** OAuth web client identifier supplied by the build configuration. */
    val WEB_CLIENT_ID = BuildConfig.WEB_CLIENT_ID
}
