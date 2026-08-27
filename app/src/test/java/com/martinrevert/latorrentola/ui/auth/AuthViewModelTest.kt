package com.martinrevert.latorrentola.ui.auth

import android.content.Context
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.network.AuthRepository
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
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
    private val context: Context = mockk()

    @Before
    fun setup() {
        viewModel = AuthViewModel(authRepository)
    }

    @Test
    fun `signInWithGoogle success updates state to Success`() = runTest {
        coEvery { authRepository.signInWithGoogle(any()) } returns Result.success(Unit)

        viewModel.authState.test {
            assertThat(awaitItem()).isEqualTo(AuthState.Idle)
            
            viewModel.signInWithGoogle(context)
            
            assertThat(awaitItem()).isEqualTo(AuthState.Loading)
            assertThat(awaitItem()).isEqualTo(AuthState.Success)
        }
    }

    @Test
    fun `signInWithGoogle failure updates state to Error`() = runTest {
        val errorMessage = "Login failed"
        coEvery { authRepository.signInWithGoogle(any()) } returns Result.failure(Exception(errorMessage))

        viewModel.authState.test {
            assertThat(awaitItem()).isEqualTo(AuthState.Idle)
            
            viewModel.signInWithGoogle(context)
            
            assertThat(awaitItem()).isEqualTo(AuthState.Loading)
            val errorState = awaitItem() as AuthState.Error
            assertThat(errorState.message).isEqualTo(errorMessage)
        }
    }

    @Test
    fun `signOut resets state to Idle`() = runTest {
        viewModel.signOut()
        
        viewModel.authState.test {
            assertThat(awaitItem()).isEqualTo(AuthState.Idle)
        }
    }
}
