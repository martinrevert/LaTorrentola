package com.martinrevert.latorrentola.ui.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.ui.home.MovieList
import com.martinrevert.latorrentola.ui.home.MovieItem
import com.martinrevert.latorrentola.ui.home.QualityChips
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.isTvDevice
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    initialGenre: String? = null,
    onMovieClick: (Movie) -> Unit,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedQuality by viewModel.selectedQuality.collectAsState()
    val lastClickedMovieId by viewModel.lastClickedMovieId.collectAsState()
    val downloadedMovieIds by viewModel.downloadedMovieIds.collectAsState()
    val selectedFavoriteIds by viewModel.selectedFavoriteIds.collectAsState()
    val qualityOptions = viewModel.qualityOptions
    var searchQuery by remember { mutableStateOf("") }
    var isShowingFavorites by remember(initialGenre) { mutableStateOf(initialGenre == "milista") }
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val focusRequester = remember { FocusRequester() }
    val textFieldFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val gridState = rememberLazyGridState()

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
        viewModel.clearSelection()
        if (initialGenre == "milista") {
            viewModel.showFavorites()
        } else if (initialGenre != null) {
            viewModel.searchByGenre(initialGenre)
        } else {
            viewModel.resetSearch()
        }
        
        if (isTv && initialGenre != "milista") {
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore if not attached
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isShowingFavorites) {
                        if (selectedFavoriteIds.isNotEmpty()) {
                            Text("${selectedFavoriteIds.size} seleccionados")
                        } else {
                            Text("My Favorites")
                        }
                    } else {
                        if (isTv) {
                            Surface(
                                onClick = { textFieldFocusRequester.requestFocus() },
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = Border(
                                        border = androidx.compose.foundation.BorderStroke(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary
                                        )
                                    )
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                            ) {
                                SearchTextField(
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = {
                                        searchQuery = it
                                        if (it.length > 2) viewModel.search(it)
                                    },
                                    onVoiceSearchClick = {
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
                                    },
                                    modifier = Modifier
                                        .focusRequester(textFieldFocusRequester)
                                        .onPreviewKeyEvent { event ->
                                            if (event.key == Key.Back || event.key == Key.Escape) {
                                                if (event.type == KeyEventType.KeyUp) {
                                                    focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Exit)
                                                }
                                                true
                                            } else false
                                        }
                                )
                            }
                        } else {
                            SearchTextField(
                                searchQuery = searchQuery,
                                onSearchQueryChange = {
                                    searchQuery = it
                                    if (it.length > 2) viewModel.search(it)
                                },
                                onVoiceSearchClick = {
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
                                }
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (isShowingFavorites && selectedFavoriteIds.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearSelection() },
                            modifier = Modifier.focusHighlight(shape = CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    } else {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.focusHighlight(shape = CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (isShowingFavorites && selectedFavoriteIds.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.deleteSelectedFavorites() },
                            modifier = Modifier.focusHighlight(shape = CircleShape)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (!isShowingFavorites) {
                QualityChips(
                    options = qualityOptions,
                    selectedQuality = selectedQuality ?: "All",
                    onQualityClick = { viewModel.setQuality(it) }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (val state = uiState) {
                        is SearchUiState.Idle -> {
                            Text(text = "Start searching...")
                        }
                        is SearchUiState.Loading -> {
                            CircularProgressIndicator()
                        }
                        is SearchUiState.Success -> {
                            MovieList(
                                movies = state.movies,
                                state = gridState,
                                downloadedMovieIds = downloadedMovieIds,
                                selectedIds = selectedFavoriteIds,
                                onMovieClick = {
                                    if (selectedFavoriteIds.isNotEmpty()) {
                                        viewModel.toggleFavoriteSelection(it.id)
                                    } else {
                                        viewModel.setLastClickedMovieId(it.id)
                                        onMovieClick(it)
                                    }
                                },
                                onLongClick = if (state.isFavorites) { id -> viewModel.toggleFavoriteSelection(id) } else null,
                                onLoadMore = { if (!state.isFavorites) viewModel.loadMore() },
                                onToggleSelection = null, // Logic moved to onMovieClick for standard feel
                                initialFocusId = lastClickedMovieId,
                                onFocusRestored = { viewModel.clearLastClickedMovieId() }
                            )
                        }
                        is SearchUiState.Empty -> {
                            Text(text = "No results found")
                        }
                        is SearchUiState.Error -> {
                            Text(text = state.message)
                        }
                    }
                }
        }
    }
}

@Composable
private fun SearchTextField(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onVoiceSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = { Text("Search movies...") },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        trailingIcon = {
            IconButton(
                onClick = onVoiceSearchClick,
                modifier = Modifier.focusHighlight(shape = CircleShape)
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Voice Search")
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
        )
    )
}
