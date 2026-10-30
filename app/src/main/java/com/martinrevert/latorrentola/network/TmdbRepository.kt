package com.martinrevert.latorrentola.network

import com.martinrevert.latorrentola.BuildConfig
import com.martinrevert.latorrentola.database.TvGenreDao
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
import com.martinrevert.latorrentola.model.TMDB.sortedByYearDescending
import com.martinrevert.latorrentola.model.YTS.Cast
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Coordinates TMDB requests and converts lookup failures into nullable/result values.
 *
 * @property tmdbService HTTP client for TMDB endpoints.
 * @property tvGenreDao local TV genre usage statistics.
 */
@Singleton
class TmdbRepository @Inject constructor(
    private val tmdbService: TmdbService,
    private val tvGenreDao: TvGenreDao
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

    /** In-memory cache of TMDB movie lookups keyed by IMDb ID. */
    private val movieResultCache = ConcurrentHashMap<String, TmdbMovieResult>()

    /** In-memory cache of TMDB movie details keyed by TMDB movie ID. */
    private val movieDetailsCache = ConcurrentHashMap<Int, TmdbMovieDetails>()

    /** In-memory cache of TMDB movie logo URLs keyed by TMDB movie ID and language tag. */
    private val movieLogoCache = ConcurrentHashMap<String, String>()

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
            val sortedCast = detail.combinedCredits?.cast?.sortedByYearDescending()
            val sortedDetail = if (sortedCast != null) {
                detail.copy(combinedCredits = detail.combinedCredits.copy(cast = sortedCast))
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

    /**
     * Loads a series with its seasons and aggregate cast, caching the result in memory.
     *
     * @param seriesId TMDB TV series identifier.
     * @return The series detail response.
     */
    suspend fun getTvDetails(seriesId: Int): TmdbTvSummary {
        requireConfiguredApiKey()
        tvDetailsCache[seriesId]?.let { return it }
        val fetched = tmdbService.getTvDetails(seriesId = seriesId, apiKey = apiKey)
        tvDetailsCache[seriesId] = fetched
        return fetched
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

    /** Fails clearly when the build does not provide a TMDB API key. */
    private fun requireConfiguredApiKey() {
        check(apiKey.isNotBlank()) { "TMDB API key not configured" }
    }
}
