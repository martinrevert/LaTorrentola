package com.martinrevert.latorrentola.network

import com.martinrevert.latorrentola.BuildConfig
import com.martinrevert.latorrentola.database.TvGenreDao
import com.martinrevert.latorrentola.database.TvProgressCacheDao
import com.martinrevert.latorrentola.database.TvProgressCacheEntity
import com.martinrevert.latorrentola.model.TMDB.TmdbActorDetail
import com.martinrevert.latorrentola.model.TMDB.TmdbMovieDetails
import com.martinrevert.latorrentola.model.TMDB.TmdbMoviePage
import com.martinrevert.latorrentola.model.TMDB.TmdbMovieResult
import com.martinrevert.latorrentola.model.TMDB.TmdbPersonResult
import com.martinrevert.latorrentola.model.TMDB.TmdbTvGenre
import com.martinrevert.latorrentola.model.TMDB.TmdbTvPage
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSeasonDetails
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.model.TMDB.TmdbWatchProvider
import com.martinrevert.latorrentola.model.TMDB.combineActingAndDirectingCredits
import com.martinrevert.latorrentola.model.TMDB.sortedByYearDescending
import com.martinrevert.latorrentola.model.YTS.Cast
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persisted offline-first snapshot of a TV series progress summary loaded from Room.
 *
 * @property summary Lightweight [TmdbTvSummary] with seasons, air dates, and IMDb ID.
 * @property isStale Whether the record exceeded its TTL and should be refreshed silently in background.
 */
data class PersistedTvProgress(
    val summary: TmdbTvSummary,
    val isStale: Boolean
)

/**
 * Coordinates TMDB requests and converts lookup failures into nullable/result values.
 *
 * @property tmdbService HTTP client for TMDB endpoints.
 * @property tvGenreDao local TV genre usage statistics.
 * @property tvProgressCacheDao optional Room cache for offline-first TV progress metadata.
 */
