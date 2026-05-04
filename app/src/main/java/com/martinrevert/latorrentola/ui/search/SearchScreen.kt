package com.martinrevert.latorrentola.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.ui.home.MovieList
import com.martinrevert.latorrentola.ui.home.MovieItem

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
