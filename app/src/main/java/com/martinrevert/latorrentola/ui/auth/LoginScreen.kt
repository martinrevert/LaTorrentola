package com.martinrevert.latorrentola.ui.auth

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import androidx.tv.material3.Border
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ButtonDefaults as TvButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.SurfaceDefaults as TvSurfaceDefaults
import androidx.tv.material3.Text as TvText
import coil3.compose.AsyncImage
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.database.SavedAccountEntity
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.BiometricAuthHelper
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.isTvDevice

/**
 * Connects login UI events, multi-account profile switching, and authentication outcomes to the auth view model.
 */
@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    themeMode: Int? = null
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val savedAccounts by viewModel.savedAccounts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val effectiveThemeMode = remember(context, themeMode) {
        themeMode ?: PreferenceManager(context).getTheme()
    }
    val isTv = remember(context) { context.isTvDevice() }

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            onLoginSuccess()
        } else if (authState is AuthState.Error) {
            Toast.makeText(context, (authState as AuthState.Error).message.asString(context), Toast.LENGTH_LONG).show()
            viewModel.resetAuthState()
        }
    }

    val biometricTitle = stringResource(R.string.biometric_prompt_title)
    val biometricSubtitleTemplate = stringResource(R.string.biometric_prompt_subtitle)

    val onAccountClick: (SavedAccountEntity) -> Unit = { account ->
        val activity = context.findFragmentActivity()
        if ((!isTv) && (activity != null) && BiometricAuthHelper.isBiometricAvailable(context)) {
            val subtitle = String.format(biometricSubtitleTemplate, account.displayName)
            BiometricAuthHelper.authenticate(
                activity = activity,
                title = biometricTitle,
                subtitle = subtitle,
                onSuccess = {
                    viewModel.selectAccount(account, context)
                },
                onError = { errMsg ->
                    Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
                },
                onFallback = {
                    viewModel.signInWithGoogle(context)
                }
            )
        } else {
            viewModel.selectAccount(account, context)
        }
    }

    if (isTv) {
        TvLaTorrentolaTheme(themeMode = effectiveThemeMode) {
            LoginScreenContent(
                authState = authState,
                savedAccounts = savedAccounts,
                isTv = true,
                onAccountClick = onAccountClick,
                onAddProfileClick = { viewModel.signInWithGoogle(context) },
                onDeleteAccount = { uid -> viewModel.deleteAccount(uid) }
            )
        }
    } else {
        LaTorrentolaTheme(themeMode = effectiveThemeMode) {
            LoginScreenContent(
                authState = authState,
                savedAccounts = savedAccounts,
                isTv = false,
                onAccountClick = onAccountClick,
                onAddProfileClick = { viewModel.signInWithGoogle(context) },
                onDeleteAccount = { uid -> viewModel.deleteAccount(uid) }
            )
        }
    }
}

/**
 * Renders either the Disney+ profile selector or the welcome branding screen based on saved accounts.
 * When [savedAccounts] is `null` (initial database read in progress), renders only the themed background
 * surface to prevent a momentary flash of [WelcomeLoginBody].
 */
@Composable
private fun LoginScreenContent(
    authState: AuthState,
    savedAccounts: List<SavedAccountEntity>?,
    isTv: Boolean,
    onAccountClick: (SavedAccountEntity) -> Unit,
    onAddProfileClick: () -> Unit,
    onDeleteAccount: (String) -> Unit
) {
    if (isTv) {
        TvSurface(
            modifier = Modifier.fillMaxSize(),
            colors = TvSurfaceDefaults.colors(
                containerColor = TvMaterialTheme.colorScheme.background
            )
        ) {
            if (savedAccounts != null) {
                if (savedAccounts.isNotEmpty()) {
                    ProfileSelectorBody(
                        authState = authState,
                        savedAccounts = savedAccounts,
                        isTv = true,
                        onAccountClick = onAccountClick,
                        onAddProfileClick = onAddProfileClick,
                        onDeleteAccount = onDeleteAccount
                    )
                } else {
                    WelcomeLoginBody(
                        authState = authState,
                        isTv = true,
                        titleColor = TvMaterialTheme.colorScheme.primary,
                        onSignInClick = onAddProfileClick
                    )
                }
            }
        }
    } else {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (savedAccounts != null) {
                if (savedAccounts.isNotEmpty()) {
                    ProfileSelectorBody(
                        authState = authState,
                        savedAccounts = savedAccounts,
                        isTv = false,
                        onAccountClick = onAccountClick,
                        onAddProfileClick = onAddProfileClick,
                        onDeleteAccount = onDeleteAccount
                    )
                } else {
                    WelcomeLoginBody(
                        authState = authState,
                        isTv = false,
                        titleColor = MaterialTheme.colorScheme.primary,
                        onSignInClick = onAddProfileClick
                    )
                }
            }
        }
    }
}