@Singleton
class TmdbRepository @Inject constructor(
    private val tmdbService: TmdbService,
    private val tvGenreDao: TvGenreDao,
    private val tvProgressCacheDao: TvProgressCacheDao? = null
) {
    /** API key injected through the app build configuration. */
    private val apiKey: String
        get() = BuildConfig.TMDB_API_KEY

    /** In-memory cache of TMDB TV genres keyed by active language. */
    @Volatile
    private var cachedTvGenres: List<TmdbTvGenre>? = null

    @Volatile
    private var cachedTvGenresLanguage: String? = null

    /** In-memory cache of TMDB TV series details by series ID. */
    private val tvDetailsCache = ConcurrentHashMap<Int, TmdbTvSummary>()

    /** In-memory cache of lightweight TMDB TV progress summaries by series ID. */
    private val tvProgressSummaryCache = ConcurrentHashMap<Int, TmdbTvSummary>()

    /** In-memory cache of TMDB movie lookups keyed by IMDb ID. */
    private val movieResultCache = ConcurrentHashMap<String, TmdbMovieResult>()

    /** In-memory cache of TMDB movie details keyed by TMDB movie ID. */
    private val movieDetailsCache = ConcurrentHashMap<Int, TmdbMovieDetails>()

    /** In-memory cache of TMDB movie logo URLs keyed by TMDB movie ID and language tag. */
    private val movieLogoCache = ConcurrentHashMap<String, String>()

    /** In-memory cache of TMDB TV series logo URLs keyed by TMDB series ID and language tag. */
    private val tvLogoCache = ConcurrentHashMap<String, String>()

    /** In-memory cache of TMDB TV series trailer YouTube keys keyed by TMDB series ID. */
    private val tvTrailerCache = ConcurrentHashMap<Int, String>()

    /**
     * Searches TMDB for people (actors/actresses) matching [query].
     *
     * @param query Search query text.
     * @param page Page number to retrieve.
     * @return Matching list of [TmdbPersonResult] entries.
     */
    suspend fun searchActors(query: String, page: Int = 1): List<TmdbPersonResult> {
        if (apiKey.isEmpty() || query.isBlank()) return emptyList()
        return try {
            tmdbService.searchPerson(
                apiKey = apiKey,
                query = query,
                page = page
            ).results.orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Looks up an actor by name, then fetches their profile and combined credits.
     *
     * @param actorName Name used for the TMDB people search.
     * @return Actor detail on success, or the lookup/network failure.
     */
    suspend fun getActorDetails(actorName: String): Result<TmdbActorDetail> {
        return try {
            if (apiKey.isEmpty()) {
                return Result.failure(IllegalStateException("TMDB API key not configured"))
            }

            val searchResponse = tmdbService.searchPerson(
                apiKey = apiKey,
                query = actorName
            )

            val personId = searchResponse.results?.firstOrNull()?.id
                ?: return Result.failure(NoSuchElementException("Actor not found"))

            getActorDetails(personId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Loads actor details and combined credits using the person's TMDB identifier,
     * with combined cast credits sorted by year descending.
     *
     * @param personId TMDB person identifier from the TV cast record.
     * @return Actor detail on success, or the network/configuration failure.
     */
    suspend fun getActorDetails(personId: Int): Result<TmdbActorDetail> {
        return try {
            if (apiKey.isEmpty()) {
                return Result.failure(IllegalStateException("TMDB API key not configured"))
            }

            val detail = tmdbService.getPersonDetail(
                personId = personId,
                apiKey = apiKey,
                appendToResponse = "combined_credits"
            )
            val combinedCredits = detail.combinedCredits?.combineActingAndDirectingCredits()
            val sortedDetail = if (combinedCredits != null) {
                detail.copy(combinedCredits = detail.combinedCredits.copy(cast = combinedCredits))
            } else {
                detail
            }
            Result.success(sortedDetail)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Returns a linked IMDb identifier for a TMDB movie, or `null` when unavailable. */
    suspend fun getMovieImdbId(tmdbMovieId: Int): String? {
        return try {
            if (apiKey.isEmpty()) return null
            val response = tmdbService.getMovieExternalIds(movieId = tmdbMovieId, apiKey = apiKey)
            val imdbId = response.imdbId
            if (imdbId.isNullOrEmpty()) null else imdbId
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Finds a TMDB movie by its external IMDb identifier with in-memory caching.
     *
     * @param imdbId IMDb title identifier (e.g. "tt0137523" or "0137523").
     * @return The matching [TmdbMovieResult] or `null` if not found.
     */
    suspend fun findMovieByImdbId(imdbId: String): TmdbMovieResult? {
        if (apiKey.isEmpty() || imdbId.isBlank()) return null
        val formattedImdbId = if (imdbId.startsWith("tt")) imdbId else "tt$imdbId"
        movieResultCache[formattedImdbId]?.let { return it }

        return try {
            val findResponse = tmdbService.findByExternalId(
                externalId = formattedImdbId,
                apiKey = apiKey
            )
            val result = findResponse.movieResults?.firstOrNull()
            if (result != null) {
                movieResultCache[formattedImdbId] = result
            }
            result
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetches full movie cast from TMDB using the movie's IMDb identifier.
     *
     * @param imdbId IMDb title identifier (e.g. "tt0137523").
     * @return List of [Cast] members populated with names, character names, and TMDB profile image URLs.
     */
    suspend fun getMovieCastByImdbId(imdbId: String): List<Cast> {
        if (apiKey.isEmpty() || imdbId.isBlank()) return emptyList()
        val formattedImdbId = if (imdbId.startsWith("tt")) imdbId else "tt$imdbId"
        return try {
            val findResponse = tmdbService.findByExternalId(
                externalId = formattedImdbId,
                apiKey = apiKey
            )
            val movieResult = findResponse.movieResults?.firstOrNull() ?: return emptyList()
            movieResultCache[formattedImdbId] = movieResult
            val creditsResponse = tmdbService.getMovieCredits(
                movieId = movieResult.id,
                apiKey = apiKey
            )
            creditsResponse.cast.orEmpty().map { tmdbCast ->
                Cast(
                    name = tmdbCast.name,
                    characterName = tmdbCast.character,
                    urlSmallImage = tmdbCast.fullProfileUrl
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Retrieves full TMDB movie details including credits and production companies.
     *
     * @param movieId TMDB movie identifier.
     * @return [TmdbMovieDetails] or `null` if the request fails.
     */
    suspend fun getMovieDetails(movieId: Int): TmdbMovieDetails? {
        if (apiKey.isEmpty() || movieId <= 0) return null
        movieDetailsCache[movieId]?.let { return it }
        return try {
            val details = tmdbService.getMovieDetails(
                movieId = movieId,
                apiKey = apiKey,
                appendToResponse = "credits,images",
                includeImageLanguage = TmdbLanguage.imageLanguages(),
                language = TmdbLanguage.current()
            )
            movieDetailsCache[movieId] = details
            val cacheKey = "${movieId}_${TmdbLanguage.imageLanguages()}"
            val resolvedLogo = details.logoUrl ?: ""
            movieLogoCache[cacheKey] = resolvedLogo
            details
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolves the primary logo image URL for a TMDB movie, with in-memory caching.
     *
     * @param movieId TMDB movie identifier.
     * @return Full logo URL (PNG/SVG) or null if unavailable.
     */
    suspend fun getMovieLogo(movieId: Int): String? {
        if (apiKey.isEmpty() || movieId <= 0) return null
        val cacheKey = "${movieId}_${TmdbLanguage.imageLanguages()}"
        movieLogoCache[cacheKey]?.let { return it.ifEmpty { null } }
        movieDetailsCache[movieId]?.let { details ->
            val resolvedLogo = details.logoUrl ?: ""
            movieLogoCache[cacheKey] = resolvedLogo
            return resolvedLogo.ifEmpty { null }
        }

        return try {
            val images = tmdbService.getMovieImages(
                movieId = movieId,
                apiKey = apiKey,
                includeImageLanguage = TmdbLanguage.imageLanguages()
            )
            val logoUrl = images.bestLogoUrl ?: ""
            movieLogoCache[cacheKey] = logoUrl
            logoUrl.ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolves the primary logo image URL for a movie by its external IMDb identifier.
     *
     * @param imdbId IMDb title identifier (e.g. "tt0137523").
     * @return Full logo URL (PNG/SVG) or null if unavailable.
     */
    suspend fun getMovieLogoByImdbId(imdbId: String): String? {
        val movieResult = findMovieByImdbId(imdbId) ?: return null
        return getMovieLogo(movieResult.id)
    }

    /**
     * Resolves an IMDb identifier to full TMDB movie details (director, studio, cast with characters).
     *
     * @param imdbId IMDb title identifier (e.g. "tt0137523").
     * @return [TmdbMovieDetails] or `null` if not found.
     */
    suspend fun getMovieDetailsByImdbId(imdbId: String): TmdbMovieDetails? {
        if (apiKey.isEmpty() || imdbId.isBlank()) return null
        val formattedImdbId = if (imdbId.startsWith("tt")) imdbId else "tt$imdbId"
        return try {
            val movieResult = findMovieByImdbId(formattedImdbId) ?: return null
            getMovieDetails(movieResult.id)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Loads one page from the requested TMDB TV Home feed.
     *
     * @param feed TMDB feed to request.
     * @param page Requested page number.
     * @return A page of TV series.
     */
    suspend fun getHomeTvFeed(feed: TmdbTvFeed, page: Int): TmdbTvPage {
        requireConfiguredApiKey()
        return when (feed) {
            TmdbTvFeed.ON_THE_AIR -> tmdbService.getOnTheAirTv(apiKey = apiKey, page = page)
            TmdbTvFeed.AIRING_TODAY -> tmdbService.getAiringTodayTv(apiKey = apiKey, page = page)
            TmdbTvFeed.POPULAR -> tmdbService.getPopularTv(apiKey = apiKey, page = page)
            TmdbTvFeed.TOP_RATED -> tmdbService.getTopRatedTv(apiKey = apiKey, page = page)
        }
    }

    /** Loads the TMDB television genre catalog, using an in-memory cache per language. */
    suspend fun getTvGenres(): List<TmdbTvGenre> {
        requireConfiguredApiKey()
        val currentLang = TmdbLanguage.current()
        val existing = cachedTvGenres
        if (existing != null && cachedTvGenresLanguage == currentLang && existing.isNotEmpty()) {
            return existing
        }
        val fetched = tmdbService.getTvGenres(apiKey = apiKey).genres.orEmpty()
        if (fetched.isNotEmpty()) {
            cachedTvGenres = fetched
            cachedTvGenresLanguage = currentLang
        }
        return fetched
    }

    /** Observes TMDB TV genre IDs ordered by local usage count. */
    fun observeTvGenreUsage(): Flow<Map<Int, Int>> =
        tvGenreDao.observeByPopularity().map { stats -> stats.associate { it.genreId to it.count } }

    /** Records one user visit to the TMDB TV genre [genreId]. */
    suspend fun recordTvGenreVisit(genreId: Int) {
        tvGenreDao.incrementOrInsert(genreId)
    }

    /**
     * Searches TMDB for TV series matching [query].
     *
     * @param query Search query text.
     * @param page Page number to retrieve.
     * @return Matching page of TV series summaries.
     */
    suspend fun searchTvSeries(query: String, page: Int = 1): TmdbTvPage {
        requireConfiguredApiKey()
        return tmdbService.searchTv(
            apiKey = apiKey,
            query = query,
            page = page
        )
    }

    /**
     * Searches TMDB for movies matching [query], optionally narrowing by release [year].
     *
     * @param query Movie title query text.
     * @param year Optional four-digit release year.
     * @param page Page number to retrieve.
     * @return Matching page of TMDB movie summaries.
     */
    suspend fun searchMovies(query: String, year: Int? = null, page: Int = 1): TmdbMoviePage {
        requireConfiguredApiKey()
        return tmdbService.searchMovies(
            apiKey = apiKey,
            query = query,
            year = year,
            page = page
        )
    }

    /**
     * Loads one sorted page of series matching a TV genre.
     *
     * @param genreId TMDB TV genre identifier.
     * @param sortBy TMDB discovery sort key.
     * @param page Requested result page.
     * @return The requested page of matching series.
     */
    suspend fun discoverTvByGenre(genreId: Int, sortBy: String, page: Int): TmdbTvPage {
        requireConfiguredApiKey()
        return tmdbService.discoverTvByGenre(
            apiKey = apiKey,
            genreId = genreId,
            sortBy = sortBy,
            page = page
        )
    }

    /**
     * Loads one sorted page of movies matching a watch provider (OTT).
     *
     * @param providerId TMDB watch provider identifier.
     * @param region ISO 3166-1 country code (defaults to device country or "US").
     * @param sortBy TMDB discovery sort key.
     * @param page Requested result page.
     * @return The requested page of matching movies.
     */
    suspend fun discoverMoviesByProvider(
        providerId: Int,
        region: String = Locale.getDefault().country.takeIf(String::isNotBlank) ?: "US",
        sortBy: String = "popularity.desc",
        page: Int = 1
    ): TmdbMoviePage {
        requireConfiguredApiKey()
        return tmdbService.discoverMoviesByProvider(
            apiKey = apiKey,
            providerId = providerId,
            watchRegion = region,
            sortBy = sortBy,
            page = page
        )
    }

    /**
     * Loads one sorted page of TV series matching a watch provider (OTT).
     *
     * @param providerId TMDB watch provider identifier.
     * @param region ISO 3166-1 country code (defaults to device country or "US").
     * @param sortBy TMDB discovery sort key.
     * @param page Requested result page.
     * @return The requested page of matching TV series.
     */
    suspend fun discoverTvByProvider(
        providerId: Int,
        region: String = Locale.getDefault().country.takeIf(String::isNotBlank) ?: "US",
        sortBy: String = "popularity.desc",
        page: Int = 1
    ): TmdbTvPage {
        requireConfiguredApiKey()
        return tmdbService.discoverTvByProvider(
            apiKey = apiKey,
            providerId = providerId,
            watchRegion = region,
            sortBy = sortBy,
            page = page
        )
    }

    /**
     * Retrieves all movie streaming providers available on TMDB for [region].
     *
     * @param region Optional country code (e.g. "AR", "ES", "US").
     * @return List of watch providers sorted by display priority.
     */
    suspend fun getMovieWatchProviders(region: String? = null): List<TmdbWatchProvider> {
        requireConfiguredApiKey()
        val effectiveRegion = region ?: Locale.getDefault().country.takeIf(String::isNotBlank)
        val response = tmdbService.getMovieWatchProviders(
            apiKey = apiKey,
            watchRegion = effectiveRegion
        )
        return response.results.orEmpty().sortedBy { it.displayPriority }
    }

    /**
     * Retrieves all TV series streaming providers available on TMDB for [region].
     *
     * @param region Optional country code (e.g. "AR", "ES", "US").
     * @return List of watch providers sorted by display priority.
     */
    suspend fun getTvWatchProviders(region: String? = null): List<TmdbWatchProvider> {
        requireConfiguredApiKey()
        val effectiveRegion = region ?: Locale.getDefault().country.takeIf(String::isNotBlank)
        val response = tmdbService.getTvWatchProviders(
            apiKey = apiKey,
            watchRegion = effectiveRegion
        )
        return response.results.orEmpty().sortedBy { it.displayPriority }
    }

    /** Returns cached TV series summary if already fetched into memory. */
    fun getCachedTvDetails(seriesId: Int): TmdbTvSummary? = tvDetailsCache[seriesId]

    /** Returns a cached TV series progress summary from RAM (either full details or lightweight summary). */
    fun getCachedTvProgressSummary(seriesId: Int): TmdbTvSummary? =
        tvDetailsCache[seriesId] ?: tvProgressSummaryCache[seriesId]

    /**
     * Batch-loads offline-first persisted TV progress summaries from Room for [seriesIds],
     * warming the in-memory progress cache only for fresh (non-stale) records.
     */
    suspend fun getPersistedTvProgressSummaries(seriesIds: List<Int>): Map<Int, PersistedTvProgress> {
        val dao = tvProgressCacheDao ?: return emptyMap()
        if (seriesIds.isEmpty()) return emptyMap()
        return try {
            val now = System.currentTimeMillis()
            dao.getByIds(seriesIds.distinct()).associate { entity ->
                val summary = entity.toTmdbTvSummary()
                val isStale = entity.isStale(now)
                if (!isStale) {
                    tvProgressSummaryCache.putIfAbsent(entity.seriesId, summary)
                }
                entity.seriesId to PersistedTvProgress(
                    summary = summary,
                    isStale = isStale
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyMap()
        }
    }

    /**
     * Batch-loads offline-first persisted TV progress summaries from Room by canonical IMDb IDs,
     * warming the in-memory progress cache only for fresh (non-stale) records.
     */
    suspend fun getPersistedTvProgressSummariesByImdbIds(imdbIds: List<String>): Map<String, PersistedTvProgress> {
        val dao = tvProgressCacheDao ?: return emptyMap()
        val distinctImdbIds = imdbIds.mapNotNull { it.trim().takeIf(String::isNotBlank) }.distinct()
        if (distinctImdbIds.isEmpty()) return emptyMap()
        return try {
            val now = System.currentTimeMillis()
            dao.getByImdbIds(distinctImdbIds).mapNotNull { entity ->
                val imdb = entity.imdbId.trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
                val summary = entity.toTmdbTvSummary()
                val isStale = entity.isStale(now)
                if (!isStale) {
                    tvProgressSummaryCache.putIfAbsent(entity.seriesId, summary)
                }
                imdb.lowercase(Locale.ROOT) to PersistedTvProgress(
                    summary = summary,
                    isStale = isStale
                )
            }.toMap()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyMap()
        }
    }

    /**
     * Loads lightweight TV series metadata (seasons, air schedule, and external IDs) optimized for
     * watch-progress calculation without fetching heavy aggregate cast or video payloads.
     *
     * Checks RAM cache, then offline-first Room `tv_progress_cache`, and falls back to the lightweight
     * TMDB endpoint (persisting the result to Room). If a stale cached record exists and the network
     * refresh fails, returns the persisted stale summary without caching it in RAM so subsequent
     * callers can retry once connectivity recovers.
     */
    suspend fun getTvProgressSummary(seriesId: Int, forceRefresh: Boolean = false): TmdbTvSummary {
        if (!forceRefresh) {
            tvDetailsCache[seriesId]?.let { return it }
            tvProgressSummaryCache[seriesId]?.let { return it }
        }

        val cachedEntity = try {
            tvProgressCacheDao?.get(seriesId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
        val cachedSummary = cachedEntity?.toTmdbTvSummary()

        if (!forceRefresh && cachedEntity != null && !cachedEntity.isStale() && cachedSummary != null) {
            tvProgressSummaryCache[seriesId] = cachedSummary
            return cachedSummary
        }

        return try {
            requireConfiguredApiKey()
            val fetched = tmdbService.getTvProgressSummary(seriesId = seriesId, apiKey = apiKey)
            val normalized = if (fetched.id == seriesId) fetched else fetched.copy(id = seriesId)
            tvProgressSummaryCache[seriesId] = normalized
            persistTvProgressSummary(normalized)
            normalized
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (cachedSummary != null) {
                if (!cachedEntity.isStale()) {
                    tvProgressSummaryCache[seriesId] = cachedSummary
                }
                cachedSummary
            } else {
                throw e
            }
        }
    }

    /**
     * Loads a series with its seasons and aggregate cast, caching the result in memory
     * and updating the offline-first Room progress cache.
     *
     * @param seriesId TMDB TV series identifier.
     * @return The series detail response.
     */
    suspend fun getTvDetails(seriesId: Int): TmdbTvSummary {
        requireConfiguredApiKey()
        tvDetailsCache[seriesId]?.let { return it }
        val fetched = tmdbService.getTvDetails(seriesId = seriesId, apiKey = apiKey)
        tvDetailsCache[seriesId] = fetched
        tvProgressSummaryCache[seriesId] = fetched
        persistTvProgressSummary(fetched)
        return fetched
    }

    private suspend fun persistTvProgressSummary(summary: TmdbTvSummary) {
        val dao = tvProgressCacheDao ?: return
        if (summary.id <= 0) return
        try {
            dao.upsert(TvProgressCacheEntity.from(summary))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    /**
     * Loads episode listings for a series season.
     *
     * @param seriesId TMDB TV series identifier.
     * @param seasonNumber Season number to retrieve.
     * @return Season metadata and episodes.
     */
    suspend fun getTvSeasonDetails(seriesId: Int, seasonNumber: Int): TmdbTvSeasonDetails {
        requireConfiguredApiKey()
        return tmdbService.getTvSeasonDetails(
            seriesId = seriesId,
            seasonNumber = seasonNumber,
            apiKey = apiKey
        )
    }

    /**
     * Returns a linked IMDb identifier for a TMDB TV series, or `null` when unavailable.
     * Strips "tt" prefix if present for API compatibility.
     *
     * @param seriesId TMDB TV series identifier.
     */
    suspend fun getTvImdbId(seriesId: Int): String? {
        return try {
            if (apiKey.isEmpty()) return null
            val response = tmdbService.getTvExternalIds(seriesId = seriesId, apiKey = apiKey)
            val imdbId = response.imdbId
            if (imdbId.isNullOrEmpty()) null else imdbId.removePrefix("tt")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Finds a TMDB TV series by its external IMDb identifier.
     *
     * @param imdbId IMDb title identifier (e.g. "tt0944947" or "0944947").
     * @return The matching [TmdbTvSummary] or `null` if not found.
     */
    suspend fun findTvSeriesByImdbId(imdbId: String): TmdbTvSummary? {
        if (apiKey.isEmpty() || imdbId.isBlank()) return null
        val formattedImdbId = if (imdbId.startsWith("tt")) imdbId else "tt$imdbId"
        return try {
            val findResponse = tmdbService.findByExternalId(
                externalId = formattedImdbId,
                apiKey = apiKey
            )
            findResponse.tvResults?.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolves the primary logo image URL for a TMDB TV series, with in-memory caching.
     *
     * @param seriesId TMDB TV series identifier.
     * @return Full logo URL (PNG/SVG) or null if unavailable.
     */
    suspend fun getTvSeriesLogo(seriesId: Int): String? {
        if (apiKey.isEmpty() || seriesId <= 0) return null
        val cacheKey = "${seriesId}_${TmdbLanguage.imageLanguages()}"
        tvLogoCache[cacheKey]?.let { return it.ifEmpty { null } }

        return try {
            val images = tmdbService.getTvImages(
                seriesId = seriesId,
                apiKey = apiKey,
                includeImageLanguage = TmdbLanguage.imageLanguages()
            )
            val logoUrl = images.bestLogoUrl ?: ""
            tvLogoCache[cacheKey] = logoUrl
            logoUrl.ifEmpty { null }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    /**
     * Resolves the primary YouTube trailer video key for a TMDB TV series, with in-memory caching.
     *
     * @param seriesId TMDB TV series identifier.
     * @return YouTube video key (e.g., "KPLWWIOCOOQ") or null if unavailable.
     */
    suspend fun getTvSeriesTrailer(seriesId: Int): String? {
        if (apiKey.isEmpty() || seriesId <= 0) return null
        tvTrailerCache[seriesId]?.let { return it.ifEmpty { null } }

        return try {
            val cachedTrailer = tvDetailsCache[seriesId]?.videos?.getBestTrailerKey()
            if (!cachedTrailer.isNullOrBlank()) {
                tvTrailerCache[seriesId] = cachedTrailer
                return cachedTrailer
            }

            val currentLangVideos = tmdbService.getTvVideos(
                seriesId = seriesId,
                apiKey = apiKey,
                language = TmdbLanguage.current()
            ).getBestTrailerKey()

            val trailer = if (!currentLangVideos.isNullOrBlank()) {
                currentLangVideos
            } else {
                tmdbService.getTvVideos(
                    seriesId = seriesId,
                    apiKey = apiKey,
                    language = "en-US"
                ).getBestTrailerKey()
            }

            val result = trailer ?: ""
            tvTrailerCache[seriesId] = result
            result.ifEmpty { null }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    /** Fails clearly when the build does not provide a TMDB API key. */
    private fun requireConfiguredApiKey() {
        check(apiKey.isNotBlank()) { "TMDB API key not configured" }
    }
}
