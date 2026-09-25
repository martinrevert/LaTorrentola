package com.martinrevert.latorrentola.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.TMDB.TmdbActorDetail
import com.martinrevert.latorrentola.model.TMDB.TmdbCastCredit
import com.martinrevert.latorrentola.utils.isTvDevice
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.hazeGlass

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun ActorDetailBottomSheet(
    actorDetailState: Result<TmdbActorDetail>?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    sheetState: SheetState,
    hazeState: HazeState? = null,
    onMovieClick: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600 || isTv

    val isInspection = LocalInspectionMode.current
    val isPreAndroid12 = !isInspection && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
    val sheetContainerColor = if (isPreAndroid12) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    }

    val sheetModifier = if (hazeState != null) {
        Modifier.hazeGlass(input = HazeInput.Sources(hazeState))
    } else {
        Modifier
    }

    if (isTv) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.85f)
                    .padding(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        actorDetailState?.fold(
                            onSuccess = { actor ->
                                TvActorContent(actor = actor, onDismiss = onDismiss, onMovieClick = onMovieClick)
                            },
                            onFailure = {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = stringResource(R.string.no_results),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = sheetContainerColor,
            modifier = sheetModifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    actorDetailState?.fold(
                        onSuccess = { actor ->
                            if (isWideScreen) {
                                WideActorContent(actor = actor, onMovieClick = onMovieClick)
                            } else {
                                MobileActorContent(actor = actor, onMovieClick = onMovieClick)
                            }
                        },
                        onFailure = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.no_results),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvActorContent(
    actor: TmdbActorDetail,
    onDismiss: () -> Unit,
    onMovieClick: ((String) -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Left Column: Photo & Personal Metadata
        Column(
            modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = actor.fullProfileUrl,
                contentDescription = actor.name,
                placeholder = painterResource(R.drawable.ic_launcher_foreground),
                error = painterResource(R.drawable.ic_launcher_foreground),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(0.75f)
                    .clip(MaterialTheme.shapes.large),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = actor.name ?: "",
                style = androidx.tv.material3.MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (!actor.birthday.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = actor.birthday,
                    style = androidx.tv.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.tv.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!actor.placeOfBirth.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = actor.placeOfBirth,
                    style = androidx.tv.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.tv.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Right Column: Biography & Filmography
        Column(
            modifier = Modifier
                .weight(0.65f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            if (!actor.biography.isNullOrEmpty()) {
                Text(
                    text = stringResource(R.string.summary_header),
                    style = androidx.tv.material3.MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = actor.biography,
                    style = androidx.tv.material3.MaterialTheme.typography.bodyMedium,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            val credits = actor.combinedCredits?.cast?.filter { !it.posterPath.isNullOrEmpty() }
            if (!credits.isNullOrEmpty()) {
                Text(
                    text = stringResource(R.string.details_title),
                    style = androidx.tv.material3.MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    modifier = Modifier.focusRestorer(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(credits.take(15)) { credit ->
                        TvFilmographyItem(credit = credit, onMovieClick = onMovieClick)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvFilmographyItem(
    credit: TmdbCastCredit,
    onMovieClick: ((String) -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        onClick = { onMovieClick?.invoke(credit.displayTitle) },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
        interactionSource = interactionSource,
        modifier = Modifier
            .width(110.dp)
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.medium
            )
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            AsyncImage(
                model = credit.fullPosterUrl,
                contentDescription = credit.displayTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.67f)
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = credit.displayTitle,
                style = androidx.tv.material3.MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!credit.character.isNullOrEmpty()) {
                Text(
                    text = credit.character,
                    style = androidx.tv.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.tv.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun WideActorContent(
    actor: TmdbActorDetail,
    onMovieClick: ((String) -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(
            modifier = Modifier.weight(0.35f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = actor.fullProfileUrl,
                contentDescription = actor.name,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(0.75f)
                    .clip(MaterialTheme.shapes.large),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = actor.name ?: "",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (!actor.birthday.isNullOrEmpty()) {
                Text(
                    text = actor.birthday,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(0.65f)
                .verticalScroll(rememberScrollState())
        ) {
            if (!actor.biography.isNullOrEmpty()) {
                Text(
                    text = stringResource(R.string.summary_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = actor.biography,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            val credits = actor.combinedCredits?.cast?.filter { !it.posterPath.isNullOrEmpty() }
            if (!credits.isNullOrEmpty()) {
                Text(
                    text = stringResource(R.string.details_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(credits.take(15)) { credit ->
                        FilmographyItem(credit = credit, onMovieClick = onMovieClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileActorContent(
    actor: TmdbActorDetail,
    onMovieClick: ((String) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = actor.fullProfileUrl,
            contentDescription = actor.name,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = actor.name ?: "",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        if (!actor.birthday.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = actor.birthday,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!actor.placeOfBirth.isNullOrEmpty()) {
            Text(
                text = actor.placeOfBirth,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!actor.biography.isNullOrEmpty()) {
            Text(
                text = stringResource(R.string.summary_header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = actor.biography,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        val credits = actor.combinedCredits?.cast?.filter { !it.posterPath.isNullOrEmpty() }
        if (!credits.isNullOrEmpty()) {
            Text(
                text = stringResource(R.string.details_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(credits.take(15)) { credit ->
                    FilmographyItem(credit = credit, onMovieClick = onMovieClick)
                }
            }
        }
    }
}

@Composable
private fun FilmographyItem(
    credit: TmdbCastCredit,
    onMovieClick: ((String) -> Unit)? = null
) {
    Card(
        onClick = { onMovieClick?.invoke(credit.displayTitle) },
        modifier = Modifier.width(100.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            AsyncImage(
                model = credit.fullPosterUrl,
                contentDescription = credit.displayTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.67f)
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = credit.displayTitle,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!credit.character.isNullOrEmpty()) {
                Text(
                    text = credit.character,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
