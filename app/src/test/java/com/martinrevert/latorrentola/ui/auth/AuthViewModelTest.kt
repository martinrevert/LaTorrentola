package com.martinrevert.latorrentola.ui.auth

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.google.firebase.auth.FirebaseUser
import com.martinrevert.latorrentola.database.SavedAccountEntity
import com.martinrevert.latorrentola.network.AuthRepository
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: AuthViewModel
    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val userLibraryRepository: UserLibraryRepository = mockk(relaxed = true)
    private val preferenceManager: PreferenceManager = mockk(relaxed = true)
    private val context: Context = mockk()

    @Before
    fun setup() {
        viewModel = AuthViewModel(authRepository, userLibraryRepository, preferenceManager)
    }

    @Test
    fun `signInWithGoogle success updates state to Success`() = runTest {
        coEvery { authRepository.signInWithGoogle(any()) } returns Result.success(Unit)

        viewModel.authState.test {
            assertThat(awaitItem()).isEqualTo(AuthState.Idle)
            
            viewModel.signInWithGoogle(context)
            
            // In some environments, Loading might be emitted and collected very fast or skipped
            // depending on the dispatcher. We expect Loading then Success.
            val nextState = awaitItem()
            if (nextState is AuthState.Loading) {
                assertThat(awaitItem()).isEqualTo(AuthState.Success)
            } else {
                assertThat(nextState).isEqualTo(AuthState.Success)
            }
        }
    }

    @Test
    fun `signInWithGoogle failure updates state to Error`() = runTest {
        val errorMessage = "Login failed"
        coEvery { authRepository.signInWithGoogle(any()) } returns Result.failure(Exception(errorMessage))

        viewModel.authState.test {
            assertThat(awaitItem()).isEqualTo(AuthState.Idle)
            
            viewModel.signInWithGoogle(context)
            
            val nextState = awaitItem()
            val finalState = if (nextState is AuthState.Loading) awaitItem() else nextState
            
            assertThat(finalState).isInstanceOf(AuthState.Error::class.java)
            assertThat((finalState as AuthState.Error).message)
                .isEqualTo(UiText.DynamicString(errorMessage))
        }
    }

    @Test
    fun `signInWithGoogle cancellation resets state to Idle`() = runTest {
        val cancellationMessage = "activity is cancelled by the user."
        coEvery { authRepository.signInWithGoogle(any()) } returns Result.failure(
            GetCredentialCancellationException(cancellationMessage)
        )

        viewModel.signInWithGoogle(context)

        assertThat(viewModel.authState.value).isEqualTo(AuthState.Idle)
    }

    @Test
    fun `signOut resets state to Idle`() = runTest {
        viewModel.signOut()
        
        viewModel.authState.test {
            assertThat(awaitItem()).isEqualTo(AuthState.Idle)
        }
        coVerify { authRepository.signOut() }
    }

    @Test
    fun `getCurrentUser should return user from repository`() {
        val mockUser = mockk<FirebaseUser>()
        every { authRepository.currentUser } returns mockUser
        
        assertThat(viewModel.currentUser).isEqualTo(mockUser)
    }

    @Test
    fun `resetAuthState sets state back to Idle`() {
        viewModel.resetAuthState()
        assertThat(viewModel.authState.value).isEqualTo(AuthState.Idle)
    }

    @Test
    fun `currentUserFlow exposes repository authStateFlow emissions`() = runTest {
        val mockUser = mockk<FirebaseUser>()
        val stateFlow = MutableStateFlow<FirebaseUser?>(null)
        every { authRepository.authStateFlow } returns stateFlow

        val testViewModel = AuthViewModel(authRepository, userLibraryRepository, preferenceManager)

        testViewModel.currentUserFlow.test {
            assertThat(awaitItem()).isNull()
            stateFlow.value = mockUser
            assertThat(awaitItem()).isEqualTo(mockUser)
        }
    }

    @Test
    fun `selectAccount when already logged in as that user triggers delta sync and proceeds`() = runTest {
        val mockUser = mockk<FirebaseUser>()
        every { mockUser.uid } returns "user_123"
        every { authRepository.currentUser } returns mockUser
        val account = SavedAccountEntity(uid = "user_123", displayName = "Martin", email = "martin@test.com")
        var proceeded = false

        viewModel.selectAccount(account, context) {
            proceeded = true
        }

        assertThat(proceeded).isTrue()
        assertThat(viewModel.authState.value).isEqualTo(AuthState.Success)
        coVerify { authRepository.touchAccount("user_123") }
    }

    @Test
    fun `selectAccount when logged in as another user triggers signInWithGoogle without touching account prematurely`() = runTest {
        val mockUser = mockk<FirebaseUser>()
        every { mockUser.uid } returns "user_other"
        every { authRepository.currentUser } returns mockUser
        coEvery { authRepository.signInWithGoogle(any()) } returns Result.success(Unit)
        val account = SavedAccountEntity(uid = "user_123", displayName = "Martin", email = "martin@test.com")

        viewModel.selectAccount(account, context) {}

        coVerify(exactly = 0) { authRepository.touchAccount(any()) }
        coVerify { authRepository.signInWithGoogle(context) }
    }

    @Test
    fun `deleteAccount signs out before deleting when deleted user is current user`() = runTest {
        val mockUser = mockk<FirebaseUser>()
        every { mockUser.uid } returns "user_to_delete"
        every { authRepository.currentUser } returns mockUser
        coEvery { authRepository.signOut() } returns Unit

        viewModel.deleteAccount("user_to_delete")

        coVerifyOrder {
            authRepository.signOut()
            authRepository.deleteAccount("user_to_delete")
        }
    }

    @Test
    fun `deleteAccount does not sign out if deleted user is not current user`() = runTest {
        val mockUser = mockk<FirebaseUser>()
        every { mockUser.uid } returns "other_user"
        every { authRepository.currentUser } returns mockUser

        viewModel.deleteAccount("user_to_delete")

        coVerify { authRepository.deleteAccount("user_to_delete") }
        coVerify(exactly = 0) { authRepository.signOut() }
    }

    @Test
    fun `savedAccounts starts as null before repository flow emits to prevent welcome flash`() = runTest {
        val accountsFlow = MutableSharedFlow<List<SavedAccountEntity>>()
        every { authRepository.savedAccountsFlow } returns accountsFlow

        val testViewModel = AuthViewModel(authRepository, userLibraryRepository, preferenceManager)

        testViewModel.savedAccounts.test {
            assertThat(awaitItem()).isNull()

            val savedProfile = SavedAccountEntity(
                uid = "user_123",
                displayName = "Martin",
                email = "martin@test.com"
            )
            accountsFlow.emit(listOf(savedProfile))

            assertThat(awaitItem()).containsExactly(savedProfile)
        }
    }
}
