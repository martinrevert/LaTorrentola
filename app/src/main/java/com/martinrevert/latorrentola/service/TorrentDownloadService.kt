package com.martinrevert.latorrentola.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.database.TorrentDownloadDao
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.ui.player.LocalPlayerActivity
import com.martinrevert.latorrentola.model.torrent.TorrentDownload
import com.martinrevert.latorrentola.utils.MediaIdHelper
import com.martinrevert.latorrentola.utils.TorrentLaunchHelper
import dagger.hilt.android.AndroidEntryPoint
import com.martinrevert.latorrentola.utils.mediaMimeType
import org.libtorrent4j.Priority
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.libtorrent4j.SessionManager
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import java.io.File
import java.util.Locale
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds
import java.util.concurrent.atomic.AtomicBoolean
import com.martinrevert.latorrentola.service.streaming.PlayerStreamingConfig
import com.martinrevert.latorrentola.service.streaming.StreamingPiecePlanner
import com.martinrevert.latorrentola.service.streaming.TorrentSessionConfig
import com.martinrevert.latorrentola.service.streaming.VerifiedPieceTracker
import com.martinrevert.latorrentola.service.storage.TorrentStorageResolver
import com.martinrevert.latorrentola.utils.isTvDevice
import org.libtorrent4j.AlertListener
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.PieceFinishedAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert

/**
 * Downloads torrents in a foreground service and only serves byte ranges covered by verified
 * libtorrent pieces; app-managed completed files are retained until the user deletes them.
 */
@AndroidEntryPoint
class TorrentDownloadService : Service() {

    /** Persists app-local transfer jobs and progress. */
    @Inject
    lateinit var torrentDownloadDao: TorrentDownloadDao

    /** Repository managing user favorites, downloads, and watch history sync. */
    @Inject
    lateinit var userLibraryRepository: UserLibraryRepository

    /** Background work scope owned by this service instance. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Native libtorrent session used while the foreground transfer is active. */
    private var sessionManager: SessionManager? = null

    /** Serializes ownership changes so native session shutdown occurs exactly once. */
    private val sessionLock = Any()

    /** Synchronizes requests submitted from the service's main-thread callbacks. */
    private val queueLock = Any()

    /** Pending transfers processed one at a time by the native session. */
    private val downloadQueue = ArrayDeque<DownloadRequest>()

    /** Verified-range endpoints kept alive for active playback and Cast sessions. */
    private val streamServers = ConcurrentHashMap<String, VerifiedTorrentHttpServer>()

    /** Player intents for ready transfers and jobs awaiting their startup buffer. */
    private val readyPlayerIntents = ConcurrentHashMap<String, Intent>()

    /** Jobs whose player should open as soon as their startup buffer is verified. */
    private val openWhenReadyHashes = ConcurrentHashMap.newKeySet<String>()

    /** Active transfers requested for deletion and awaiting native-session shutdown. */
    private val deletedActiveHashes = ConcurrentHashMap.newKeySet<String>()

    /** Whether a worker currently owns the transfer queue. */
    @Volatile
    private var workerStarted = false

