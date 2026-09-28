package com.martinrevert.latorrentola.network

/** TMDB TV catalog feeds available from the Home TV mode. */
enum class TmdbTvFeed {
    /** Series airing during the next seven days. */
    ON_THE_AIR,
    /** Series airing today. */
    AIRING_TODAY,
    /** Series ordered by TMDB popularity. */
    POPULAR,
    /** Series ordered by TMDB rating. */
    TOP_RATED
}
