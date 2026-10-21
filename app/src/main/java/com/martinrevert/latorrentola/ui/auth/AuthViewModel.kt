package com.martinrevert.latorrentola.ui.auth

import android.content.Context
import android.util.Log
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.network.AuthRepository
import com.martinrevert.latorrentola.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Coordinates authentication state and post-login preference synchronization.
 *
 * @property authRepository performs authentication operations.
 * @property userLibraryRepository loads cloud-saved account settings.
 * @property preferenceManager persists the synchronized local preferences.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userLibraryRepository: com.martinrevert.latorrentola.network.UserLibraryRepository,
    private val preferenceManager: com.martinrevert.latorrentola.utils.PreferenceManager
) : ViewModel() {

    /** Mutable backing state for authentication progress and outcomes. */
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    /** Observable authentication progress and outcome. */
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /** Currently authenticated Firebase account, if signed in. */
    val currentUser: FirebaseUser? get() = authRepository.currentUser

    /** Signs in with Google, syncs the user's language filter, and publishes the outcome. */
    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signInWithGoogle(context)
            if (result.isSuccess) {
                // Sync settings from Firestore after login bounded by 3s timeout so it never stalls login completion
                try {
                    withTimeoutOrNull(3000L) {
                        val remoteFiltered = userLibraryRepository.getRemoteFilteredLanguages()
                        if (remoteFiltered != null) {
                            preferenceManager.setFilteredLanguages(remoteFiltered)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AuthViewModel", "Failed syncing remote settings post-login: ${e.message}")
                }
                _authState.value = AuthState.Success
            } else {
                val exception = result.exceptionOrNull()
                if (exception is GetCredentialCancellationException ||
                    exception?.message?.contains("cancelled", ignoreCase = true) == true) {
                    _authState.value = AuthState.Idle
                } else {
                    val error = exception?.message?.takeIf { it.isNotBlank() }
                        ?.let { UiText.DynamicString(it) }
                        ?: UiText.StringResource(R.string.unknown_error)
                    _authState.value = AuthState.Error(error)
                }
            }
        }
    }

    /** Signs out and resets the view-model authentication state. */
    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _authState.value = AuthState.Idle
        }
    }
}

/** Authentication operation states exposed to the login UI. */
sealed interface AuthState {
    /** No authentication operation is currently active. */
    object Idle : AuthState
    /** An authentication operation is in progress. */
    object Loading : AuthState
    /** Authentication completed successfully. */
    object Success : AuthState
    /**
     * Authentication failed with a displayable message.
     *
     * @property message User-facing error content.
     */
    data class Error(val message: UiText) : AuthState
}
