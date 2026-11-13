package com.martinrevert.latorrentola.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.utils.GenreTranslation
import com.martinrevert.latorrentola.utils.PreferenceManager

/** Presents a localized, alphabetized genre picker in a modal bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreBottomSheet(
    genres: List<String>,
    onGenreClick: (String) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
    themeMode: Int? = null
) {
    val context = LocalContext.current
    val effectiveThemeMode = remember(context, themeMode) {
        themeMode ?: PreferenceManager(context).getTheme()
    }
    val sortedGenres = remember(genres) {
        genres.sortedBy { GenreTranslation.getGenreText(it).asString(context) }
    }

    LaTorrentolaTheme(themeMode = effectiveThemeMode) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
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
}

@OptIn(ExperimentalMaterial3Api::class)
@com.martinrevert.latorrentola.ui.theme.LightDarkPreviews
@Composable
private fun GenreBottomSheetPreview() {
    com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        GenreBottomSheet(
            genres = listOf("Action", "Adventure", "Animation", "Biography", "Comedy", "Crime", "Documentary", "Drama"),
            onGenreClick = {},
            onDismiss = {},
            sheetState = sheetState
        )
    }
}
