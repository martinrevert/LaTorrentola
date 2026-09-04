package com.martinrevert.latorrentola

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import com.martinrevert.latorrentola.utils.PreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var firebaseMessagingInitializer: FirebaseMessagingInitializer

    @Inject
    lateinit var preferenceManager: PreferenceManager

    private var movieJsonToOpen by mutableStateOf<String?>(null)
    private var movieIdToOpen by mutableStateOf<Int?>(null)
    private var searchQueryToOpen by mutableStateOf<String?>(null)

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleIntent(intent)
        
        askNotificationPermission()

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val themeMode by preferenceManager.themeFlow.collectAsState()
            LaTorrentolaTheme(themeMode = themeMode) {
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
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val sanitizer = IntentSanitizer.Builder()
            .allowComponent(ComponentName(this, MainActivity::class.java))
            .allowAction(Intent.ACTION_MAIN)
            .allowAction(Intent.ACTION_VIEW)
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

            // Handle Deep Links (URI)
            if (it.action == Intent.ACTION_VIEW) {
                it.data?.let { uri ->
                    if (uri.host?.contains("imdb.com") == true) {
                        // Extract ttID from /title/tt1234567/
                        val segments = uri.pathSegments
                        if (segments.size >= 2 && segments[0] == "title") {
                            searchQueryToOpen = segments[1]
                        }
                    }
                }
            }
        }
    }

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
