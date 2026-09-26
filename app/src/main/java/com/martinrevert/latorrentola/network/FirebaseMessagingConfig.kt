package com.martinrevert.latorrentola.network

/** Shared FCM topic, notification-channel, and intent-extra identifiers. */
object FirebaseMessagingConfig {
    /** Topic used for broadcast movie notifications. */
    const val TOPIC_ALL = "all"
    /** Identifier of the app's general notification channel. */
    const val DEFAULT_CHANNEL_ID = "latorrentola_general"
    /** User-visible name of the general notification channel. */
    const val DEFAULT_CHANNEL_NAME = "General"
    /** User-visible description of the general notification channel. */
    const val DEFAULT_CHANNEL_DESCRIPTION = "General app notifications"

    /** Intent key containing a serialized movie payload. */
    const val EXTRA_MOVIE_JSON = "PELI"
    /** Intent key containing a movie identifier. */
    const val EXTRA_MOVIE_ID = "MOVIE_ID"
}
