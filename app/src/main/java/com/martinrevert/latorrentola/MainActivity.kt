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
import androidx.core.content.ContextCompat
import com.martinrevert.latorrentola.network.FirebaseMessagingConfig
import com.martinrevert.latorrentola.network.FirebaseMessagingInitializer
import com.martinrevert.latorrentola.ui.navigation.AppNavigation
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var firebaseMessagingInitializer: FirebaseMessagingInitializer

    private var movieJsonToOpen by mutableStateOf<String?>(null)
    private var movieIdToOpen by mutableStateOf<Int?>(null)

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
            LaTorrentolaTheme {
                AppNavigation(
                    initialMovieJson = movieJsonToOpen,
                    initialMovieId = movieIdToOpen,
                    onInitialDataHandled = {
                        movieJsonToOpen = null
                        movieIdToOpen = null
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
