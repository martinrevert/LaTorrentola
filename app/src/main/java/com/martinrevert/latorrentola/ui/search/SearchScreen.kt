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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.ui.home.MovieList
import com.martinrevert.latorrentola.ui.home.MovieItem
import com.martinrevert.latorrentola.ui.home.QualityChips
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.isTvDevice
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    initialGenre: String? = null,
    initialQuery: String? = null,
    onMovieClick: (Movie) -> Unit,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedQuality by viewModel.selectedQuality.collectAsState()
    val lastClickedMovieId by viewModel.lastClickedMovieId.collectAsState()
    val downloadedMovieIds by viewModel.downloadedMovieIds.collectAsState()
    val selectedFavoriteIds by viewModel.selectedFavoriteIds.collectAsState()
    val qualityOptions = viewModel.qualityOptions
    var searchQuery by remember { mutableStateOf(initialQuery ?: "") }
    var isShowingFavorites by remember(initialGenre) { mutableStateOf(initialGenre == "milista") }
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val focusRequester = remember { FocusRequester() }
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

    LaunchedEffect(initialGenre, initialQuery) {
        viewModel.clearSelection()
        if (initialGenre == "milista") {
            viewModel.showFavorites()
        } else if (initialQuery != null) {
            searchQuery = initialQuery
            viewModel.search(initialQuery)
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
                            Text(stringResource(R.string.selected_count, selectedFavoriteIds.size))
                        } else {
                            Text(stringResource(R.string.my_favorites))
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
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_search_prompt))
                                }
                                try {
                                    speechLauncher.launch(intent)
                                } catch (e: Exception) {
                                    // Handle case where speech recognition is not available
                                }
                            },
                            showVoiceSearch = !isTv,
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .then(if (isTv) Modifier.focusHighlight() else Modifier)
                                .onPreviewKeyEvent { event ->
                                    if (isTv && (event.key == Key.Back || event.key == Key.Escape)) {
                                        if (event.type == KeyEventType.KeyUp) {
                                            focusManager.moveFocus(FocusDirection.Exit)
                                        }
                                        true
                                    } else false
                                }
                        )
                    }
                },
                navigationIcon = {
                    if (isShowingFavorites && selectedFavoriteIds.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearSelection() },
                            modifier = Modifier.focusHighlight(shape = CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_selection_desc))
                        }
                    } else {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.focusHighlight(shape = CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(
                                R.string.back_desc))
                        }
                    }
                },
                actions = {
                    if (isShowingFavorites && selectedFavoriteIds.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.deleteSelectedFavorites() },
                            modifier = Modifier.focusHighlight(shape = CircleShape)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_selected_desc))
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
                            Text(text = stringResource(com.martinrevert.latorrentola.R.string.start_searching))
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
                            Text(text = stringResource(com.martinrevert.latorrentola.R.string.no_results))
                        }
                        is SearchUiState.Error -> {
                            Text(text = state.message.asString())
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
    modifier: Modifier = Modifier,
    showVoiceSearch: Boolean = true
) {
    TextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = { Text(stringResource(com.martinrevert.latorrentola.R.string.search_placeholder)) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        trailingIcon = if (showVoiceSearch) {
            {
                IconButton(
                    onClick = onVoiceSearchClick,
                    modifier = Modifier.focusHighlight(shape = CircleShape)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = stringResource(com.martinrevert.latorrentola.R.string.voice_search_desc))
                }
            }
        } else null,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
        )
    )
}
