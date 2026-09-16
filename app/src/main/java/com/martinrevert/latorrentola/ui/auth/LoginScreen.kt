package com.martinrevert.latorrentola.ui.auth

import android.widget.Toast
import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.UiText

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit
) {
    val authState by viewModel.authState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            onLoginSuccess()
        } else if (authState is AuthState.Error) {
            Toast.makeText(context, (authState as AuthState.Error).message.asString(context), Toast.LENGTH_LONG).show()
        }
    }

    LoginScreenContent(
        authState = authState,
        onSignInClick = { viewModel.signInWithGoogle(context) }
    )
}

@Composable
private fun LoginScreenContent(
    authState: AuthState,
    onSignInClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
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
                        .clip(MaterialTheme.shapes.medium)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(48.dp))

                if (authState is AuthState.Loading) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = onSignInClick,
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(56.dp)
                            .focusHighlight(shape = ButtonDefaults.shape),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_google_logo),
                            contentDescription = stringResource(R.string.google_logo_desc),
                            tint = Color.Unspecified,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.login_google),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "TV Light", showBackground = true, device = "id:tv_720p", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "TV Dark", showBackground = true, device = "id:tv_720p", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun LoginScreenTvPreview() {
    LaTorrentolaTheme {
        LoginScreenContent(
            authState = AuthState.Idle,
            onSignInClick = {}
        )
    }
}

@PreviewLightDark
@Composable
fun LoginScreenPreview() {
    LaTorrentolaTheme {
        LoginScreenContent(
            authState = AuthState.Idle,
            onSignInClick = {}
        )
    }
}

@PreviewLightDark
@Composable
fun LoginScreenLoadingPreview() {
    LaTorrentolaTheme {
        LoginScreenContent(
            authState = AuthState.Loading,
            onSignInClick = {}
        )
    }
}