/**
 * Disney+ OTT styled profile selector screen ("¿Quién anda ahí?").
 */
@Composable
private fun ProfileSelectorBody(
    authState: AuthState,
    savedAccounts: List<SavedAccountEntity>,
    isTv: Boolean,
    onAccountClick: (SavedAccountEntity) -> Unit,
    onAddProfileClick: () -> Unit,
    onDeleteAccount: (String) -> Unit
) {
    val initialFocusRequester = remember { FocusRequester() }
    val editButtonFocusRequester = remember { FocusRequester() }
    var isEditing by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<SavedAccountEntity?>(null) }
    val context = LocalContext.current
    val hasBiometrics = remember(context) { BiometricAuthHelper.isBiometricAvailable(context) }

    LaunchedEffect(isTv, accountToDelete, savedAccounts.size) {
        if (isTv && accountToDelete == null) {
            try {
                initialFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    if (accountToDelete != null) {
        val account = accountToDelete!!
        if (isTv) {
            val cancelFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                try {
                    cancelFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
            Dialog(
                onDismissRequest = { accountToDelete = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TvMaterialTheme.colorScheme.scrim.copy(alpha = 0.85f))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TvSurface(
                        shape = RoundedCornerShape(16.dp),
                        colors = TvSurfaceDefaults.colors(
                            containerColor = TvMaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.widthIn(max = 500.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            TvText(
                                text = stringResource(R.string.delete_profile_confirmation, account.displayName),
                                style = TvMaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TvMaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TvButton(
                                    onClick = { accountToDelete = null },
                                    modifier = Modifier.focusRequester(cancelFocusRequester),
                                    colors = TvButtonDefaults.colors(
                                        containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
                                        focusedContainerColor = TvMaterialTheme.colorScheme.onSurface,
                                        contentColor = TvMaterialTheme.colorScheme.onSurfaceVariant,
                                        focusedContentColor = TvMaterialTheme.colorScheme.inverseOnSurface
                                    )
                                ) {
                                    TvText(stringResource(R.string.cancel))
                                }
                                TvButton(
                                    onClick = {
                                        onDeleteAccount(account.uid)
                                        accountToDelete = null
                                    },
                                    colors = TvButtonDefaults.colors(
                                        containerColor = TvMaterialTheme.colorScheme.errorContainer,
                                        focusedContainerColor = TvMaterialTheme.colorScheme.error,
                                        contentColor = TvMaterialTheme.colorScheme.onErrorContainer,
                                        focusedContentColor = TvMaterialTheme.colorScheme.onError
                                    )
                                ) {
                                    TvText(stringResource(R.string.delete))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = { accountToDelete = null },
                title = {
                    Text(
                        text = stringResource(R.string.delete_profile_confirmation, account.displayName),
                        fontWeight = FontWeight.Bold
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteAccount(account.uid)
                            accountToDelete = null
                        }
                    ) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { accountToDelete = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (authState is AuthState.Loading) {
            CircularProgressIndicator(
                color = if (isTv) TvMaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isTv) 48.dp else 24.dp, vertical = 24.dp)
            ) {
                // Header "¿Quién anda ahí?"
                if (isTv) {
                    TvText(
                        text = stringResource(R.string.who_is_watching),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = TvMaterialTheme.colorScheme.onBackground
                    )
                } else {
                    Text(
                        text = stringResource(R.string.who_is_watching),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.height(if (isTv) 56.dp else 40.dp))

                // Profile avatars list + Add profile button
                LazyRow(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(savedAccounts, key = { _, it -> it.uid }) { index, account ->
                        ProfileAvatarItem(
                            account = account,
                            isTv = isTv,
                            isEditing = isEditing,
                            hasBiometrics = hasBiometrics,
                            focusRequester = if (index == 0) initialFocusRequester else null,
                            onClick = {
                                if (isEditing) {
                                    accountToDelete = account
                                } else {
                                    onAccountClick(account)
                                }
                            }
                        )
                    }

                    item {
                        AddProfileItem(
                            isTv = isTv,
                            focusRequester = if (savedAccounts.isEmpty()) initialFocusRequester else null,
                            onClick = onAddProfileClick
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (isTv) 56.dp else 44.dp))

                // Bottom pill button "EDITAR PERFILES" / "LISTO"
                if (isTv) {
                    TvButton(
                        onClick = { isEditing = !isEditing },
                        shape = TvButtonDefaults.shape(CircleShape),
                        colors = TvButtonDefaults.colors(
                            containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = TvMaterialTheme.colorScheme.onSurface,
                            contentColor = TvMaterialTheme.colorScheme.onSurfaceVariant,
                            focusedContentColor = TvMaterialTheme.colorScheme.inverseOnSurface
                        ),
                        modifier = Modifier
                            .focusRequester(editButtonFocusRequester)
                            .height(44.dp),
                        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 0.dp)
                    ) {
                        TvText(
                            text = stringResource(if (isEditing) R.string.done_editing_profiles else R.string.edit_profiles),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    Button(
                        onClick = { isEditing = !isEditing },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.height(44.dp),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = stringResource(if (isEditing) R.string.done_editing_profiles else R.string.edit_profiles),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/** Individual circular profile avatar item. */
@Composable
private fun ProfileAvatarItem(
    account: SavedAccountEntity,
    isTv: Boolean,
    isEditing: Boolean,
    hasBiometrics: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    val avatarSize = if (isTv) 116.dp else 96.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = if (isTv) 18.dp else 12.dp)
    ) {
        if (isTv) {
            TvSurface(
                onClick = onClick,
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = TvMaterialTheme.colorScheme.surfaceVariant
                ),
                border = ClickableSurfaceDefaults.border(
                    border = Border(BorderStroke(2.dp, TvMaterialTheme.colorScheme.border.copy(alpha = 0.4f))),
                    focusedBorder = Border(BorderStroke(4.dp, TvMaterialTheme.colorScheme.onSurface))
                ),
                modifier = Modifier
                    .size(avatarSize)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (!account.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = account.photoUrl,
                            contentDescription = account.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        Text(
                            text = account.displayName.take(1).uppercase(),
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            color = TvMaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isEditing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(TvMaterialTheme.colorScheme.scrim.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = TvMaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        } else {
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(3.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.85f)),
                modifier = Modifier.size(avatarSize)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (!account.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = account.photoUrl,
                            contentDescription = account.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        Text(
                            text = account.displayName.take(1).uppercase(),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isEditing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isTv) {
            TvText(
                text = account.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TvMaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Text(
                text = account.displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (!isTv && hasBiometrics) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                modifier = Modifier.size(13.dp)
            )
        } else {
            Spacer(modifier = Modifier.size(13.dp))
        }
    }
}

/** Circular "Crear perfil" ("Add profile") item. */
@Composable
private fun AddProfileItem(
    isTv: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    val avatarSize = if (isTv) 116.dp else 96.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = if (isTv) 18.dp else 12.dp)
    ) {
        if (isTv) {
            TvSurface(
                onClick = onClick,
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = TvMaterialTheme.colorScheme.primaryContainer
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(BorderStroke(4.dp, TvMaterialTheme.colorScheme.onSurface))
                ),
                modifier = Modifier
                    .size(avatarSize)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.create_profile),
                        tint = TvMaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        } else {
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.size(avatarSize)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.create_profile),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isTv) {
            TvText(
                text = stringResource(R.string.create_profile),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TvMaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
        } else {
            Text(
                text = stringResource(R.string.create_profile),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Spacer(modifier = Modifier.size(13.dp))
    }
}

/**
 * Standard welcome branding and Google sign-in layout when no profiles are saved.
 */
@Composable
private fun WelcomeLoginBody(
    authState: AuthState,
    isTv: Boolean,
    titleColor: Color,
    onSignInClick: () -> Unit
) {
    val initialFocusRequester = remember { FocusRequester() }
    val buttonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isTv) {
        if (isTv) {
            try {
                initialFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_banner),
                contentDescription = stringResource(R.string.app_banner_desc),
                modifier = Modifier
                    .size(width = 280.dp, height = 157.dp)
                    .clip(if (isTv) TvMaterialTheme.shapes.medium else MaterialTheme.shapes.medium)
                    .then(
                        if (isTv) {
                            Modifier
                                .focusRequester(initialFocusRequester)
                                .focusProperties {
                                    down = buttonFocusRequester
                                    left = buttonFocusRequester
                                    right = buttonFocusRequester
                                    up = FocusRequester.Cancel
                                }
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyUp &&
                                        (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                                    ) {
                                        buttonFocusRequester.requestFocus()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                .focusable()
                        } else {
                            Modifier
                        }
                    )
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isTv) {
                TvText(
                    text = stringResource(R.string.app_name),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
            } else {
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            if (authState is AuthState.Loading) {
                CircularProgressIndicator(
                    color = if (isTv) TvMaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                )
            } else {
                val buttonTextStyle = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(
                        includeFontPadding = false
                    ),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both
                    )
                )

                if (isTv) {
                    TvButton(
                        onClick = onSignInClick,
                        modifier = Modifier
                            .focusRequester(buttonFocusRequester)
                            .focusProperties {
                                up = initialFocusRequester
                                down = FocusRequester.Cancel
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            }
                            .fillMaxWidth(0.8f)
                            .height(56.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = TvButtonDefaults.colors(
                            containerColor = TvMaterialTheme.colorScheme.primary,
                            focusedContainerColor = TvMaterialTheme.colorScheme.primaryContainer,
                            contentColor = TvMaterialTheme.colorScheme.onPrimary,
                            focusedContentColor = TvMaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_google_logo),
                                contentDescription = stringResource(R.string.google_logo_desc),
                                tint = Color.Unspecified,
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.CenterVertically)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            TvText(
                                text = stringResource(R.string.login_google),
                                style = buttonTextStyle,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onSignInClick,
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(56.dp)
                            .focusHighlight(shape = ButtonDefaults.shape),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_google_logo),
                                contentDescription = stringResource(R.string.google_logo_desc),
                                tint = Color.Unspecified,
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.CenterVertically)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.login_google),
                                style = buttonTextStyle,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** Previews of the Disney+ profile selector on mobile. */
@Preview(name = "Mobile Profile Selector", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun LoginScreenMobilePreview() {
    LaTorrentolaTheme {
        LoginScreenContent(
            authState = AuthState.Idle,
            savedAccounts = listOf(
                SavedAccountEntity(uid = "1", displayName = "Martin", email = "martin@test.com", photoUrl = null)
            ),
            isTv = false,
            onAccountClick = {},
            onAddProfileClick = {},
            onDeleteAccount = {}
        )
    }
}

/** Previews of the Disney+ profile selector on TV. */
@Preview(name = "TV Profile Selector", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, widthDp = 960, heightDp = 540)
@Composable
fun LoginScreenTvPreview() {
    TvLaTorrentolaTheme {
        LoginScreenContent(
            authState = AuthState.Idle,
            savedAccounts = listOf(
                SavedAccountEntity(uid = "1", displayName = "Martin", email = "martin@test.com", photoUrl = null)
            ),
            isTv = true,
            onAccountClick = {},
            onAddProfileClick = {},
            onDeleteAccount = {}
        )
    }
}

/** Previews of the welcome screen when no profiles are saved. */
@Preview(name = "Mobile Welcome", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun LoginScreenWelcomePreview() {
    LaTorrentolaTheme {
        LoginScreenContent(
            authState = AuthState.Idle,
            savedAccounts = emptyList(),
            isTv = false,
            onAccountClick = {},
            onAddProfileClick = {},
            onDeleteAccount = {}
        )
    }
}

/** Previews of the login loading state across form factors. */
@Preview(name = "Mobile Loading", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun LoginScreenLoadingPreview() {
    LaTorrentolaTheme {
        LoginScreenContent(
            authState = AuthState.Loading,
            savedAccounts = emptyList(),
            isTv = false,
            onAccountClick = {},
            onAddProfileClick = {},
            onDeleteAccount = {}
        )
    }
}
