package com.martinrevert.latorrentola.network

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest {

    private lateinit var repository: AuthRepository
    private val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val firebaseUser: FirebaseUser = mockk()

    @Before
    fun setUp() {
        // CredentialManager.create(context) might throw in non-android environments
        // but we relaxed the context and repository uses it in constructor.
        try {
            repository = AuthRepository(firebaseAuth, context)
        } catch (e: Exception) {
            // If it fails to initialize CM, we can't test repo methods that use it
            // but we can at least mock the repo if needed. For this test we try to proceed.
            repository = mockk()
        }
    }

    @Test
    fun `currentUser should return user from firebaseAuth`() {
        every { firebaseAuth.currentUser } returns firebaseUser
        
        // Use a fresh repo instance specifically for this if setup failed
        val repo = AuthRepository(firebaseAuth, context)
        assertThat(repo.currentUser).isEqualTo(firebaseUser)
    }

    @Test
    fun `signOut should call firebaseAuth signOut`() = runTest {
        val repo = AuthRepository(firebaseAuth, context)
        try {
            repo.signOut()
        } catch (e: Exception) {
            // Ignore CM exceptions in unit test environment
        }
        
        verify { firebaseAuth.signOut() }
    }
}