    /** Most recently delivered service start identifier for safe shutdown. */
    @Volatile
    private var latestStartId = 0
    /** Hash of the currently active torrent, if one is downloading. */
    @Volatile
    private var activeInfoHash: String? = null
    /** Active request of the currently downloading torrent. */
    @Volatile
    private var activeRequest: DownloadRequest? = null
    /** Hash of a transfer paused from the download manager. */
    @Volatile
    private var pausedInfoHash: String? = null

    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    private fun acquireLocks() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "LaTorrentola:TorrentWakeLock").apply {
                    setReferenceCounted(false)
                    acquire(24 * 60 * 60 * 1000L)
                }
            }
            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                wifiLock = @Suppress("DEPRECATION") if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    wifiManager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "LaTorrentola:TorrentWifiLock")
                } else {
                    wifiManager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "LaTorrentola:TorrentWifiLock")
                }.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLock/WifiLock", e)
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null

        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Exception) {}
        wifiLock = null
    }

    /** Initializes the notification channel required for foreground transfers. */
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireLocks()
        serviceScope.launch {
            torrentDownloadDao.getRecoverable().forEach { stored ->
                val hash = parseHexInfoHash(stored.magnetUri) ?: return@forEach
                synchronized(queueLock) {
                    if (downloadQueue.none { it.infoHash == hash } && hash != activeInfoHash) {
                        downloadQueue.addLast(
                            DownloadRequest(hash, stored.magnetUri, stored.title, stored.castWhenReady)
                        )
                    }
                }
            }
            if (synchronized(queueLock) { downloadQueue.isNotEmpty() }) {
                startQueueWorker(getString(R.string.torrent_download_title))
            }
        }
    }

    /**
     * Starts a local torrent transfer and opens playback as soon as its startup buffer verifies.
     *
     * @param intent Explicit service request containing action, magnet URI, and title.
     * @param flags Android service start flags.
     * @param startId Identifier for this start request.
     * @return Redelivery policy for the active transfer.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent?.action == ACTION_PLAY_STREAM) {
            val infoHash = intent.getStringExtra(EXTRA_INFO_HASH).orEmpty()
            if (infoHash.isBlank()) {
                stopSelf(startId)
                return START_NOT_STICKY
            }
            val readyIntent = readyPlayerIntents[infoHash]
            val server = streamServers[infoHash]
            if (readyIntent != null && server != null) {
                startForegroundForPlayback(
                    buildReadyNotification(
                        readyIntent.getStringExtra(LocalPlayerActivity.EXTRA_TITLE).orEmpty(),
                        readyIntent,
                        server.listeningPort
                    )
                )
            } else {
                startForegroundForPlayback(
                    buildProgressNotification(getString(R.string.torrent_download_title), 0)
                )
            }
            serviceScope.launch { openReadyPlayer(infoHash) }
            return START_STICKY
        }
        if (intent?.action == ACTION_STOP_STREAM) {
            val infoHash = intent.getStringExtra(EXTRA_INFO_HASH).orEmpty()
            streamServers.remove(infoHash)?.stop()
            readyPlayerIntents.remove(infoHash)
            if (!workerStarted && streamServers.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PAUSE || intent?.action == ACTION_RESUME) {
            val requestedHash = intent.getStringExtra(EXTRA_INFO_HASH).orEmpty()
            if (requestedHash.isNotBlank()) {
                val shouldPause = intent.action == ACTION_PAUSE
                val isActive = synchronized(queueLock) {
                    if (activeInfoHash == requestedHash) {
                        if (shouldPause) pausedInfoHash = requestedHash
                        true
                    } else {
                        if (shouldPause) {
                            downloadQueue.removeAll { it.infoHash == requestedHash }
                        }
                        false
                    }
                }
                if (isActive) {
                    synchronized(sessionLock) {
                        sessionManager
                            ?.find(Sha1Hash.parseHex(requestedHash))
                            ?.takeIf { it.isValid }
                            ?.let { handle ->
                                if (shouldPause) handle.pause() else handle.resume()
                            }
                    }
                }
                serviceScope.launch {
                    val stored = torrentDownloadDao.get(requestedHash) ?: return@launch
                    if (shouldPause) {
                        updateDownloadState(requestedHash, STATE_PAUSED)
                        if (!isActive) {
                            withContext(Dispatchers.Main) {
                                if (!workerStarted && streamServers.isEmpty()) {
                                    stopForeground(STOP_FOREGROUND_REMOVE)
                                    stopSelf(startId)
                                }
                            }
                        }
                    } else if (isActive) {
                        pausedInfoHash = null
                        updateDownloadState(requestedHash, STATE_DOWNLOADING)
                    } else {
                        val resumed = stored.copy(
                            state = STATE_QUEUED,
                            updatedAtMillis = System.currentTimeMillis()
                        )
                        torrentDownloadDao.upsert(resumed)
                        synchronized(queueLock) {
                            if (activeInfoHash != requestedHash &&
                                downloadQueue.none { it.infoHash == requestedHash }
                            ) {
                                downloadQueue.addLast(
                                    DownloadRequest(
                                        resumed.infoHash,
                                        resumed.magnetUri,
                                        resumed.title,
                                        resumed.castWhenReady
                                    )
                                )
                            }
                        }
                        startQueueWorker(resumed.title)
                    }
                }
            }
            return START_STICKY
        }
        if (intent?.action == ACTION_STORAGE_UNMOUNTED) {
            Log.w(TAG, "Storage volume unmounted or ejected. Pausing active transfers.")
            val activeHash = synchronized(queueLock) { activeInfoHash }
            if (activeHash != null) {
                pausedInfoHash = activeHash
                synchronized(sessionLock) {
                    sessionManager
                        ?.find(Sha1Hash.parseHex(activeHash))
                        ?.takeIf { it.isValid }
                        ?.pause()
                }
                serviceScope.launch {
                    updateDownloadState(activeHash, STATE_PAUSED)
                    val activeTitle = activeRequest?.title ?: getString(R.string.torrent_download_title)
                    updateNotification(buildFailedNotification(activeTitle))
                }
            }
            return START_STICKY
        }
        if (intent?.action == ACTION_DELETE) {
            val requestedHash = intent.getStringExtra(EXTRA_INFO_HASH).orEmpty()
            if (requestedHash.isNotBlank()) {
                serviceScope.launch {
                    val active = synchronized(queueLock) {
                        if (activeInfoHash == requestedHash) {
                            deletedActiveHashes.add(requestedHash)
                            true
                        } else {
                            downloadQueue.removeAll { it.infoHash == requestedHash }
                            false
                        }
                    }
                    streamServers.remove(requestedHash)?.stop()
                    readyPlayerIntents.remove(requestedHash)
                    openWhenReadyHashes.remove(requestedHash)
                    if (active) {
                        synchronized(sessionLock) {
                            sessionManager?.let { manager ->
                                manager.find(Sha1Hash.parseHex(requestedHash))?.let(manager::remove)
                            }
                        }
                    } else {
                        TorrentStorageResolver.findTorrentDirectory(this@TorrentDownloadService, requestedHash)?.deleteRecursively()
                        File(filesDir, "$TORRENT_DIRECTORY/$requestedHash").deleteRecursively()
                        torrentDownloadDao.delete(requestedHash)
                        if (!workerStarted && streamServers.isEmpty()) {
                            withContext(Dispatchers.Main.immediate) {
                                stopForeground(STOP_FOREGROUND_REMOVE)
                                stopSelf(startId)
                            }
                        }
                    }
                }
            }
            return START_STICKY
        }
        if (intent?.action != ACTION_DOWNLOAD) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        var seriesId = intent.getIntExtra(EXTRA_SERIES_ID, 0)
        var seasonNumber = intent.getIntExtra(EXTRA_SEASON_NUMBER, 0)
        var episodeNumber = intent.getIntExtra(EXTRA_EPISODE_NUMBER, 0)
        var movieId = intent.getIntExtra(EXTRA_MOVIE_ID, 0)
        val magnetUri = intent.getStringExtra(EXTRA_MAGNET_URI).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank {
            getString(R.string.torrent_download_title)
        }
        if (magnetUri.isBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val infoHash = parseHexInfoHash(magnetUri)
        if (infoHash == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (seriesId <= 0 && movieId <= 0) {
            try {
                val existingRecord = runBlocking(Dispatchers.IO) { torrentDownloadDao.get(infoHash) }
                if (existingRecord != null) {
                    if ((existingRecord.seriesId ?: 0) > 0) {
                        seriesId = existingRecord.seriesId ?: 0
                        if (seasonNumber <= 0) seasonNumber = existingRecord.seasonNumber ?: 0
                        if (episodeNumber <= 0) episodeNumber = existingRecord.episodeNumber ?: 0
                    }
                    if ((existingRecord.movieId ?: 0) > 0) {
                        movieId = existingRecord.movieId ?: 0
                    }
                }
            } catch (_: Exception) {}
        }

        openWhenReadyHashes.add(infoHash)
        startForegroundForDownload(buildProgressNotification(title, 0))
        serviceScope.launch {
            val castWhenReady = intent.getBooleanExtra(EXTRA_CAST_WHEN_READY, false)
            val existingIntent = readyPlayerIntents[infoHash]
            if (existingIntent != null) {
                existingIntent.putExtra(LocalPlayerActivity.EXTRA_CAST_ENABLED, castWhenReady)
                openWhenReadyHashes.remove(infoHash)
                openReadyPlayer(infoHash)
                return@launch
            }

            if (infoHash == activeInfoHash) {
                openWhenReadyHashes.add(infoHash)
                activeRequest?.castWhenReady = castWhenReady
                return@launch
            }
            val existing = torrentDownloadDao.get(infoHash)
            val completedFile = existing
                ?.takeIf { it.state == STATE_COMPLETED }
                ?.mediaPath
                ?.let(::File)
                ?.takeIf { it.isFile && it.canRead() }
            val nextEpisodeInfo = intent.getStringArrayListExtra(EXTRA_NEXT_EPISODE_INFO)

            val mediaId = if (seriesId > 0) {
                MediaIdHelper.forEpisode(
                    seriesId = seriesId,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    infoHash = infoHash,
                    title = title
                )
            } else {
                MediaIdHelper.forMovie(
                    movieId = movieId,
                    infoHash = infoHash,
                    title = title
                )
            }
            val playbackProgress = userLibraryRepository.getPlaybackProgress(mediaId)
            val resumePos = if (playbackProgress != null && playbackProgress.positionMs > 5000L && !playbackProgress.isCompleted) playbackProgress.positionMs else 0L
            val dur = if (playbackProgress != null && playbackProgress.positionMs > 5000L && !playbackProgress.isCompleted) playbackProgress.durationMs else 0L

            if (completedFile != null) {
                openWhenReadyHashes.remove(infoHash)
                val castWhenReady = intent.getBooleanExtra(EXTRA_CAST_WHEN_READY, false)
                startCompletedPlayback(infoHash, title, completedFile, castWhenReady, nextEpisodeInfo, seriesId, seasonNumber, episodeNumber, movieId)
                return@launch
            }

            if (!TorrentLaunchHelper.hasEnoughStorage(this@TorrentDownloadService)) {
                openWhenReadyHashes.remove(infoHash)
                torrentDownloadDao.upsert(
                    TorrentDownload(
                        infoHash = infoHash,
                        magnetUri = magnetUri,
                        title = title,
                        state = STATE_FAILED,
                        progressPercent = existing?.progressPercent ?: 0,
                        mediaPath = existing?.mediaPath,
                        castWhenReady = intent.getBooleanExtra(EXTRA_CAST_WHEN_READY, false),
                        updatedAtMillis = System.currentTimeMillis(),
                        seriesId = seriesId,
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber,
                        movieId = movieId
                    )
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                return@launch
            }
            torrentDownloadDao.upsert(
                TorrentDownload(
                    infoHash = infoHash,
                    magnetUri = magnetUri,
                    title = title,
                    state = STATE_QUEUED,
                    progressPercent = existing?.progressPercent ?: 0,
                    mediaPath = existing?.mediaPath,
                    castWhenReady = intent.getBooleanExtra(EXTRA_CAST_WHEN_READY, false),
                    updatedAtMillis = System.currentTimeMillis(),
                    seriesId = seriesId,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    movieId = movieId
                )
            )
            synchronized(queueLock) {
                if (infoHash != activeInfoHash && downloadQueue.none { it.infoHash == infoHash }) {
                    downloadQueue.addLast(
                        DownloadRequest(
                            infoHash = infoHash,
                            magnetUri = magnetUri,
                            title = title,
                            castWhenReady = intent.getBooleanExtra(EXTRA_CAST_WHEN_READY, false),
                            nextEpisodeInfo = nextEpisodeInfo,
                            seriesId = seriesId,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber,
                            movieId = movieId,
                            resumePositionMs = resumePos,
                            durationMs = dur
                        )
                    )
                }
            }
            startQueueWorker(title)
        }
        return START_REDELIVER_INTENT
    }

    /**
     * Makes a previously completed, verified file available to local playback or a Cast receiver.
     *
     * @param infoHash Local torrent job identifier.
     * @param title User-visible media title.
     * @param file Existing file whose complete torrent was hash verified.
     * @param castWhenReady Whether to expose a LAN URL for Cast.
     * @param nextEpisodeInfo Optional next episode information for quality selection after playback.
     */
    private suspend fun startCompletedPlayback(
        infoHash: String,
        title: String,
        file: File,
        castWhenReady: Boolean,
        nextEpisodeInfo: ArrayList<String>? = null,
        seriesId: Int = 0,
        seasonNumber: Int = 0,
        episodeNumber: Int = 0,
        movieId: Int = 0
    ) {
        streamServers.remove(infoHash)?.stop()

        val server = VerifiedTorrentHttpServer(
            mediaFile = file,
            expectedLength = file.length(),
            accessToken = infoHash,
            isRangeVerified = { _, _ -> true }
        )
        val port = server.startServer()
        val castUrl = try {
            server.lanUrl()
        } catch (_: Exception) {
            null
        }
        streamServers[infoHash] = server

        val playerIntent = Intent(this, LocalPlayerActivity::class.java)
            .putExtra(LocalPlayerActivity.EXTRA_STREAM_URL, server.localUrl())
            .putExtra(LocalPlayerActivity.EXTRA_CAST_URL, castUrl)
            .putExtra(LocalPlayerActivity.EXTRA_INFO_HASH, infoHash)
            .putExtra(LocalPlayerActivity.EXTRA_FILE_PATH, file.absolutePath)
            .putExtra(LocalPlayerActivity.EXTRA_CAST_ENABLED, castWhenReady)
            .putExtra(LocalPlayerActivity.EXTRA_MIME_TYPE, file.mediaMimeType())
            .putExtra(LocalPlayerActivity.EXTRA_TITLE, title)
            .putExtra(LocalPlayerActivity.EXTRA_SERIES_ID, seriesId)
            .putExtra(LocalPlayerActivity.EXTRA_SEASON_NUMBER, seasonNumber)
            .putExtra(LocalPlayerActivity.EXTRA_EPISODE_NUMBER, episodeNumber)
            .putExtra(LocalPlayerActivity.EXTRA_MOVIE_ID, movieId)
            .apply {
                if (nextEpisodeInfo != null) {
                    putStringArrayListExtra(LocalPlayerActivity.EXTRA_NEXT_EPISODE_INFO, nextEpisodeInfo)
                }
            }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        readyPlayerIntents[infoHash] = playerIntent

        withContext(Dispatchers.Main) {
            startForegroundForPlayback(buildReadyNotification(title, playerIntent, port))
            startActivity(playerIntent)
        }
    }

    /**
     * Opens a ready stream now or remembers the request until the startup buffer is verified.
     *
     * @param infoHash Stable local torrent job identifier.
     */
    private suspend fun openReadyPlayer(infoHash: String) {
        val readyIntent = readyPlayerIntents[infoHash]
        val server = streamServers[infoHash]
        if (readyIntent != null && server != null) {
            val title = readyIntent.getStringExtra(LocalPlayerActivity.EXTRA_TITLE).orEmpty()
            withContext(Dispatchers.Main.immediate) {
                startForegroundForPlayback(
                    buildReadyNotification(title, readyIntent, server.listeningPort)
                )
                startActivity(Intent(readyIntent))
            }
            return
        }

        // The stream server is not currently running for this hash (e.g. after service restart).
        // Check if the torrent exists in our local Room database to resurrect streaming automatically.
        val stored = torrentDownloadDao.get(infoHash)
        if (stored != null) {
            val completedFile = stored.mediaPath?.let(::File)?.takeIf { it.isFile && it.canRead() }
            if (stored.state == STATE_COMPLETED && completedFile != null) {
                openWhenReadyHashes.remove(infoHash)
                startCompletedPlayback(
                    infoHash = infoHash,
                    title = stored.title,
                    file = completedFile,
                    castWhenReady = stored.castWhenReady,
                    seriesId = stored.seriesId ?: 0,
                    seasonNumber = stored.seasonNumber ?: 0,
                    episodeNumber = stored.episodeNumber ?: 0,
                    movieId = stored.movieId ?: 0
                )
                return
            }

            openWhenReadyHashes.add(infoHash)
            if (infoHash == activeInfoHash) {
                return
            }

            val mediaId = if ((stored.seriesId ?: 0) > 0) {
                MediaIdHelper.forEpisode(
                    seriesId = stored.seriesId ?: 0,
                    seasonNumber = stored.seasonNumber ?: 0,
                    episodeNumber = stored.episodeNumber ?: 0,
                    infoHash = infoHash,
                    title = stored.title
                )
            } else {
                MediaIdHelper.forMovie(
                    movieId = stored.movieId ?: 0,
                    infoHash = infoHash,
                    title = stored.title
                )
            }
            val playbackProgress = userLibraryRepository.getPlaybackProgress(mediaId)
            val resumePos = if (playbackProgress != null && playbackProgress.positionMs > 5000L && !playbackProgress.isCompleted) playbackProgress.positionMs else 0L
            val dur = if (playbackProgress != null && playbackProgress.positionMs > 5000L && !playbackProgress.isCompleted) playbackProgress.durationMs else 0L

            synchronized(queueLock) {
                if (downloadQueue.none { it.infoHash == infoHash }) {
                    downloadQueue.addFirst(
                        DownloadRequest(
                            infoHash = infoHash,
                            magnetUri = stored.magnetUri,
                            title = stored.title,
                            castWhenReady = stored.castWhenReady,
                            seriesId = stored.seriesId ?: 0,
                            seasonNumber = stored.seasonNumber ?: 0,
                            episodeNumber = stored.episodeNumber ?: 0,
                            movieId = stored.movieId ?: 0,
                            resumePositionMs = resumePos,
                            durationMs = dur
                        )
                    )
                }
            }
            startQueueWorker(stored.title)
            return
        }

        openWhenReadyHashes.add(infoHash)
    }

    /**
     * Starts queue processing once and presents the required foreground notification.
     *
     * @param title Title shown while the transfer queue is active.
     */
    private suspend fun startQueueWorker(title: String) {
        val shouldStart = withContext(Dispatchers.Main.immediate) {
            synchronized(queueLock) {
                if (!workerStarted && downloadQueue.isNotEmpty()) {
                    workerStarted = true
                    startForegroundForDownload(buildProgressNotification(title, 0))
                    true
                } else {
                    false
                }
            }
        }
        if (shouldStart) serviceScope.launch { processDownloadQueue() }
    }

    /** Processes queued transfers serially and safely stops once the queue is empty. */
    private suspend fun processDownloadQueue() {
        while (true) {
            val request = synchronized(queueLock) {
                if (downloadQueue.isEmpty()) {
                    null
                } else {
                    downloadQueue.removeFirst().also {
                        activeInfoHash = it.infoHash
                        activeRequest = it
                    }
                }
            } ?: break

            updateNotification(buildProgressNotification(request.title, 0))
            try {
                downloadAndAwaitVerification(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid torrent download request", e)
                streamServers.remove(request.infoHash)?.stop()
                readyPlayerIntents.remove(request.infoHash)
                openWhenReadyHashes.remove(request.infoHash)
                updateDownloadState(request.infoHash, STATE_FAILED)
                updateNotification(buildFailedNotification(request.title))
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Torrent download could not be completed", e)
                streamServers.remove(request.infoHash)?.stop()
                readyPlayerIntents.remove(request.infoHash)
                openWhenReadyHashes.remove(request.infoHash)
                updateDownloadState(request.infoHash, STATE_FAILED)
                updateNotification(buildFailedNotification(request.title))
            } catch (e: SecurityException) {
                Log.e(TAG, "Torrent download was blocked by the platform", e)
                streamServers.remove(request.infoHash)?.stop()
                readyPlayerIntents.remove(request.infoHash)
                openWhenReadyHashes.remove(request.infoHash)
                updateDownloadState(request.infoHash, STATE_FAILED)
                updateNotification(buildFailedNotification(request.title))
            } finally {
                stopSessionManager()
                if (deletedActiveHashes.remove(request.infoHash)) {
                    TorrentStorageResolver.findTorrentDirectory(this@TorrentDownloadService, request.infoHash)?.deleteRecursively()
                    File(filesDir, "$TORRENT_DIRECTORY/${request.infoHash}").deleteRecursively()
                    torrentDownloadDao.delete(request.infoHash)
                }
                activeInfoHash = null
                activeRequest = null
                pausedInfoHash = null
            }
        }

        withContext(Dispatchers.Main) {
            synchronized(queueLock) {
                if (downloadQueue.isNotEmpty()) {
                    serviceScope.launch {
                        processDownloadQueue()
                    }
                } else {
                    workerStarted = false
                    if (streamServers.isEmpty()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelfResult(latestStartId)
                    } else {
                        readyPlayerIntents.entries.firstOrNull { streamServers.containsKey(it.key) }
                            ?.let { (infoHash, playerIntent) ->
                                val port = streamServers[infoHash]?.listeningPort ?: return@let
                                startForegroundForPlayback(
                                    buildReadyNotification(
                                        playerIntent.getStringExtra(LocalPlayerActivity.EXTRA_TITLE)
                                            .orEmpty(),
                                        playerIntent,
                                        port
                                    )
                                )
                            }
                    }
                }
            }
        }
    }

    /**
     * Runs libtorrent off the main thread until every wanted piece has passed hash checks.
     *
     * @param request Transfer identity, source magnet, notification title, and playback mode.
     */
    private suspend fun downloadAndAwaitVerification(request: DownloadRequest) {
        val infoHash = request.infoHash
        val downloadDirectory = TorrentStorageResolver.resolveTorrentDirectory(this, infoHash)
        if (!downloadDirectory.exists() && !downloadDirectory.mkdirs()) {
            throw IllegalStateException("Unable to create the private torrent directory")
        }

        val isTv = isTvDevice()
        val sessionParams = TorrentSessionConfig.buildSessionParams(isTv = isTv)
        val manager = SessionManager()
        var pieceTracker: VerifiedPieceTracker? = null
        var piecePlanner: StreamingPiecePlanner? = null

        val alertListener = object : AlertListener {
            override fun types(): IntArray = intArrayOf(
                org.libtorrent4j.alerts.AlertType.PIECE_FINISHED.swig(),
                org.libtorrent4j.alerts.AlertType.TORRENT_FINISHED.swig(),
                org.libtorrent4j.alerts.AlertType.TORRENT_ERROR.swig()
            )
            override fun alert(alert: Alert<*>) {
                when (alert) {
                    is PieceFinishedAlert -> {
                        pieceTracker?.markPieceVerified(alert.pieceIndex())
                    }
                    is TorrentFinishedAlert -> {
                        pieceTracker?.setAllVerified()
                    }
                    else -> {}
                }
            }
        }
        manager.addListener(alertListener)

        synchronized(sessionLock) {
            sessionManager = manager
            manager.start(sessionParams)
            manager.download(
                request.magnetUri,
                downloadDirectory,
                TorrentFlags.SEQUENTIAL_DOWNLOAD
            )
        }
        var mediaTarget: MediaTarget? = null
        var readyNotified = false
        var readyPlayerIntent: Intent? = null
        var readyServerPort = 0
        val completed = AtomicBoolean(false)

        var lastReportedProgress = -1
        var lastReportedState = ""
        var lastDbUpdateTime = 0L

        try {
            while (true) {
                if (request.infoHash in deletedActiveHashes) return
                val targetWasMissing = mediaTarget == null
                val handle = synchronized(sessionLock) {
                    if (sessionManager !== manager) {
                        null
                    } else {
                        manager.find(Sha1Hash.parseHex(infoHash))
                            ?.takeIf { it.isValid }
                            ?.also { handle ->
                                if (pausedInfoHash == infoHash) handle.pause()
                                if (mediaTarget == null) {
                                    mediaTarget = findMediaTarget(handle, downloadDirectory, request.title)
                                    mediaTarget?.let { target ->
                                        val tracker = VerifiedPieceTracker(
                                            totalPieces = target.lastPiece + 1,
                                            fileOffset = target.fileOffset,
                                            pieceLength = target.pieceLength
                                        )
                                        for (p in target.firstPiece..target.lastPiece) {
                                            if (handle.havePiece(p)) {
                                                tracker.markPieceVerified(p)
                                            }
                                        }
                                        pieceTracker = tracker

                                        val isMkv = target.file.extension.equals("mkv", ignoreCase = true)
                                        val planner = StreamingPiecePlanner(
                                            fileOffset = target.fileOffset,
                                            fileLength = target.length,
                                            pieceLength = target.pieceLength,
                                            firstPiece = target.firstPiece,
                                            lastPiece = target.lastPiece,
                                            isMkv = isMkv
                                        )
                                        piecePlanner = planner

                                        for (index in 0 until target.fileCount) {
                                            handle.filePriority(
                                                index,
                                                if (index == target.fileIndex) {
                                                    Priority.DEFAULT
                                                } else {
                                                    Priority.IGNORE
                                                }
                                            )
                                        }

                                        val resumePiece = planner.calculateResumePiece(request.resumePositionMs, request.durationMs)
                                        val initialPlan = planner.planForRange(
                                            requestedStart = 0L,
                                            requestedEnd = minOf(target.length - 1, STARTUP_BUFFER_BYTES),
                                            tracker = tracker
                                        )
                                        for (p in initialPlan.piecesToPrioritize) {
                                            handle.piecePriority(p, Priority.TOP_PRIORITY)
                                        }
                                        handle.setSequentialRange(resumePiece, target.lastPiece)
                                        if (resumePiece > target.firstPiece) {
                                            for (p in resumePiece..minOf(resumePiece + 32, target.lastPiece)) {
                                                handle.piecePriority(p, Priority.TOP_PRIORITY)
                                            }
                                        }
                                    }
                                }
                            }
                    }
                }
                if (handle != null) {
                    val target = mediaTarget
                    if (target != null && targetWasMissing) {
                        updateDownloadRecord(request, STATE_BUFFERING, 0, target.file.absolutePath)
                    }

                    // Deterministic startup buffer enforcement: continuously request missing startup pieces without polling JNI on verified pieces
                    if (!readyNotified && target != null) {
                        val tracker = pieceTracker
                        val planner = piecePlanner
                        if (tracker != null && planner != null) {
                            synchronized(sessionLock) {
                                if (sessionManager === manager && handle.isValid) {
                                    val initialPlan = planner.planForRange(
                                        requestedStart = 0L,
                                        requestedEnd = minOf(target.length - 1, STARTUP_BUFFER_BYTES),
                                        tracker = tracker
                                    )
                                    for (p in initialPlan.piecesToPrioritize) {
                                        handle.piecePriority(p, Priority.TOP_PRIORITY)
                                        try { handle.setPieceDeadline(p, 500) } catch (_: Exception) {}
                                    }

                                    val isIndexedContainer = target.file.extension.equals("mkv", ignoreCase = true) ||
                                        target.file.extension.equals("webm", ignoreCase = true) ||
                                        target.file.extension.equals("mp4", ignoreCase = true)
                                    if (isIndexedContainer) {
                                        val tailBytes = minOf(target.length, 5L * 1024L * 1024L)
                                        val tailPieceStart = ((target.fileOffset + maxOf(0L, target.length - tailBytes)) / target.pieceLength).toInt().coerceIn(target.firstPiece, target.lastPiece)
                                        for (p in tailPieceStart..target.lastPiece) {
                                            if (!tracker.isPieceVerified(p)) {
                                                handle.piecePriority(p, Priority.TOP_PRIORITY)
                                                try { handle.setPieceDeadline(p, 500) } catch (_: Exception) {}
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val status = synchronized(sessionLock) {
                        if (sessionManager === manager && handle.isValid) handle.status() else null
                    }
                    if (status == null) {
                        delay(1.seconds)
                        continue
                    }
                    val progress = (status.progress() * 100).toInt().coerceIn(0, 100)
                    val numSeeds = status.numSeeds()
                    val numPeers = status.numPeers()
                    val readyIntent = readyPlayerIntent

                    val currentState = when {
                        readyIntent != null -> STATE_READY
                        mediaTarget == null -> STATE_DOWNLOADING
                        else -> STATE_BUFFERING
                    }
                    val now = System.currentTimeMillis()
                    val shouldUpdateDb = currentState != lastReportedState ||
                        progress != lastReportedProgress ||
                        (now - lastDbUpdateTime >= 5000L)

                    if (shouldUpdateDb) {
                        lastReportedState = currentState
                        lastReportedProgress = progress
                        lastDbUpdateTime = now
                        if (readyIntent == null) {
                            updateNotification(buildProgressNotification(request.title, progress))
                        } else {
                            updateNotification(
                                buildReadyNotification(request.title, readyIntent, readyServerPort)
                            )
                        }
                        updateDownloadRecord(
                            request,
                            currentState,
                            progress,
                            mediaTarget?.file?.absolutePath,
                            numSeeds,
                            numPeers
                        )
                    }

                    val tracker = pieceTracker
                    if (target != null && tracker != null) {
                        val unverified = tracker.getUnverifiedPieces(target.firstPiece, target.lastPiece)
                        if (unverified.isNotEmpty()) {
                            val newlyVerified = mutableListOf<Int>()
                            synchronized(sessionLock) {
                                if (sessionManager === manager && handle.isValid) {
                                    for (p in unverified) {
                                        if (handle.havePiece(p)) {
                                            newlyVerified.add(p)
                                        }
                                    }
                                }
                            }
                            if (newlyVerified.isNotEmpty()) {
                                tracker.markPiecesVerified(newlyVerified)
                            }
                        }
                    }

                    val isReady = !readyNotified && target != null && tracker != null && run {
                        val startupEnd = minOf(target.length - 1, STARTUP_BUFFER_BYTES - 1)
                        val headVerified = tracker.isRangeVerified(0, startupEnd) { p ->
                            synchronized(sessionLock) {
                                sessionManager === manager && handle.isValid && handle.havePiece(p)
                            }
                        }
                        val isIndexedContainer = target.file.extension.equals("mkv", ignoreCase = true) ||
                            target.file.extension.equals("webm", ignoreCase = true) ||
                            target.file.extension.equals("mp4", ignoreCase = true)
                        val tailVerified = if (isIndexedContainer) {
                            val tailBytes = minOf(target.length, 2L * 1024L * 1024L)
                            val tailStart = maxOf(0L, target.length - tailBytes)
                            tracker.isRangeVerified(tailStart, target.length - 1L) { p ->
                                synchronized(sessionLock) {
                                    sessionManager === manager && handle.isValid && handle.havePiece(p)
                                }
                            }
                        } else {
                            true
                        }
                        val resumePiece = piecePlanner?.calculateResumePiece(request.resumePositionMs, request.durationMs) ?: target.firstPiece
                        val resumeVerified = if (resumePiece > target.firstPiece) {
                            (resumePiece until minOf(resumePiece + 3, target.lastPiece + 1)).all { p ->
                                tracker.isPieceVerified(p) || synchronized(sessionLock) {
                                    sessionManager === manager && handle.isValid && handle.havePiece(p)
                                }
                            }
                        } else {
                            true
                        }
                        headVerified && tailVerified && resumeVerified
                    }

                    if (isReady) {
                        var lastPrioritizedWindowStart = -1
                        var lastPrioritizedWindowEnd = -1

                        val server = VerifiedTorrentHttpServer(
                            mediaFile = target.file,
                            expectedLength = target.length,
                            accessToken = infoHash,
                            onRangeRequested = { start, end ->
                                if (!completed.get()) {
                                    val plan = piecePlanner?.planForRange(
                                        requestedStart = start,
                                        requestedEnd = end,
                                        tracker = tracker
                                    )
                                    if (plan != null) {
                                        val windowStart = plan.sequentialRangeStart
                                        val isCurrentRangeUnverified = !tracker.isRangeVerified(start, end)
                                        val needsUpdate = isCurrentRangeUnverified ||
                                            plan.isSeekJump || plan.isTailProbe ||
                                            lastPrioritizedWindowStart == -1 ||
                                            kotlin.math.abs(windowStart - lastPrioritizedWindowStart) >= 2

                                        if (needsUpdate) {
                                            val oldWindowStart = lastPrioritizedWindowStart
                                            val oldWindowEnd = lastPrioritizedWindowEnd
                                            lastPrioritizedWindowStart = windowStart
                                            lastPrioritizedWindowEnd = windowStart + 32

                                            synchronized(sessionLock) {
                                                if (sessionManager === manager && handle.isValid) {
                                                    try {
                                                        if (plan.isSeekJump && oldWindowStart != -1) {
                                                            // Only reset deadlines on the previously prioritized window, NOT all pieces
                                                            for (p in oldWindowStart..minOf(oldWindowEnd, target.lastPiece)) {
                                                                try { handle.resetPieceDeadline(p) } catch (_: Throwable) {}
                                                            }
                                                        }
                                                        if (!plan.isTailProbe) {
                                                            try { handle.setSequentialRange(plan.sequentialRangeStart, target.lastPiece) } catch (_: Throwable) {}
                                                        }
                                                        for (p in plan.piecesToPrioritize) {
                                                            try { handle.piecePriority(p, Priority.TOP_PRIORITY) } catch (_: Throwable) {}
                                                            val deadline = plan.deadlines[p] ?: 1000
                                                            try {
                                                                handle.setPieceDeadline(p, deadline)
                                                            } catch (_: Throwable) {}
                                                        }
                                                    } catch (e: Throwable) {
                                                        Log.w(TAG, "Error updating piece deadlines in libtorrent", e)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            isRangeVerified = { start, end ->
                                completed.get() || tracker.isRangeVerified(start, end) { p ->
                                    synchronized(sessionLock) {
                                        sessionManager === manager && handle.isValid && handle.havePiece(p)
                                    }
                                }
                            },
                            awaitRangeVerified = { start, end, timeoutMs ->
                                completed.get() || tracker.awaitRange(start, end, timeoutMs) { p ->
                                    synchronized(sessionLock) {
                                        sessionManager === manager && handle.isValid && handle.havePiece(p)
                                    }
                                }
                            }
                        )
                        val port = server.startServer()
                        val castUrl = try {
                            server.lanUrl()
                        } catch (_: Exception) {
                            null
                        }
                        streamServers[infoHash]?.stop()
                        streamServers[infoHash] = server


                        val playerIntent = Intent(this, LocalPlayerActivity::class.java)
                            .putExtra(LocalPlayerActivity.EXTRA_STREAM_URL, server.localUrl())
                            .putExtra(LocalPlayerActivity.EXTRA_CAST_URL, castUrl)
                            .putExtra(LocalPlayerActivity.EXTRA_INFO_HASH, infoHash)
                            .putExtra(LocalPlayerActivity.EXTRA_FILE_PATH, target.file.absolutePath)
                            .putExtra(LocalPlayerActivity.EXTRA_PARTIAL_TORRENT_STREAM, true)
                            .putExtra(LocalPlayerActivity.EXTRA_CAST_ENABLED, request.castWhenReady)
                            .putExtra(LocalPlayerActivity.EXTRA_MIME_TYPE, target.file.mediaMimeType())
                            .putExtra(LocalPlayerActivity.EXTRA_TITLE, request.title)
                            .putExtra(LocalPlayerActivity.EXTRA_SERIES_ID, request.seriesId)
                            .putExtra(LocalPlayerActivity.EXTRA_SEASON_NUMBER, request.seasonNumber)
                            .putExtra(LocalPlayerActivity.EXTRA_EPISODE_NUMBER, request.episodeNumber)
                            .putExtra(LocalPlayerActivity.EXTRA_MOVIE_ID, request.movieId)
                            .apply {
                                if (request.nextEpisodeInfo != null) {
                                    putStringArrayListExtra(LocalPlayerActivity.EXTRA_NEXT_EPISODE_INFO, request.nextEpisodeInfo)
                                }
                            }
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        readyPlayerIntent = playerIntent
                        readyPlayerIntents[infoHash] = playerIntent
                        readyServerPort = port
                        withContext(Dispatchers.Main.immediate) {
                            startForegroundForPlayback(
                                buildReadyNotification(request.title, playerIntent, port)
                            )
                        }
                        updateDownloadRecord(request, STATE_READY, progress, target.file.absolutePath, numSeeds, numPeers)
                        if (openWhenReadyHashes.remove(infoHash)) {
                            withContext(Dispatchers.Main) { startActivity(playerIntent) }
                        }
                        readyNotified = true
                    }
                    if (progress == 100 && status.totalWantedDone() >= status.totalWanted()) {
                        val playableFile = target?.file
                            ?: findLargestVideoFile(downloadDirectory)
                            ?: throw IllegalStateException("The completed torrent contains no supported video file")
                        completed.set(true)
                        pieceTracker?.setAllVerified()
                        updateDownloadRecord(request, STATE_COMPLETED, 100, playableFile.absolutePath, 0, 0)

                        if (playableFile.extension.equals("mkv", ignoreCase = true)) {
                            val mp4File = File(playableFile.parentFile, "${playableFile.nameWithoutExtension}.mp4")
                            if (mp4File.isFile) {
                                try {
                                    mp4File.delete()
                                    Log.i(TAG, "Deleted legacy remuxed MP4 file: ${mp4File.name}")
                                } catch (_: Exception) {}
                            }
                        }

                        // ZERO SEEDING MANDATE: completely terminate native torrent session
                        manager.removeListener(alertListener)
                        stopSessionManager()

                        val playerIntent = readyPlayerIntent
                        if (playerIntent != null) {
                            updateNotification(
                                buildReadyNotification(request.title, playerIntent, readyServerPort)
                            )
                        } else {
                            updateNotification(buildCompletedNotification(request.title, playableFile))
                        }
                        return
                    }
                    if (pausedInfoHash == infoHash) {
                        updateDownloadRecord(request, STATE_PAUSED, progress, null, numSeeds, numPeers)
                    }
                }
                delay(1.seconds)
            }
        } finally {
            try { manager.removeListener(alertListener) } catch (_: Exception) {}
        }
    }

    /**
     * Converts a hexadecimal or Base32 BTIH from a magnet link to normalized hexadecimal.
     *
     * @param magnetUri Magnet URI to inspect.
     * @return Lowercase hexadecimal BTIH or `null` when the hash is unsupported.
     */
    private fun parseHexInfoHash(magnetUri: String): String? {
        val rawHash = Regex(
            """xt=urn:btih:([a-fA-F0-9]{40}|[A-Z2-7]{32})""",
            RegexOption.IGNORE_CASE
        )
            .find(magnetUri)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null
        if (rawHash.length == 40) return rawHash.lowercase(Locale.ROOT)

        val decoded = ByteArray(20)
        var bitBuffer = 0
        var bitCount = 0
        var outputIndex = 0
        for (character in rawHash.uppercase(Locale.ROOT)) {
            val value = BASE32_ALPHABET.indexOf(character)
            if (value < 0) return null
            bitBuffer = (bitBuffer shl 5) or value
            bitCount += 5
            if (bitCount >= 8) {
                bitCount -= 8
                if (outputIndex >= decoded.size) return null
                decoded[outputIndex++] = (bitBuffer shr bitCount).toByte()
                bitBuffer = bitBuffer and ((1 shl bitCount) - 1)
            }
        }
        if (outputIndex != decoded.size || bitCount != 0) return null
        return decoded.joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
    }

    /**
     * Selects the supported video file matching the requested episode title from resolved torrent metadata.
     *
     * @param handle Active libtorrent handle.
     * @param directory Private torrent payload directory.
     * @param title User-visible media title containing season and episode info.
     * @return Selected video file and its piece geometry, or `null` until metadata is available.
     */
    private fun findMediaTarget(
        handle: org.libtorrent4j.TorrentHandle,
        directory: File,
        title: String = ""
    ): MediaTarget? {
        val torrentInfo = handle.torrentFile()?.takeIf { it.isValid } ?: return null
        val files = torrentInfo.files()
        val videoExtensions = setOf("mkv", "mp4", "m4v", "avi", "mov", "webm", "ts")
        val videoIndices = (0 until files.numFiles())
            .filter { files.fileName(it).substringAfterLast('.', "").lowercase() in videoExtensions }

        if (videoIndices.isEmpty()) return null

        val targetIndex = videoIndices.firstOrNull { index ->
            matchesTitleEpisode(files.fileName(index), title)
        } ?: videoIndices.maxByOrNull(files::fileSize) ?: return null

        val path = File(files.filePath(targetIndex, directory.absolutePath))
        val firstPiece = files.pieceIndexAtFile(targetIndex)
        val lastPiece = files.lastPieceIndexAtFile(targetIndex)
        return MediaTarget(
            file = path,
            fileIndex = targetIndex,
            fileCount = files.numFiles(),
            length = files.fileSize(targetIndex),
            fileOffset = files.fileOffset(targetIndex),
            pieceLength = files.pieceLength(),
            firstPiece = firstPiece,
            lastPiece = lastPiece
        )
    }

    private fun matchesTitleEpisode(fileName: String, title: String): Boolean {
        if (title.isBlank()) return false
        val sEPattern = Regex("""[sS](\d{1,2})[eE](\d{1,2})""", RegexOption.IGNORE_CASE)
        val titleMatch = sEPattern.find(title)
        if (titleMatch != null) {
            val ts = titleMatch.groupValues[1]
            val te = titleMatch.groupValues[2]
            val fileMatch = sEPattern.find(fileName)
            if (fileMatch != null) {
                val fs = fileMatch.groupValues[1]
                val fe = fileMatch.groupValues[2]
                if (ts.toInt() == fs.toInt() && te.toInt() == fe.toInt()) return true
            }
        }

        val epNumRegex = Regex("""[eE](\d{1,2})""", RegexOption.IGNORE_CASE)
        val titleEpMatch = epNumRegex.find(title)
        if (titleEpMatch != null) {
            val te = titleEpMatch.groupValues[1].toInt()
            val fileEpMatch = epNumRegex.find(fileName)
            if (fileEpMatch != null) {
                val fe = fileEpMatch.groupValues[1].toInt()
                if (te == fe) return true
            }
        }

        return false
    }

    /**
     * Requires an initial contiguous span of complete, hash-verified pieces.
     *
     * @param handle Active libtorrent handle.
     * @param target Selected video and its piece geometry.
     * @return Whether at least the startup buffer is safe to offer.
     */
    private fun hasVerifiedBuffer(
        handle: org.libtorrent4j.TorrentHandle,
        target: MediaTarget
    ): Boolean {
        val startupEnd = minOf(target.length - 1, STARTUP_BUFFER_BYTES - 1)
        return isVerifiedRange(handle, target, 0, startupEnd)
    }

    /**
     * Checks every torrent piece that overlaps a requested range before serving any bytes.
     *
     * @param handle Active libtorrent handle.
     * @param target Selected video and its piece geometry.
     * @param start Inclusive byte offset within the video.
     * @param end Inclusive byte offset within the video.
     * @return Whether every overlapping piece has passed libtorrent's hash check.
     */
    private fun isVerifiedRange(
        handle: org.libtorrent4j.TorrentHandle,
        target: MediaTarget,
        start: Long,
        end: Long
    ): Boolean = synchronized(sessionLock) {
        if (start < 0 || end !in start until target.length || !handle.isValid) {
            return@synchronized false
        }
        val firstPiece = ((target.fileOffset + start) / target.pieceLength).toInt()
        val lastPiece = ((target.fileOffset + end) / target.pieceLength).toInt()
        (firstPiece..lastPiece).all(handle::havePiece)
    }

    /**
     * Finds the largest supported video file after the entire download is verified.
     *
     * @param directory Completed torrent storage directory.
     * @return Largest readable video candidate, or `null` if no supported video exists.
     */
    private fun findLargestVideoFile(directory: File): File? {
        val supportedExtensions = setOf("mkv", "mp4", "m4v", "avi", "mov", "webm", "ts")
        return directory.walkTopDown()
            .filter { file ->
                file.isFile && file.extension.lowercase(Locale.ROOT) in supportedExtensions
            }
            .maxByOrNull(File::length)
    }

    /** Creates the channel used by persistent torrent progress notifications. */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.torrent_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * Keeps downloads in the foreground and declares active streaming when a server is also open.
     *
     * @param notification Foreground transfer notification.
     */
    private fun startForegroundForDownload(notification: Notification) {
        startForegroundWithType(
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            includePlayback = streamServers.isNotEmpty()
        )
    }

    /**
     * Keeps active local or Cast streaming in a media-playback foreground service.
     *
     * @param notification Foreground playback notification.
     */
    private fun startForegroundForPlayback(notification: Notification) {
        val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
            (if (workerStarted) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
        startForegroundWithType(notification, type, includePlayback = true)
    }

    /**
     * Applies the foreground service types supported by the current Android version.
     *
     * @param notification Current foreground notification.
     * @param type Declared foreground service types required for the active work.
     * @param includePlayback Whether an HTTP stream is being served.
     */
    private fun startForegroundWithType(
        notification: Notification,
        type: Int,
        includePlayback: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val isPlayback = includePlayback || streamServers.isNotEmpty()
            val foregroundType = if (isPlayback) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                type
            }

            try {
                startForeground(NOTIFICATION_ID, notification, foregroundType)
            } catch (e: Exception) {
                Log.w(TAG, "startForeground with type $foregroundType failed, attempting fallback to MEDIA_PLAYBACK", e)
                try {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } catch (e2: Exception) {
                    Log.e(TAG, "All startForeground attempts failed", e2)
                }
            }
        } else {
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.e(TAG, "startForeground legacy failed", e)
            }
        }
    }

    /**
     * Builds a progress notification for an in-progress local download.
     *
     * @param title Media title shown in the notification.
     * @param progress Integer progress percentage.
     * @return Foreground transfer notification.
     */
    private fun buildProgressNotification(title: String, progress: Int): Notification =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(getString(R.string.torrent_download_progress, progress))
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    /**
     * Builds a notification that opens the verified completed file when selected.
     *
     * @param title Media title shown in the notification.
     * @param file Completed video file with verified torrent pieces.
     * @return Notification with a playback content action.
     */
    private fun buildCompletedNotification(title: String, file: File): Notification {
        val hashPattern = Regex("(?i)[a-f0-9]{40}")
        val extractedHash = hashPattern.find(file.absolutePath)?.value?.lowercase(java.util.Locale.ROOT).orEmpty()
        var seriesId = 0
        var seasonNumber = 0
        var episodeNumber = 0
        var movieId = 0
        if (extractedHash.isNotBlank()) {
            try {
                val record = runBlocking(Dispatchers.IO) { torrentDownloadDao.get(extractedHash) }
                if (record != null) {
                    seriesId = record.seriesId ?: 0
                    seasonNumber = record.seasonNumber ?: 0
                    episodeNumber = record.episodeNumber ?: 0
                    movieId = record.movieId ?: 0
                }
            } catch (_: Exception) {}
        }
        val playerIntent = Intent(this, LocalPlayerActivity::class.java)
            .putExtra(LocalPlayerActivity.EXTRA_FILE_PATH, file.absolutePath)
            .putExtra(LocalPlayerActivity.EXTRA_TITLE, title)
            .putExtra(LocalPlayerActivity.EXTRA_INFO_HASH, extractedHash)
            .putExtra(LocalPlayerActivity.EXTRA_SERIES_ID, seriesId)
            .putExtra(LocalPlayerActivity.EXTRA_SEASON_NUMBER, seasonNumber)
            .putExtra(LocalPlayerActivity.EXTRA_EPISODE_NUMBER, episodeNumber)
            .putExtra(LocalPlayerActivity.EXTRA_MOVIE_ID, movieId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            this,
            file.absolutePath.hashCode(),
            playerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(getString(R.string.torrent_download_complete))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
    }

    /**
     * Builds a notification that opens a stream after its startup pieces pass hash checks.
     *
     * @param title Media title.
     * @param playerIntent Intent containing local and LAN playback URLs.
     * @param port Embedded server port used for playback.
     * @return Actionable playback-ready notification.
     */
    private fun buildReadyNotification(title: String, playerIntent: Intent, port: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            title.hashCode(),
            playerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(getString(R.string.torrent_playback_ready))
            .setSubText(":$port")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /**
     * Builds an error notification without exposing the magnet or torrent metadata.
     *
     * @param title Media title shown in the notification.
     * @return Non-ongoing failure notification.
     */
    private fun buildFailedNotification(title: String): Notification =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(getString(R.string.torrent_download_failed))
            .setAutoCancel(true)
            .build()

    /**
     * Replaces the foreground notification with [notification].
     *
     * @param notification Updated progress, completion, or failure notification.
     */
    private fun updateNotification(notification: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    /**
     * Stores the latest durable local job state without touching cloud history.
     *
     * @param request Transfer metadata persisted with the state.
     * @param state Current local transfer state.
     * @param progress Verified torrent progress percentage.
     * @param mediaPath Selected video file path, if torrent metadata is available.
     */
    private suspend fun updateDownloadRecord(
        request: DownloadRequest,
        state: String,
        progress: Int,
        mediaPath: String?,
        numSeeds: Int = 0,
        numPeers: Int = 0
    ) {
        torrentDownloadDao.upsert(
            TorrentDownload(
                infoHash = request.infoHash,
                magnetUri = request.magnetUri,
                title = request.title,
                state = state,
                progressPercent = progress,
                mediaPath = mediaPath,
                castWhenReady = request.castWhenReady,
                updatedAtMillis = System.currentTimeMillis(),
                seriesId = request.seriesId,
                seasonNumber = request.seasonNumber,
                episodeNumber = request.episodeNumber,
                movieId = request.movieId,
                numSeeds = numSeeds,
                numPeers = numPeers
            )
        )
    }

    /**
     * Changes a persisted transfer's state while preserving its progress and file path.
     *
     * @param infoHash Stable local job identifier.
     * @param state New transfer state.
     */
    private suspend fun updateDownloadState(infoHash: String, state: String) {
        torrentDownloadDao.get(infoHash)?.let { download ->
            torrentDownloadDao.upsert(
                download.copy(state = state, updatedAtMillis = System.currentTimeMillis())
            )
        }
    }

    /** Stops and clears the active native session without racing service teardown. */
    private suspend fun stopSessionManager() {
        val manager = synchronized(sessionLock) {
            val m = sessionManager
            sessionManager = null
            m
        } ?: return
        withContext(Dispatchers.IO) {
            try {
                manager.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping SessionManager", e)
            }
        }
    }

    /**
     * One queued transfer with the original magnet URI and user-visible title.
     *
     * @property infoHash Normalized info hash used by persistence and file storage.
     * @property magnetUri Magnet URI submitted by the selected movie or episode release.
     * @property title Display title used in progress and completion notifications.
     * @property castWhenReady Whether this job should expose a Cast route when ready.
     */
    private data class DownloadRequest(
        val infoHash: String,
        val magnetUri: String,
        val title: String,
        var castWhenReady: Boolean,
        val nextEpisodeInfo: ArrayList<String>? = null,
        val seriesId: Int = 0,
        val seasonNumber: Int = 0,
        val episodeNumber: Int = 0,
        val movieId: Int = 0,
        val resumePositionMs: Long = 0L,
        val durationMs: Long = 0L
    )

    /**
     * Video file location and torrent piece geometry used by the verified-range server.
     *
     * @property file Local libtorrent storage path.
     * @property fileIndex Index in the torrent file tree.
     * @property fileCount Number of torrent files for priority setup.
     * @property length Exact video file length from torrent metadata.
     * @property fileOffset Absolute byte offset in the concatenated torrent data.
     * @property pieceLength Torrent piece size in bytes.
     * @property firstPiece First piece intersecting the video file.
     * @property lastPiece Last piece intersecting the video file.
     */
    private data class MediaTarget(
        val file: File,
        val fileIndex: Int,
        val fileCount: Int,
        val length: Long,
        val fileOffset: Long,
        val pieceLength: Int,
        val firstPiece: Int,
        val lastPiece: Int
    )

    /**
     * This service is started explicitly and does not expose a binder.
     *
     * @param intent Ignored binding intent.
     * @return Always `null`; clients use explicit start requests.
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /** Stops owned coroutines and closes the native session when the service is destroyed. */
    override fun onDestroy() {
        releaseLocks()
        serviceScope.cancel()
        Thread { runBlockingStopSession() }.start()
        super.onDestroy()
    }

    /** Closes the session from the teardown helper thread without blocking the main thread. */
    private fun runBlockingStopSession() {
        val manager = synchronized(sessionLock) { sessionManager } ?: return
        synchronized(sessionLock) {
            if (sessionManager === manager) {
                try {
                    manager.stop()
                } finally {
                    sessionManager = null
                }
            }
        }
    }

    companion object {
        /** Foreground transfer action. */
        const val ACTION_DOWNLOAD = "com.martinrevert.latorrentola.action.DOWNLOAD_TORRENT"
        /** Pause active-transfer command. */
        const val ACTION_PAUSE = "com.martinrevert.latorrentola.action.PAUSE_TORRENT"
        /** Resume active-transfer command. */
        const val ACTION_RESUME = "com.martinrevert.latorrentola.action.RESUME_TORRENT"
        /** Open a locally downloaded torrent once its startup buffer is ready. */
        const val ACTION_PLAY_STREAM = "com.martinrevert.latorrentola.action.PLAY_TORRENT_STREAM"
        /** Remove a local transfer command. */
        const val ACTION_DELETE = "com.martinrevert.latorrentola.action.DELETE_TORRENT"
        /** Storage volume unmounted or ejected while transfers may be active. */
        const val ACTION_STORAGE_UNMOUNTED = "com.martinrevert.latorrentola.action.STORAGE_UNMOUNTED"
        /** Magnet URI intent extra. */
        const val EXTRA_MAGNET_URI = "torrent_magnet_uri"
        /** User-visible content title intent extra. */
        const val EXTRA_TITLE = "torrent_title"
        /** Whether to show the Cast action after a verified startup buffer is ready. */
        const val EXTRA_CAST_WHEN_READY = "torrent_cast_when_ready"
        /** MIME type of the selected video file. */
        const val EXTRA_MIME_TYPE = "torrent_media_mime_type"
        /** Info-hash extra used by job control commands. */
        const val EXTRA_INFO_HASH = "torrent_info_hash"
        /** Series ID extra. */
        const val EXTRA_SERIES_ID = "torrent_series_id"
        /** Season number extra. */
        const val EXTRA_SEASON_NUMBER = "torrent_season_number"
        /** Episode number extra. */
        const val EXTRA_EPISODE_NUMBER = "torrent_episode_number"
        /** Movie ID extra. */
        const val EXTRA_MOVIE_ID = "torrent_movie_id"
        /** Next episode information passed along for quality selection upon completion. */
        const val EXTRA_NEXT_EPISODE_INFO = "extra_next_episode_info"
        /** Stop serving a torrent after the player closes. */
        const val ACTION_STOP_STREAM = "com.martinrevert.latorrentola.action.STOP_STREAM"
        /** Private application files subdirectory for completed and partial transfers. */
        const val TORRENT_DIRECTORY = "torrent_downloads"
        /** Notification channel identifier. */
        const val NOTIFICATION_CHANNEL_ID = "torrent_downloads"
        /** Foreground progress notification identifier. */
        const val NOTIFICATION_ID = 2401
        /** RFC 4648 alphabet used by magnet BTIH Base32 hashes. */
        const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        /** Log tag used for native torrent failures. */
        const val TAG = "TorrentDownloadService"
        /** Locally queued transfer state. */
        const val STATE_QUEUED = "QUEUED"
        /** Active transfer state. */
        const val STATE_DOWNLOADING = "DOWNLOADING"
        /** Torrent metadata is available and the video buffer is being acquired. */
        const val STATE_BUFFERING = "BUFFERING"
        /** At least the contiguous startup buffer has verified. */
        const val STATE_READY = "READY"
        /** User-paused transfer state. */
        const val STATE_PAUSED = "PAUSED"
        /** Fully downloaded and verified state. */
        const val STATE_COMPLETED = "COMPLETED"
        /** Transfer failed and is available for an explicit retry. */
        const val STATE_FAILED = "FAILED"
        /** Minimum verified startup buffer before playback may be offered (5 MB). */
        const val STARTUP_BUFFER_BYTES = 5L * 1024L * 1024L
    }
}
