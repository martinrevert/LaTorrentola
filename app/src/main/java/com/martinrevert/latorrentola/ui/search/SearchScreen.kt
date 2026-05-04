package com.martinrevert.latorrentola.ui.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.ui.home.MovieList
import com.martinrevert.latorrentola.ui.home.MovieItem
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    initialGenre: String? = null,
    onMovieClick: (Movie) -> Unit,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isShowingFavorites by remember(initialGenre) { mutableStateOf(initialGenre == "milista") }
    val context = LocalContext.current

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            spokenText?.let {
                searchQuery = it
                viewModel.search(it)
            }
        }
    }

    LaunchedEffect(initialGenre) {
        if (initialGenre == "milista") {
            viewModel.showFavorites()
        } else if (initialGenre != null) {
            viewModel.searchByGenre(initialGenre)
        } else {
            viewModel.resetSearch()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isShowingFavorites) {
                        Text("My Favorites")
                    } else {
                        TextField(
                            value = searchQuery,
                            onValueChange = { 
                                searchQuery = it
                                if (it.length > 2) viewModel.search(it)
                            },
                            placeholder = { Text("Search movies...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search movies")
                                    }
                                    try {
                                        speechLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        // Handle case where speech recognition is not available
                                    }
                                }) {
                                    Icon(Icons.Default.Mic, contentDescription = "Voice Search")
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Favorites toggle removed from here as per user request
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (val state = uiState) {
                is SearchUiState.Idle -> {
                    Text(text = "Start searching...", modifier = Modifier.align(Alignment.Center))
                }
                is SearchUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is SearchUiState.Success -> {
                    val gridState = rememberLazyStaggeredGridState()
                    MovieList(
                        movies = state.movies,
                        state = gridState,
                        onMovieClick = onMovieClick,
                        onLoadMore = { if (!state.isFavorites) viewModel.loadMore() },
                        onDeleteClick = if (state.isFavorites) { movie -> viewModel.removeFavorite(movie) } else null
                    )
                }
                is SearchUiState.Empty -> {
                    Text(text = "No results found", modifier = Modifier.align(Alignment.Center))
                }
                is SearchUiState.Error -> {
                    Text(text = state.message, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}
