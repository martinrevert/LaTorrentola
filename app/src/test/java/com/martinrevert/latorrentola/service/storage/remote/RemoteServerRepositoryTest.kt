package com.martinrevert.latorrentola.service.storage.remote

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test

class RemoteServerRepositoryTest {

    private val context: Context = mockk(relaxed = true)
    private val prefs: SharedPreferences = mockk(relaxed = true)
    private val editor: SharedPreferences.Editor = mockk(relaxed = true)

    private val memoryPrefs = mutableMapOf<String, String>()

    @Before
    fun setUp() {
        every { context.getSharedPreferences("remote_servers_prefs", Context.MODE_PRIVATE) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            memoryPrefs[firstArg()] = secondArg()
            editor
        }
        every { editor.apply() } just Runs
        every { prefs.getString(any(), any()) } answers {
            memoryPrefs[firstArg()] ?: secondArg()
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `saveServer and getServer stores and retrieves server config correctly`() {
        val repo = RemoteServerRepository(context)

        val config = RemoteServerConfig(
            id = "server-1",
            name = "Living Room NAS",
            type = RemoteServerType.SMB,
            host = "192.168.1.100",
            port = 445,
            shareOrExport = "media",
            username = "user",
            password = "secretPassword"
        )

        repo.saveServer(config)

        val retrieved = repo.getServer("server-1")
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.name).isEqualTo("Living Room NAS")
        assertThat(retrieved?.host).isEqualTo("192.168.1.100")
        assertThat(retrieved?.shareOrExport).isEqualTo("media")
        assertThat(retrieved?.type).isEqualTo(RemoteServerType.SMB)

        val list = repo.getServers()
        assertThat(list).hasSize(1)
        assertThat(list.first().id).isEqualTo("server-1")
    }

    @Test
    fun `deleteServer removes config from repository`() {
        val repo = RemoteServerRepository(context)

        val config = RemoteServerConfig(
            id = "server-del",
            name = "Temporary Share",
            type = RemoteServerType.SMB,
            host = "192.168.1.105",
            shareOrExport = "temp"
        )
        repo.saveServer(config)
        assertThat(repo.getServers()).hasSize(1)

        repo.deleteServer("server-del")
        assertThat(repo.getServers()).isEmpty()
        assertThat(repo.getServer("server-del")).isNull()
    }
}
