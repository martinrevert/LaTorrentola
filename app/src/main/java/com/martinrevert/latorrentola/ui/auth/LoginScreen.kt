package com.martinrevert.latorrentola.ui.auth

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ButtonDefaults as TvButtonDefaults
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.SurfaceDefaults as TvSurfaceDefaults
import androidx.tv.material3.Text as TvText
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.UiText
import com.martinrevert.latorrentola.utils.isTvDevice

/** Connects login UI events and authentication outcomes to the auth view model. */
@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    themeMode: Int? = null
) {
    val authState by viewModel.authState.collectAsState()
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
        }
    }

    if (isTv) {
        TvLaTorrentolaTheme(themeMode = effectiveThemeMode) {
            LoginScreenContent(
                authState = authState,
                isTv = true,
                onSignInClick = { viewModel.signInWithGoogle(context) }
            )
        }
    } else {
        LaTorrentolaTheme(themeMode = effectiveThemeMode) {
            LoginScreenContent(
                authState = authState,
                isTv = false,
                onSignInClick = { viewModel.signInWithGoogle(context) }
            )
        }
    }
}

/** Renders login branding and the appropriate loading or Google sign-in control. */
@Composable
private fun LoginScreenContent(
    authState: AuthState,
    isTv: Boolean,
    onSignInClick: () -> Unit
) {
    if (isTv) {
        TvSurface(
            modifier = Modifier.fillMaxSize(),
            colors = TvSurfaceDefaults.colors(
                containerColor = TvMaterialTheme.colorScheme.background
            )
        ) {
            LoginBody(
                authState = authState,
                isTv = true,
                titleColor = TvMaterialTheme.colorScheme.primary,
                onSignInClick = onSignInClick
            )
        }
    } else {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            LoginBody(
                authState = authState,
                isTv = false,
                titleColor = MaterialTheme.colorScheme.primary,
                onSignInClick = onSignInClick
            )
        }
    }
}

@Composable
private fun LoginBody(
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

/** Previews of the idle login screen on mobile (Light & Dark). */
@Preview(name = "Mobile Light", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true, widthDp = 360, heightDp = 640)
@Preview(name = "Mobile Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun LoginScreenMobilePreview() {
    LaTorrentolaTheme {
        LoginScreenContent(
            authState = AuthState.Idle,
            isTv = false,
            onSignInClick = {}
        )
    }
}

/** Previews of the idle login screen on Android TV (Light & Dark). */
@Preview(name = "TV Light", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true, widthDp = 960, heightDp = 540)
@Preview(name = "TV Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, widthDp = 960, heightDp = 540)
@Composable
fun LoginScreenTvPreview() {
    TvLaTorrentolaTheme {
        LoginScreenContent(
            authState = AuthState.Idle,
            isTv = true,
            onSignInClick = {}
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
            isTv = false,
            onSignInClick = {}
        )
    }
}
