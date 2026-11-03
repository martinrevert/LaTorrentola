package com.martinrevert.latorrentola.ui.auth

import android.content.Context
import android.util.Log
import com.martinrevert.latorrentola.utils.AppLogger
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.network.AuthRepository
import com.martinrevert.latorrentola.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

import com.martinrevert.latorrentola.database.SavedAccountEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

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

    /** Observable user authentication state from repository. */
    val currentUserFlow: StateFlow<FirebaseUser?> = authRepository.authStateFlow

    /** Currently authenticated Firebase account, if signed in. */
    val currentUser: FirebaseUser? get() = authRepository.currentUser

    /** Locally saved accounts for OTT / Disney+ multi-account profile switching. */
    val savedAccounts: StateFlow<List<SavedAccountEntity>> =
        authRepository.savedAccountsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Resets the authentication UI state back to [AuthState.Idle]. */
    fun resetAuthState() {
        _authState.value = AuthState.Idle
    }

    /** Signs in with Google, syncs the user's library and settings, and publishes the outcome. */
    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signInWithGoogle(context)
            if (result.isSuccess) {
                // Asynchronously sync cloud library into Room without blocking login response
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        userLibraryRepository.syncFromCloud(forceFull = true)
                        val remoteFiltered = userLibraryRepository.getRemoteFilteredLanguages()
                        if (remoteFiltered != null) {
                            preferenceManager.setFilteredLanguages(remoteFiltered)
                        }
                    } catch (e: Exception) {
                        AppLogger.e("AuthViewModel", "Failed syncing cloud data post-login: ${e.message}", e)
                    }
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

    /**
     * Activates a saved profile. If Firebase session matches, proceeds directly;
     * otherwise initiates Google sign-in.
     */
    fun selectAccount(
        account: SavedAccountEntity,
        context: Context,
        onProceed: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            authRepository.touchAccount(account.uid)
            val current = authRepository.currentUser
            if (current != null && current.uid == account.uid) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        userLibraryRepository.syncFromCloud(forceFull = false)
                    } catch (e: Exception) {
                        AppLogger.w("AuthViewModel", "Delta sync failed on account select: ${e.message}")
                    }
                }
                _authState.value = AuthState.Success
                onProceed?.invoke()
            } else {
                signInWithGoogle(context)
            }
        }
    }

    /** Returns true if the specified UID matches the active Firebase authenticated user. */
    fun isCurrentAccount(uid: String): Boolean = authRepository.currentUser?.uid == uid

    /** Continues to app without authentication in guest mode, signing out of any active account. */
    fun continueAsGuest(onProceed: () -> Unit) {
        _authState.value = AuthState.Idle
        viewModelScope.launch {
            try {
                authRepository.signOut()
            } catch (e: Exception) {
                AppLogger.w("AuthViewModel", "Sign out on continueAsGuest error: ${e.message}")
            }
            _authState.value = AuthState.Idle
            onProceed()
        }
    }

    /** Removes a saved profile from local device, signing out if it was the currently active user. */
    fun deleteAccount(uid: String) {
        viewModelScope.launch {
            val currentUid = authRepository.currentUser?.uid
            authRepository.deleteAccount(uid)
            if (currentUid == uid) {
                try {
                    authRepository.signOut()
                } catch (e: Exception) {
                    AppLogger.w("AuthViewModel", "Sign out on deleteAccount error: ${e.message}")
                }
            }
        }
    }

    /** Signs out and resets the view-model authentication state. */
    fun signOut() {
        _authState.value = AuthState.Idle
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
