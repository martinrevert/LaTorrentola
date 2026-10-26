package com.martinrevert.latorrentola.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages fetching, caching, and supplying live, high-availability public BitTorrent trackers.
 *
 * Keeps an in-memory cache seeded with vetted tier-1 trackers for instantaneous synchronous access,
 * and asynchronously refreshes the list from authoritative remote tracker mirrors on app startup or
 * periodically once every 24 hours.
 */
@Singleton
class PublicTrackersRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        loadCachedTrackers()
    }

    private fun loadCachedTrackers() {
        val cached = sharedPreferences.getString(KEY_CACHED_TRACKERS, null)
        if (!cached.isNullOrBlank()) {
            val list = cached.lines()
                .map { it.trim() }
                .filter { it.startsWith("udp://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
            if (list.size >= MIN_VALID_TRACKERS) {
                activeTrackers = list
            }
        }
    }

    /**
     * Refreshes the active public trackers from remote sources if the cache has expired (> 24 hours)
     * or if [force] is true. Operates in the background and never throws.
     *
     * @param force When true, bypasses the 24-hour TTL check.
     * @return `true` if trackers were updated from remote, `false` otherwise.
     */
    suspend fun refreshTrackersIfStale(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val lastUpdate = sharedPreferences.getLong(KEY_LAST_UPDATE_MS, 0L)
        val now = System.currentTimeMillis()
        if (!force && (now - lastUpdate < CACHE_TTL_MS) && activeTrackers.isNotEmpty()) {
            return@withContext false
        }

        val remoteTrackers = fetchTrackersFromRemote(PRIMARY_URL)
            ?: fetchTrackersFromRemote(FALLBACK_URL)

        if (remoteTrackers != null && remoteTrackers.size >= MIN_VALID_TRACKERS) {
            activeTrackers = remoteTrackers
            sharedPreferences.edit()
                .putString(KEY_CACHED_TRACKERS, remoteTrackers.joinToString("\n"))
                .putLong(KEY_LAST_UPDATE_MS, now)
                .apply()
            Log.i(TAG, "Successfully refreshed and cached ${remoteTrackers.size} live public trackers")
            true
        } else {
            Log.w(TAG, "Failed to refresh trackers from remote; keeping existing ${activeTrackers.size} trackers")
            false
        }
    }

    private fun fetchTrackersFromRemote(url: String): List<String>? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "LaTorrentola/1.0")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body.string()
                val parsed = body.lines()
                    .map { it.trim() }
                    .filter { line ->
                        line.isNotBlank() &&
                            (line.startsWith("udp://", ignoreCase = true) ||
                             line.startsWith("http://", ignoreCase = true) ||
                             line.startsWith("https://", ignoreCase = true))
                    }
                    .distinct()
                if (parsed.size >= MIN_VALID_TRACKERS) parsed else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching trackers from $url: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "PublicTrackersRepo"
        private const val PREFS_NAME = "public_trackers_prefs"
        private const val KEY_CACHED_TRACKERS = "cached_trackers"
        private const val KEY_LAST_UPDATE_MS = "last_update_ms"
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val MIN_VALID_TRACKERS = 5

        /** Primary source for vetted live public trackers. */
        const val PRIMARY_URL = "https://raw.githubusercontent.com/ngosang/trackerslist/master/trackers_best.txt"

        /** High-speed CDN mirror for live public trackers. */
        const val FALLBACK_URL = "https://cdn.jsdelivr.net/gh/ngosang/trackerslist@master/trackers_best.txt"

        /**
         * Hardcoded fallback of vetted, top-tier active public BitTorrent trackers
         * (sourced from ngosang/trackerslist best trackers).
         */
        val DEFAULT_TRACKERS = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://open.tracker.cl:1337/announce",
            "udp://tracker.skynetcloud.site:6969/announce",
            "udp://tracker.qu.ax:6969/announce",
            "udp://tracker.ducks.party:1984/announce",
            "udp://tracker-udp.gbitt.info:80/announce",
            "udp://tracker.tryhackx.org:6969/announce",
            "udp://tracker.corpscorp.online:80/announce",
            "udp://tracker.nyaa.vc:6969/announce",
            "http://tracker.dler.org:6969/announce",
            "udp://tracker2.dler.org:80/announce",
            "udp://tracker.gmi.gd:6969/announce",
            "udp://tracker.peerfect.org:6969/announce",
            "udp://tracker.opentrackr.com:6969/announce",
            "udp://tracker.ilibr.org:6969/announce"
        )

        @Volatile
        private var activeTrackers: List<String> = DEFAULT_TRACKERS

        /**
         * Returns the current active list of public trackers.
         * Thread-safe, non-blocking, and available instantaneously.
         */
        fun getActiveTrackers(): List<String> = activeTrackers

        /**
         * Ensures that [magnetUri] contains the active public trackers.
         * If any active tracker is not already present, appends it as `&tr=...`.
         * Returns non-magnet URIs unchanged.
         */
        fun ensureTrackersInMagnet(magnetUri: String): String {
            if (!magnetUri.startsWith("magnet:?", ignoreCase = true)) return magnetUri
            val trackers = getActiveTrackers()
            val existingDecodedUri = try {
                URLDecoder.decode(magnetUri, Charsets.UTF_8.name())
            } catch (_: Exception) {
                magnetUri
            }

            val missingTrackers = trackers.filter { tracker ->
                !existingDecodedUri.contains(tracker, ignoreCase = true)
            }
            if (missingTrackers.isEmpty()) return magnetUri

            val trackerQuery = missingTrackers.joinToString("") { tracker ->
                "&tr=$tracker"
            }
            return magnetUri + trackerQuery
        }

        internal fun setActiveTrackersForTesting(trackers: List<String>) {
            activeTrackers = trackers
        }
    }
}
