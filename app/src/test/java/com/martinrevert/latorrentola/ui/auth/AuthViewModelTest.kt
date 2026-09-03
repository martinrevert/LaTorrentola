package com.martinrevert.latorrentola.ui.auth

import android.content.Context
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.google.firebase.auth.FirebaseUser
import com.martinrevert.latorrentola.network.AuthRepository
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import com.martinrevert.latorrentola.utils.PreferenceManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
            assertThat((finalState as AuthState.Error).message).isEqualTo(errorMessage)
        }
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
}
