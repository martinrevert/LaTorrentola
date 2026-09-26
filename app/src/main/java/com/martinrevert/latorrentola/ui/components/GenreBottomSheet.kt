package com.martinrevert.latorrentola.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.utils.GenreTranslation
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.hazeGlass

/**
 * Reusable Bottom Sheet for browsing and filtering movies by genre.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun GenreBottomSheet(
    genres: List<String>,
    onGenreClick: (String) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
    hazeState: HazeState? = null
) {
    val context = LocalContext.current
    val sortedGenres = remember(genres) {
        genres.sortedBy { GenreTranslation.getGenreText(it).asString(context) }
    }
    val sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh

    val sheetModifier = if (hazeState != null) {
        Modifier.hazeGlass(input = HazeInput.Sources(hazeState))
    } else {
        Modifier
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetContainerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = sheetModifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.browse_by_genre),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Grid of genres
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sortedGenres.forEach { genre ->
                    AdaptiveChip(
                        selected = false,
                        onClick = { onGenreClick(genre) },
                        label = {
                            Text(
                                text = GenreTranslation.getGenreText(genre).asString(),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
