package com.martinrevert.latorrentola.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.TMDB.TmdbActorDetail
import com.martinrevert.latorrentola.model.TMDB.TmdbCastCredit
import com.martinrevert.latorrentola.utils.isTvDevice
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.hazeGlass

/**
 * Displays actor biography and filmography in an adaptive modal sheet.
 *
 * @param actorDetailState Actor profile lookup result, when available.
 * @param isLoading Whether the profile request is in progress.
 * @param onDismiss Closes the sheet.
 * @param sheetState Material bottom-sheet state used on handhelds.
 * @param hazeState Optional glass source used behind the handheld sheet.
 * @param onCreditClick Opens the selected TMDB movie or TV credit.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun ActorDetailBottomSheet(
    actorDetailState: Result<TmdbActorDetail>?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    sheetState: SheetState,
    hazeState: HazeState? = null,
    onCreditClick: ((TmdbCastCredit) -> Unit)? = null
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600 || isTv

    val sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh

    val sheetModifier = if (hazeState != null) {
        Modifier.hazeGlass(input = HazeInput.Sources(hazeState))
    } else {
        Modifier
    }

    if (isTv) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .fillMaxHeight(0.92f)
                    .padding(12.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        actorDetailState?.fold(
                            onSuccess = { actor ->
                                TvActorContent(actor = actor, onDismiss = onDismiss, onCreditClick = onCreditClick)
                            },
                            onFailure = {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = stringResource(R.string.no_results),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
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
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = sheetModifier
                .fillMaxWidth()
                .widthIn(max = 1200.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = (configuration.screenHeightDp * 0.9f).dp)
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
                                WideActorContent(actor = actor, onCreditClick = onCreditClick)
                            } else {
                                MobileActorContent(actor = actor, onCreditClick = onCreditClick)
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
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
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
/**
 * Renders actor details using the TV-oriented content layout.
 *
 * @param actor Actor details and combined credits.
 * @param onDismiss Closes the actor details.
 * @param onCreditClick Opens a selected credit.
 */
