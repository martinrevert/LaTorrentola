package com.martinrevert.latorrentola.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavBackStackHandlerTest {

    @Test
    fun `pop with multiple entries removes top entry and does not finish activity`() {
        val backStack = mutableListOf<Route>(Route.Home, Route.Settings)
        var finishCalled = false

        NavBackStackHandler.pop(backStack) {
            finishCalled = true
        }

        assertEquals(1, backStack.size)
        assertEquals(Route.Home, backStack.first())
        assertFalse("onFinish should not be invoked when size > 1", finishCalled)
    }

    @Test
    fun `pop with single entry does not remove entry and invokes onFinish`() {
        val backStack = mutableListOf<Route>(Route.Home)
        var finishCalled = false

        NavBackStackHandler.pop(backStack) {
            finishCalled = true
        }

        assertEquals(1, backStack.size)
        assertEquals(Route.Home, backStack.first())
        assertTrue("onFinish MUST be invoked when size == 1", finishCalled)
    }

    @Test
    fun `pop with empty entry invokes onFinish and stays empty without crashing`() {
        val backStack = mutableListOf<Route>()
        var finishCalled = false

        NavBackStackHandler.pop(backStack) {
            finishCalled = true
        }

        assertEquals(0, backStack.size)
        assertTrue(finishCalled)
    }

    @Test
    fun `resetTo replaces entire backstack with targetRoute without leaving it empty`() {
        val backStack = mutableListOf<Route>(
            Route.Home,
            Route.Detail(movieId = 123),
            Route.Settings
        )

        NavBackStackHandler.resetTo(backStack, Route.Login)

        assertEquals(1, backStack.size)
        assertEquals(Route.Login, backStack.first())
    }

    @Test
    fun `replaceTopDetail replaces top Route Detail with targetRoute`() {
        val backStack = mutableListOf<Route>(Route.Home, Route.Detail(movieId = 99))

        NavBackStackHandler.replaceTopDetail(backStack, Route.TvDetail(seriesId = 55))

        assertEquals(2, backStack.size)
        assertEquals(Route.Home, backStack[0])
        assertEquals(Route.TvDetail(seriesId = 55), backStack[1])
    }

    @Test
    fun `replaceTopDetail on single Route Detail replaces it without ever dropping size to 0`() {
        val backStack = mutableListOf<Route>(Route.Detail(movieId = 99))

        NavBackStackHandler.replaceTopDetail(backStack, Route.TvDetail(seriesId = 55))

        assertEquals(1, backStack.size)
        assertEquals(Route.TvDetail(seriesId = 55), backStack.first())
    }

    @Test
    fun `replaceTopDetail when top is not Route Detail simply adds targetRoute`() {
        val backStack = mutableListOf<Route>(Route.Home, Route.Settings)

        NavBackStackHandler.replaceTopDetail(backStack, Route.TvDetail(seriesId = 77))

        assertEquals(3, backStack.size)
        assertEquals(Route.Home, backStack[0])
        assertEquals(Route.Settings, backStack[1])
        assertEquals(Route.TvDetail(seriesId = 77), backStack[2])
    }

    @Test
    fun `ensureNotEmpty populates empty backstack with fallbackRoute`() {
        val backStack = mutableListOf<Route>()

        NavBackStackHandler.ensureNotEmpty(backStack, Route.Home)

        assertEquals(1, backStack.size)
        assertEquals(Route.Home, backStack.first())
    }

    @Test
    fun `ensureNotEmpty preserves existing backstack`() {
        val backStack = mutableListOf<Route>(Route.Login)

        NavBackStackHandler.ensureNotEmpty(backStack, Route.Home)

        assertEquals(1, backStack.size)
        assertEquals(Route.Login, backStack.first())
    }

    @Test
    fun `findActivity extracts Activity from nested ContextWrappers`() {
        val mockActivity = mockk<Activity>()
        val wrapper1 = mockk<ContextWrapper>()
        val wrapper2 = mockk<ContextWrapper>()
        io.mockk.every { wrapper2.baseContext } returns wrapper1
        io.mockk.every { wrapper1.baseContext } returns mockActivity

        val resolved = wrapper2.findActivity()

        assertEquals(mockActivity, resolved)
    }

    @Test
    fun `findActivity returns null when base context has no Activity`() {
        val mockContext = mockk<Context>()
        val wrapper = mockk<ContextWrapper>()
        io.mockk.every { wrapper.baseContext } returns mockContext

        val resolved = wrapper.findActivity()

        assertNull(resolved)
    }
}
