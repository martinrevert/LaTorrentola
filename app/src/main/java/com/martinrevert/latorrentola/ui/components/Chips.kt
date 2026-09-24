package com.martinrevert.latorrentola.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.utils.GenreTranslation

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun QualityChips(
    options: List<String>,
    selectedQuality: String,
    onQualityClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .focusRestorer()
            .padding(bottom = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        lazyItems(options) { quality ->
            val isSelected = selectedQuality == quality
            TvChip(
                selected = isSelected,
                onClick = { onQualityClick(quality) },
                label = {
                    Text(
                        text = if (quality == "All") stringResource(R.string.quality_all) else quality
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GenreChips(
    genres: List<String>,
    onGenreClick: (String) -> Unit,
    onAllGenresClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .focusRestorer()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            TvChip(
                selected = false,
                onClick = onAllGenresClick,
                label = { Text(stringResource(R.string.all_genres)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
        item {
            TvChip(
                selected = false,
                onClick = { onGenreClick("nuevas") },
                label = { Text(stringResource(R.string.new_movies)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.NewReleases,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
        item {
            TvChip(
                selected = false,
                onClick = { onGenreClick("ya_vistas") },
                label = { Text(stringResource(R.string.already_seen)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
        lazyItems(genres) { genre ->
            TvChip(
                selected = false,
                onClick = { onGenreClick(genre) },
                label = { Text(GenreTranslation.getGenreText(genre).asString()) }
            )
        }
    }
}
