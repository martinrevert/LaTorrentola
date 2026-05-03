package com.martinrevert.latorrentola

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.martinrevert.latorrentola.ui.navigation.AppNavigation
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var movieJsonToOpen by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleIntent(intent)

        enableEdgeToEdge()
        setContent {
            LaTorrentolaTheme {
                AppNavigation(initialMovieJson = movieJsonToOpen)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra("PELI")?.let {
            movieJsonToOpen = it
        }
    }
}
