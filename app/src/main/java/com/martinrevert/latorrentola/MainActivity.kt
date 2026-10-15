package com.martinrevert.latorrentola

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.core.content.IntentSanitizer
import com.martinrevert.latorrentola.network.FirebaseMessagingConfig
import com.martinrevert.latorrentola.network.FirebaseMessagingInitializer
import com.martinrevert.latorrentola.ui.navigation.AppNavigation
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.isTvDevice
import dagger.hilt.android.AndroidEntryPoint
import androidx.fragment.app.FragmentActivity
import javax.inject.Inject

/**
 * Hosts the app UI and converts launch intents into navigation requests.
 *
 * The activity sanitizes external intents before accepting movie or IMDb
 * identifiers and initializes notification permission handling.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    /** Initializes FCM topic subscription after the notification permission flow. */
    @Inject
    lateinit var firebaseMessagingInitializer: FirebaseMessagingInitializer

    /** Supplies the persisted theme selection to the Compose content. */
    @Inject
    lateinit var preferenceManager: PreferenceManager

    /** Full movie payload awaiting consumption by [AppNavigation]. */
    private var movieJsonToOpen by mutableStateOf<String?>(null)
    /** Movie identifier awaiting consumption by [AppNavigation]. */
    private var movieIdToOpen by mutableStateOf<Int?>(null)
    /** Search text, such as an IMDb title identifier, awaiting navigation. */
    private var searchQueryToOpen by mutableStateOf<String?>(null)

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

        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        setContent {
            val themeMode by preferenceManager.themeFlow.collectAsState()
            val isTv = isTvDevice()
            val navigationContent: @Composable () -> Unit = {
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
            Log.e("Security", "Intent sanitization failed", e)
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
        val regex = Regex("tt\\d{7,10}", RegexOption.IGNORE_CASE)
        return regex.find(input)?.value
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
}
