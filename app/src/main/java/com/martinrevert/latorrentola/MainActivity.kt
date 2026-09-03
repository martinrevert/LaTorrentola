package com.martinrevert.latorrentola

import android.Manifest
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
        intent?.let {
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
                    when {
                        uri.host?.contains("imdb.com") == true -> {
                            // Extract ttID from /title/tt1234567/
                            val segments = uri.pathSegments
                            if (segments.size >= 2 && segments[0] == "title") {
                                searchQueryToOpen = segments[1]
                            }
                        }
                        uri.host?.contains("yts") == true -> {
                            // Extract slug from /movies/movie-slug or /movie/movie-slug
                            val segments = uri.pathSegments
                            if (segments.size >= 2 && (segments[0] == "movies" || segments[0] == "movie")) {
                                searchQueryToOpen = segments[1]
                            }
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
