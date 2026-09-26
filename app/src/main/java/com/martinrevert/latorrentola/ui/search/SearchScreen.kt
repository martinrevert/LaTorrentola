package com.martinrevert.latorrentola.ui.search

import android.app.Activity
import android.content.res.Configuration
import android.content.Intent
import android.os.Build
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.ui.components.MovieListPlaceholder
import com.martinrevert.latorrentola.ui.components.MovieList
import com.martinrevert.latorrentola.ui.components.MovieItem
import com.martinrevert.latorrentola.ui.components.QualityChips
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.utils.GenreTranslation
import com.martinrevert.latorrentola.utils.isTvDevice
import com.martinrevert.latorrentola.utils.UiText
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.platform.LocalInspectionMode
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.hazeGlass
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
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val qualityOptions = viewModel.qualityOptions
    var searchQuery by remember { mutableStateOf(initialQuery ?: "") }
    var isShowingFavorites by remember(initialGenre) { mutableStateOf(initialGenre == "milista") }
    var isShowingDownloads by remember(initialGenre) { mutableStateOf(initialGenre == "ya_vistas") }
    var isShowingNew by remember(initialGenre) { mutableStateOf(initialGenre == "nuevas") }
    var isShowingGenre by remember(initialGenre) { 
        mutableStateOf(initialGenre != null && initialGenre != "milista" && initialGenre != "ya_vistas" && initialGenre != "nuevas") 
    }
    val context = LocalContext.current
    val resources = LocalResources.current
    val isTv = remember(context) { context.isTvDevice() }
    val focusRequester = remember { FocusRequester() }

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
        isShowingFavorites = initialGenre == "milista"
        isShowingDownloads = initialGenre == "ya_vistas"
        isShowingNew = initialGenre == "nuevas"
        isShowingGenre = initialGenre != null && initialGenre != "milista" && initialGenre != "ya_vistas" && initialGenre != "nuevas"
        
        if (isShowingFavorites) {
            viewModel.showFavorites()
        } else if (isShowingDownloads) {
            viewModel.showDownloadedMovies()
        } else if (isShowingNew) {
            viewModel.showNewMovies()
        } else if (initialQuery != null) {
            searchQuery = initialQuery
            viewModel.search(initialQuery)
        } else if (initialGenre != null) {
            viewModel.searchByGenre(initialGenre)
        } else {
            viewModel.resetSearch()
        }
        
        if (isTv && !isShowingFavorites && !isShowingDownloads && !isShowingGenre && !isShowingNew) {
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore if not attached
            }
        }
    }

    SearchScreenContent(
        uiState = uiState,
        selectedQuality = selectedQuality,
        lastClickedMovieId = lastClickedMovieId,
        downloadedMovieIds = downloadedMovieIds,
        selectedFavoriteIds = selectedFavoriteIds,
        qualityOptions = qualityOptions,
        searchQuery = searchQuery,
        isShowingFavorites = isShowingFavorites,
        isShowingDownloads = isShowingDownloads,
        isShowingNew = isShowingNew,
        isShowingGenre = isShowingGenre,
        initialGenre = initialGenre,
        isTv = isTv,
        isLoadingMore = isLoadingMore,
        focusRequester = focusRequester,
        onSearchQueryChange = {
            searchQuery = it
            if (it.length > 2) {
                isShowingFavorites = false
                isShowingDownloads = false
                isShowingNew = false
                isShowingGenre = false
                viewModel.search(it)
            }
        },
        onVoiceSearchClick = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, resources.getString(R.string.voice_search_prompt))
            }
            try {
                speechLauncher.launch(intent)
            } catch (e: Exception) {
            }
        },
        onQualityClick = { viewModel.setQuality(it) },
        onMovieClick = {
            if (selectedFavoriteIds.isNotEmpty()) {
                viewModel.toggleFavoriteSelection(it.id)
            } else {
                viewModel.setLastClickedMovieId(it.id)
                onMovieClick(it)
            }
        },
        onLongClick = { viewModel.toggleFavoriteSelection(it) },
        onLoadMore = { viewModel.loadMore() },
        onBackClick = onBackClick,
        onClearSelection = { viewModel.clearSelection() },
        onDeleteSelectedFavorites = { viewModel.deleteSelectedFavorites() },
        onFocusRestored = { viewModel.clearLastClickedMovieId() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreenContent(
    uiState: SearchUiState,
    selectedQuality: String?,
    lastClickedMovieId: Int?,
    downloadedMovieIds: Set<Int>,
    selectedFavoriteIds: Set<Int>,
    qualityOptions: List<String>,
    searchQuery: String,
    isShowingFavorites: Boolean,
    isShowingDownloads: Boolean,
    isShowingNew: Boolean,
    isShowingGenre: Boolean,
    initialGenre: String?,
    isTv: Boolean,
    isLoadingMore: Boolean = false,
    focusRequester: FocusRequester,
    onSearchQueryChange: (String) -> Unit,
    onVoiceSearchClick: () -> Unit,
    onQualityClick: (String) -> Unit,
    onMovieClick: (Movie) -> Unit,
    onLongClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onBackClick: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelectedFavorites: () -> Unit,
    onFocusRestored: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val gridState = rememberLazyGridState()
    val qualityChipsFocusRequester = remember { FocusRequester() }
    val hazeState = if (!isTv) rememberHazeState() else null
    val isInspection = LocalInspectionMode.current
    val isPreAndroid12 = !isInspection && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
    val topBarContainerColor = if (isPreAndroid12) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    } else {
        Color.Transparent
    }

    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 10
        }
    }

    val hazeAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 0.15f else 1f,
        animationSpec = tween(
            durationMillis = 600,
            easing = EaseInOutCubic
        ),
        label = "hazeAlpha"
    )

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .focusProperties { down = qualityChipsFocusRequester }
                    .then(
                        if (hazeState != null) {
                            Modifier
                                .graphicsLayer { alpha = hazeAlpha }
                                .hazeGlass(input = HazeInput.Sources(hazeState))
                        } else Modifier
                    ),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarContainerColor),
                title = {
                    if (isShowingFavorites) {
                        if (selectedFavoriteIds.isNotEmpty()) {
                            Text(stringResource(R.string.selected_count, selectedFavoriteIds.size))
                        } else {
                            Text(stringResource(R.string.my_favorites))
                        }
                    } else if (isShowingDownloads) {
                        Text(stringResource(R.string.already_seen))
                    } else if (isShowingNew) {
                        Text(stringResource(R.string.new_movies))
                    } else if (isShowingGenre && initialGenre != null) {
                        Text(GenreTranslation.getGenreText(initialGenre).asString())
                    } else {
                        SearchTextField(
                            searchQuery = searchQuery,
                            onSearchQueryChange = onSearchQueryChange,
                            onVoiceSearchClick = onVoiceSearchClick,
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
                            onClick = onClearSelection,
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
                            onClick = onDeleteSelectedFavorites,
                            modifier = Modifier.focusHighlight(shape = CircleShape)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_selected_desc))
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (isTv) {
            // TV Layout: Single vertical column for unbroken D-pad focus traversal
            val movieListFocusRequester = remember { FocusRequester() }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                QualityChips(
                    options = qualityOptions,
                    selectedQuality = selectedQuality ?: "All",
                    onQualityClick = onQualityClick,
                    modifier = Modifier
                        .focusRequester(qualityChipsFocusRequester)
                        .focusProperties { down = movieListFocusRequester }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(top = 4.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (val state = uiState) {
                        is SearchUiState.Idle -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = stringResource(R.string.start_searching))
                            }
                        }
                        is SearchUiState.Loading -> {
                            MovieListPlaceholder(contentPadding = PaddingValues(16.dp))
                        }
                        is SearchUiState.Success -> {
                            MovieList(
                                movies = state.movies,
                                state = gridState,
                                downloadedMovieIds = downloadedMovieIds,
                                selectedIds = selectedFavoriteIds,
                                isLoadingMore = isLoadingMore,
                                onMovieClick = onMovieClick,
                                onLongClick = if (state.isFavorites) onLongClick else null,
                                onLoadMore = { if (!state.isFavorites && !state.isDownloads && !state.isNew) onLoadMore() },
                                initialFocusId = lastClickedMovieId,
                                onFocusRestored = onFocusRestored,
                                contentPadding = PaddingValues(16.dp),
                                modifier = Modifier.focusRequester(movieListFocusRequester)
                            )
                        }
                        is SearchUiState.Empty -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = stringResource(R.string.no_results))
                            }
                        }
                        is SearchUiState.Error -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = state.message.asString())
                            }
                        }
                    }
                }
            }
        } else {
            // Handheld Layout: Edge-to-edge with Haze top blur and translucent bottom bar
            val searchContentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 64.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
                start = 16.dp,
                end = 16.dp
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (hazeState != null) Modifier.hazeSource(state = hazeState) else Modifier)
            ) {
                when (val state = uiState) {
                    is SearchUiState.Idle -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = stringResource(R.string.start_searching))
                        }
                    }
                    is SearchUiState.Loading -> {
                        MovieListPlaceholder(contentPadding = searchContentPadding)
                    }
                    is SearchUiState.Success -> {
                        MovieList(
                            movies = state.movies,
                            state = gridState,
                            downloadedMovieIds = downloadedMovieIds,
                            selectedIds = selectedFavoriteIds,
                            isLoadingMore = isLoadingMore,
                            onMovieClick = onMovieClick,
                            onLongClick = if (state.isFavorites) onLongClick else null,
                            onLoadMore = { if (!state.isFavorites && !state.isDownloads && !state.isNew) onLoadMore() },
                            initialFocusId = lastClickedMovieId,
                            onFocusRestored = onFocusRestored,
                            contentPadding = searchContentPadding
                        )
                    }
                    is SearchUiState.Empty -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = stringResource(R.string.no_results))
                        }
                    }
                    is SearchUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = state.message.asString())
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = padding.calculateTopPadding())
                        .then(
                            if (hazeState != null) {
                                Modifier
                                    .graphicsLayer { alpha = hazeAlpha }
                                    .hazeGlass(input = HazeInput.Sources(hazeState))
                            } else Modifier
                        )
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    QualityChips(
                        options = qualityOptions,
                        selectedQuality = selectedQuality ?: "All",
                        onQualityClick = onQualityClick,
                        modifier = Modifier.focusRequester(qualityChipsFocusRequester)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 4.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
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

@Preview(name = "TV Light", showBackground = true, device = "id:tv_720p", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "TV Dark", showBackground = true, device = "id:tv_720p", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SearchScreenTvPreview() {
    val sampleMovies = listOf(
        Movie(id = 1, title = "Inception", year = 2010, rating = "8.8"),
        Movie(id = 2, title = "Interstellar", year = 2014, rating = "8.6")
    )

    LaTorrentolaTheme {
        SearchScreenContent(
            uiState = SearchUiState.Success(sampleMovies),
            selectedQuality = "All",
            lastClickedMovieId = null,
            downloadedMovieIds = emptySet(),
            selectedFavoriteIds = emptySet(),
            qualityOptions = listOf("All", "1080p", "720p"),
            searchQuery = "Inception",
            isShowingFavorites = false,
            isShowingDownloads = false,
            isShowingNew = false,
            isShowingGenre = false,
            initialGenre = null,
            isTv = true,
            focusRequester = remember { FocusRequester() },
            onSearchQueryChange = {},
            onVoiceSearchClick = {},
            onQualityClick = {},
            onMovieClick = {},
            onLongClick = {},
            onLoadMore = {},
            onBackClick = {},
            onClearSelection = {},
            onDeleteSelectedFavorites = {},
            onFocusRestored = {}
        )
    }
}

@PreviewLightDark
@Composable
fun SearchScreenPreview() {
    val sampleMovies = listOf(
        Movie(id = 1, title = "Inception", year = 2010, rating = "8.8"),
        Movie(id = 2, title = "Interstellar", year = 2014, rating = "8.6")
    )

    LaTorrentolaTheme {
        SearchScreenContent(
            uiState = SearchUiState.Success(sampleMovies),
            selectedQuality = "All",
            lastClickedMovieId = null,
            downloadedMovieIds = emptySet(),
            selectedFavoriteIds = emptySet(),
            qualityOptions = listOf("All", "1080p", "720p"),
            searchQuery = "Inception",
            isShowingFavorites = false,
            isShowingDownloads = false,
            isShowingNew = false,
            isShowingGenre = false,
            initialGenre = null,
            isTv = false,
            focusRequester = remember { FocusRequester() },
            onSearchQueryChange = {},
            onVoiceSearchClick = {},
            onQualityClick = {},
            onMovieClick = {},
            onLongClick = {},
            onLoadMore = {},
            onBackClick = {},
            onClearSelection = {},
            onDeleteSelectedFavorites = {},
            onFocusRestored = {}
        )
    }
}
