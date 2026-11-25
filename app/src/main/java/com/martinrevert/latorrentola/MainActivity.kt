package com.martinrevert.latorrentola

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.martinrevert.latorrentola.utils.AppLogger
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentSanitizer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import com.martinrevert.latorrentola.utils.MediaControllerHelper
import com.google.common.util.concurrent.ListenableFuture
import com.martinrevert.latorrentola.model.update.AppUpdateManifest
import com.martinrevert.latorrentola.network.ApkDownloadState
import com.martinrevert.latorrentola.network.AppUpdateRepository
import com.martinrevert.latorrentola.network.FirebaseMessagingConfig
import com.martinrevert.latorrentola.network.FirebaseMessagingInitializer
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.service.TorrentDownloadService
import com.martinrevert.latorrentola.ui.components.AppUpdateDialog
import com.martinrevert.latorrentola.ui.components.FloatingVideoPipOverlay
import com.martinrevert.latorrentola.ui.navigation.AppNavigation
import com.martinrevert.latorrentola.ui.player.LocalPlayerActivity
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme
import com.martinrevert.latorrentola.utils.PlayerIntentUtils
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.isTvDevice
import dagger.hilt.android.AndroidEntryPoint
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hosts the app UI and converts launch intents into navigation requests.
 *
 * The activity sanitizes external intents before accepting movie or IMDb
 * identifiers and initializes notification permission handling.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    companion object {
        private val IMDB_ID_REGEX = Regex("tt\\d{7,10}", RegexOption.IGNORE_CASE)
    }

    /** Initializes FCM topic subscription after the notification permission flow. */
    @Inject
    lateinit var firebaseMessagingInitializer: FirebaseMessagingInitializer

    /** Supplies the persisted theme selection to the Compose content. */
    @Inject
    lateinit var preferenceManager: PreferenceManager

    /** Coordinates lightweight cross-device watch-history delta sync on foreground resume. */
    @Inject
    lateinit var userLibraryRepository: UserLibraryRepository

    /** Verifies and downloads mandatory release APK updates from the self-hosted server. */
    @Inject
    lateinit var appUpdateRepository: AppUpdateRepository

    private var streamControllerFuture: ListenableFuture<MediaController>? = null

    /** Full movie payload awaiting consumption by [AppNavigation]. */
    private var movieJsonToOpen by mutableStateOf<String?>(null)
    /** Movie identifier awaiting consumption by [AppNavigation]. */
    private var movieIdToOpen by mutableStateOf<Int?>(null)
    /** Search text, such as an IMDb title identifier, awaiting navigation. */
    private var searchQueryToOpen by mutableStateOf<String?>(null)

    /** Non-null when a newer release APK exists on the self-hosted server and requires installation. */
    private var mandatoryUpdateManifest by mutableStateOf<AppUpdateManifest?>(null)
    /** Current progress or verification state of the mandatory APK update download. */
    private var apkDownloadState by mutableStateOf<ApkDownloadState>(ApkDownloadState.Idle)
    /** Whether the OS currently allows this app to request package installations. */
    private var canInstallUnknownApps by mutableStateOf(false)
    /** True while the user is in system settings granting unknown app installation permission. */
    private var awaitingInstallPermissionReturn by mutableStateOf(false)
    private var checkUpdateJob: Job? = null
    private var downloadApkJob: Job? = null

    /** Requests notification permission and updates the default FCM topic subscription. */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("FCM", "Notification permission granted")
            firebaseMessagingInitializer.subscribeToDefaultTopic()
        } else {
            Log.w("FCM", "Notification permission denied")
            firebaseMessagingInitializer.unsubscribeFromDefaultTopic()
        }
    }

    /** Sets up edge-to-edge rendering, launch data, permission handling, and app navigation. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleIntent(intent)
        
        askNotificationPermission()
        canInstallUnknownApps = appUpdateRepository.canRequestPackageInstalls()
        checkForMandatoryUpdateIfNeeded()

        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        setContent {
            val themeMode by preferenceManager.themeFlow.collectAsStateWithLifecycle()
            val isTv = isTvDevice()

            LaunchedEffect(Unit) {
                TorrentDownloadService.readyStreamEvents.collect { intent ->
                    val isTvDevice = isTvDevice()
                    val castEnabled = intent.getBooleanExtra(LocalPlayerActivity.EXTRA_CAST_ENABLED, false)
                    if (isTvDevice || castEnabled) {
                        val launchIntent = Intent(intent).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(launchIntent)
                    } else {
                        MediaControllerHelper.releaseFuture(streamControllerFuture)
                        val controllerFuture = MediaControllerHelper.buildControllerAsync(this@MainActivity)
                        streamControllerFuture = controllerFuture
                        controllerFuture.addListener({
                            try {
                                if (isFinishing || isDestroyed) return@addListener
                                val controller = controllerFuture.get()
                                if (!LocalPlayerActivity.isPlayerActivityForeground()) {
                                    val mediaItem = PlayerIntentUtils.buildMediaItem(intent)
                                    controller.setMediaItem(mediaItem)
                                    controller.prepare()
                                    controller.play()
                                }
                            } catch (_: Exception) {}
                        }, ContextCompat.getMainExecutor(this@MainActivity))
                    }
                }
            }

            val navigationContent: @Composable () -> Unit = {
                Box(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        initialMovieJson = movieJsonToOpen,
                        initialMovieId = movieIdToOpen,
                        initialSearchQuery = searchQueryToOpen,
                        onInitialDataHandled = {
                            movieJsonToOpen = null
                            movieIdToOpen = null
                            searchQueryToOpen = null
                        }
                    )
                    if (!isTv) {
                        FloatingVideoPipOverlay()
                    }
                    mandatoryUpdateManifest?.let { manifest ->
                        AppUpdateDialog(
                            manifest = manifest,
                            downloadState = apkDownloadState,
                            canInstallPackages = canInstallUnknownApps,
                            onStartDownload = { startMandatoryUpdateDownload(manifest) },
                            onInstallApk = { apkFile -> launchApkInstaller(apkFile) },
                            onRequestInstallPermission = { openManageUnknownAppSourcesSettings() },
                            themeMode = themeMode
                        )
                    }
                }
            }

            if (isTv) {
                TvLaTorrentolaTheme(themeMode = themeMode) {
                    navigationContent()
                }
            } else {
                LaTorrentolaTheme(themeMode = themeMode) {
                    navigationContent()
                }
            }
        }
    }

    /** Triggers a throttled watch-history delta sync and re-evaluates mandatory update state when returning to the foreground. */
    override fun onResume() {
        super.onResume()
        canInstallUnknownApps = appUpdateRepository.canRequestPackageInstalls()
        val wasAwaitingPermission = awaitingInstallPermissionReturn
        awaitingInstallPermissionReturn = false
        if (wasAwaitingPermission && canInstallUnknownApps) {
            (apkDownloadState as? ApkDownloadState.ReadyToInstall)?.apkFile?.let { apkFile ->
                launchApkInstaller(apkFile)
            }
        } else if (mandatoryUpdateManifest == null) {
            checkForMandatoryUpdateIfNeeded()
        }
        lifecycleScope.launch(Dispatchers.IO) {
            userLibraryRepository.syncWatchHistoryDelta(force = false)
        }
    }

    /** Queries the self-hosted APK server for a newer release version when running a release build. */
    private fun checkForMandatoryUpdateIfNeeded() {
        if (checkUpdateJob?.isActive == true || mandatoryUpdateManifest != null) return
        checkUpdateJob = lifecycleScope.launch {
            val manifest = appUpdateRepository.checkForMandatoryUpdate()
            if (manifest != null) {
                mandatoryUpdateManifest = manifest
            }
        }
    }

    /** Streams and verifies the mandatory APK update, launching the installer automatically once ready if permitted. */
    private fun startMandatoryUpdateDownload(manifest: AppUpdateManifest) {
        if (downloadApkJob?.isActive == true) return
        downloadApkJob = lifecycleScope.launch {
            appUpdateRepository.downloadAndVerifyApk(manifest).collect { state ->
                apkDownloadState = state
                if (state is ApkDownloadState.ReadyToInstall) {
                    canInstallUnknownApps = appUpdateRepository.canRequestPackageInstalls()
                    if (canInstallUnknownApps) {
                        launchApkInstaller(state.apkFile)
                    }
                }
            }
        }
    }

    /** Opens the system settings screen so the user can authorize unknown app installations for this package. */
    private fun openManageUnknownAppSourcesSettings() {
        try {
            awaitingInstallPermissionReturn = true
            startActivity(appUpdateRepository.createManageUnknownAppSourcesIntent())
        } catch (e: Exception) {
            awaitingInstallPermissionReturn = false
            AppLogger.e("AppUpdate", "Failed to open unknown app sources settings", e)
        }
    }

    /** Launches the native Android package installer for the verified [apkFile]. */
    private fun launchApkInstaller(apkFile: File) {
        try {
            startActivity(appUpdateRepository.createInstallApkIntent(apkFile))
        } catch (e: Exception) {
            AppLogger.e("AppUpdate", "Failed to launch APK installer", e)
        }
    }

    /** Processes a new activity intent without recreating the current UI. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Validates an incoming intent and stores supported movie or IMDb data for navigation.
     *
     * Unsupported or unsafe intent fields are discarded; an intent that cannot be
     * sanitized is ignored.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val sanitizer = IntentSanitizer.Builder()
            .allowComponent(ComponentName(this, MainActivity::class.java))
            .allowAction(Intent.ACTION_MAIN)
            .allowAction(Intent.ACTION_VIEW)
            .allowAction(Intent.ACTION_SEND)
            .allowCategory(Intent.CATEGORY_LAUNCHER)
            .allowCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .allowCategory(Intent.CATEGORY_DEFAULT)
            .allowCategory(Intent.CATEGORY_BROWSABLE)
            .allowExtra(FirebaseMessagingConfig.EXTRA_MOVIE_JSON, String::class.java)
            .allowExtra(FirebaseMessagingConfig.EXTRA_MOVIE_ID, String::class.java)
            .allowExtra(FirebaseMessagingConfig.EXTRA_MOVIE_ID, Int::class.javaObjectType)
            .allowExtra("peli", String::class.java)
            .allowExtra("id", String::class.java)
            .allowExtra("id", Int::class.javaObjectType)
            .allowExtra(Intent.EXTRA_TEXT, String::class.java)
            .allowExtra(Intent.EXTRA_SUBJECT, String::class.java)
            .allowData { uri ->
                uri.host?.contains("imdb.com") == true && uri.path?.startsWith("/title/") == true
            }
            .build()

        val safeIntent = try {
            sanitizer.sanitizeByFiltering(intent)
        } catch (e: Exception) {
            AppLogger.e("Security", "Intent sanitization failed", e)
            return
        }

        safeIntent.let {
            // Check for Movie JSON (full object)
            it.getStringExtra(FirebaseMessagingConfig.EXTRA_MOVIE_JSON)?.let { json ->
                movieJsonToOpen = json
            } ?: it.getStringExtra("peli")?.let { json ->
                movieJsonToOpen = json
            }

            // Check for Movie ID
            val idExtra = it.getStringExtra(FirebaseMessagingConfig.EXTRA_MOVIE_ID)
                ?: it.getStringExtra("id")
                ?: it.getIntExtra(FirebaseMessagingConfig.EXTRA_MOVIE_ID, -1).takeIf { id -> id != -1 }?.toString()
                ?: it.getIntExtra("id", -1).takeIf { id -> id != -1 }?.toString()

            idExtra?.let { idStr ->
                movieIdToOpen = idStr.toIntOrNull()
            }

            // Handle ACTION_SEND (sharing text / link from IMDb app or browser)
            if (it.action == Intent.ACTION_SEND) {
                val sharedText = it.getStringExtra(Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrBlank()) {
                    extractImdbId(sharedText)?.let { imdbId ->
                        searchQueryToOpen = imdbId
                    }
                }
            }

            // Handle Deep Links (URI)
            if (it.action == Intent.ACTION_VIEW) {
                it.data?.let { uri ->
                    if (uri.host?.contains("imdb.com") == true) {
                        val extractedId = extractImdbId(uri.toString()) ?: run {
                            val segments = uri.pathSegments
                            if (segments.size >= 2 && segments[0] == "title") segments[1] else null
                        }
                        extractedId?.let { imdbId ->
                            searchQueryToOpen = imdbId
                        }
                    }
                }
            }
        }
    }

    /** Extracts an IMDb title identifier (`tt1234567`) from a string, if present. */
    private fun extractImdbId(input: String): String? {
        return IMDB_ID_REGEX.find(input)?.value
    }

    /** Requests Android 13+ notification permission when needed, otherwise subscribes to FCM. */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                firebaseMessagingInitializer.subscribeToDefaultTopic()
            }
        } else {
            firebaseMessagingInitializer.subscribeToDefaultTopic()
        }
    }

    override fun onDestroy() {
        MediaControllerHelper.releaseFuture(streamControllerFuture)
        streamControllerFuture = null
        super.onDestroy()
    }
}