@Composable
private fun TvActorContent(
    actor: TmdbActorDetail,
    onDismiss: () -> Unit,
    onCreditClick: ((TmdbCastCredit) -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // Left Column: Photo & Personal Metadata
        Column(
            modifier = Modifier
                .weight(0.32f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            ActorPersonalDetails(actor, centered = true, isTv = true)
        }

        // Right Column: Biography & Filmography
        Column(
            modifier = Modifier
                .weight(0.68f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            ActorBiography(actor, isTv = true)

            val credits = actor.combinedCredits?.cast.orEmpty()
            if (credits.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.details_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    modifier = Modifier.focusRestorer(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(credits) { credit ->
                        TvFilmographyItem(credit = credit, onCreditClick = onCreditClick)
                    }
                }
            }
        }
    }
}

/**
 * Displays the available personal details returned for an actor.
 *
 * @param actor Actor profile data.
 * @param centered Whether the details should be centered under the profile.
 * @param isTv Whether to use the larger type scale for TV viewing distance.
 */
@Composable
private fun ActorPersonalDetails(actor: TmdbActorDetail, centered: Boolean, isTv: Boolean = false) {
    val horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        actor.birthday?.takeIf(String::isNotBlank)?.let {
            ActorDetailValue(stringResource(R.string.actor_birthday), it, centered, isTv)
        }
        actor.deathday?.takeIf(String::isNotBlank)?.let {
            ActorDetailValue(stringResource(R.string.actor_deathday), it, centered, isTv)
        }
        actor.placeOfBirth?.takeIf(String::isNotBlank)?.let {
            ActorDetailValue(stringResource(R.string.actor_place_of_birth), it, centered, isTv)
        }
        actor.knownForDepartment?.takeIf(String::isNotBlank)?.let {
            ActorDetailValue(stringResource(R.string.actor_known_for), it, centered, isTv)
        }
    }
}

/**
 * Always presents the biography section without truncating the API response.
 *
 * @param actor Actor details returned by TMDB.
 * @param modifier Layout modifier for this section.
 * @param isTv Whether to use the larger type scale for TV viewing distance.
 */
@Composable
private fun ActorBiography(
    actor: TmdbActorDetail,
    modifier: Modifier = Modifier,
    isTv: Boolean = false
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.summary_header),
            style = if (isTv) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = actor.biography?.takeIf(String::isNotBlank)
                ?: stringResource(R.string.no_summary),
            style = if (isTv) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Displays one labeled actor attribute.
 *
 * @param label Localized detail name.
 * @param value API-provided detail value.
 * @param centered Whether the text should be centered.
 * @param isTv Whether to use the larger type scale for TV viewing distance.
 */
@Composable
private fun ActorDetailValue(
    label: String,
    value: String,
    centered: Boolean,
    isTv: Boolean
) {
    Text(
        text = stringResource(R.string.actor_detail_value, label, value),
        style = if (isTv) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
/**
 * Renders one filmography entry with TV focus treatment.
 *
 * @param credit TMDB movie or TV credit.
 * @param onCreditClick Opens the selected credit.
 */
@Composable
private fun TvFilmographyItem(
    credit: TmdbCastCredit,
    onCreditClick: ((TmdbCastCredit) -> Unit)? = null
) {
    val configuration = LocalConfiguration.current
    val posterWidth = (configuration.screenWidthDp * 0.14f).dp.coerceIn(150.dp, 220.dp)

    Surface(
        onClick = { onCreditClick?.invoke(credit) },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        modifier = Modifier.width(posterWidth)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = credit.fullPosterUrl,
                contentDescription = credit.displayTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.67f)
                    .clip(MaterialTheme.shapes.small),
                placeholder = painterResource(R.drawable.ic_launcher_foreground),
                error = painterResource(R.drawable.ic_launcher_foreground),
                fallback = painterResource(R.drawable.ic_launcher_foreground),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = credit.displayTitle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            FilmographyMetadata(credit, isTv = true)
            if (!credit.character.isNullOrEmpty()) {
                Text(
                    text = credit.character,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Renders actor details in a wide-screen two-column layout.
 *
 * @param actor Actor details and combined credits.
 * @param onCreditClick Opens a selected credit.
 */
@Composable
private fun WideActorContent(
    actor: TmdbActorDetail,
    onCreditClick: ((TmdbCastCredit) -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(0.32f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            ActorPersonalDetails(actor, centered = true)
        }

        Column(
            modifier = Modifier
                .weight(0.68f)
                .verticalScroll(rememberScrollState())
        ) {
            ActorBiography(actor)

            val credits = actor.combinedCredits?.cast.orEmpty()
            if (credits.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.details_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(credits) { credit ->
                        FilmographyItem(credit = credit, onCreditClick = onCreditClick)
                    }
                }
            }
        }
    }
}

/**
 * Renders actor details in the compact handheld layout.
 *
 * @param actor Actor details and combined credits.
 * @param onCreditClick Opens a selected credit.
 */
@Composable
private fun MobileActorContent(
    actor: TmdbActorDetail,
    onCreditClick: ((TmdbCastCredit) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = actor.fullProfileUrl,
                contentDescription = actor.name,
                placeholder = painterResource(R.drawable.ic_launcher_foreground),
                error = painterResource(R.drawable.ic_launcher_foreground),
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = actor.name.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ActorPersonalDetails(actor, centered = false)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ActorBiography(actor, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(20.dp))

        val credits = actor.combinedCredits?.cast.orEmpty()
        if (credits.isNotEmpty()) {
            Text(
                text = stringResource(R.string.details_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(credits) { credit ->
                    FilmographyItem(credit = credit, onCreditClick = onCreditClick)
                }
            }
        }
    }
}

/**
 * Renders one poster and title in the handheld filmography list.
 *
 * @param credit TMDB movie or TV credit.
 * @param onCreditClick Opens the selected credit.
 */
@Composable
private fun FilmographyItem(
    credit: TmdbCastCredit,
    onCreditClick: ((TmdbCastCredit) -> Unit)? = null
) {
    Card(
        onClick = { onCreditClick?.invoke(credit) },
        modifier = Modifier.width(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
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
                placeholder = painterResource(R.drawable.ic_launcher_foreground),
                error = painterResource(R.drawable.ic_launcher_foreground),
                fallback = painterResource(R.drawable.ic_launcher_foreground),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = credit.displayTitle,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            FilmographyMetadata(credit)
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

/**
 * Displays the release year and media kind for one filmography credit.
 *
 * @param credit TMDB movie or TV credit.
 * @param isTv Whether to use the larger type scale for TV viewing distance.
 */
@Composable
private fun FilmographyMetadata(credit: TmdbCastCredit, isTv: Boolean = false) {
    val type = when (credit.mediaType) {
        "movie" -> stringResource(R.string.filmography_type_movie)
        "tv" -> stringResource(R.string.filmography_type_tv)
        else -> null
    }
    val year = credit.displayYear
    val metadata = when {
        year.isNotBlank() && type != null ->
            stringResource(R.string.filmography_metadata, year, type)
        type != null -> type
        else -> year
    }
    if (metadata.isNotBlank()) {
        Text(
            text = metadata,
            style = if (isTv) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
