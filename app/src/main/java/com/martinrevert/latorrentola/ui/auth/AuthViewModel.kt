package com.martinrevert.latorrentola.ui.auth

import android.content.Context
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
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userLibraryRepository: com.martinrevert.latorrentola.network.UserLibraryRepository,
    private val preferenceManager: com.martinrevert.latorrentola.utils.PreferenceManager
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val currentUser: FirebaseUser? get() = authRepository.currentUser

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signInWithGoogle(context)
            if (result.isSuccess) {
                // Sync settings from Firestore after login
                val remoteFiltered = userLibraryRepository.getRemoteFilteredLanguages()
                if (remoteFiltered != null) {
                    preferenceManager.setFilteredLanguages(remoteFiltered)
                }
                _authState.value = AuthState.Success
            } else {
                val error = result.exceptionOrNull()?.message?.let { UiText.DynamicString(it) }
                    ?: UiText.StringResource(R.string.unknown_error)
                _authState.value = AuthState.Error(error)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _authState.value = AuthState.Idle
        }
    }
}

sealed interface AuthState {
    object Idle : AuthState
    object Loading : AuthState
    object Success : AuthState
    data class Error(val message: UiText) : AuthState
}
