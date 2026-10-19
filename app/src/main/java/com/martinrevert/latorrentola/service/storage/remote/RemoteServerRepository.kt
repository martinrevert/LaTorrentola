package com.martinrevert.latorrentola.service.storage.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages persistence and updates for configured remote SMB and NFS storage servers.
 */
@Singleton
class RemoteServerRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val gson = Gson()
    private val listType = object : TypeToken<List<RemoteServerConfig>>() {}.type

    private val _serversFlow = MutableStateFlow(loadServers())
    val serversFlow: StateFlow<List<RemoteServerConfig>> = _serversFlow.asStateFlow()

    fun getServers(): List<RemoteServerConfig> = _serversFlow.value

    fun getServer(id: String): RemoteServerConfig? =
        _serversFlow.value.firstOrNull { it.id == id }

    fun saveServer(config: RemoteServerConfig) {
        val current = _serversFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == config.id }
        if (index >= 0) {
            current[index] = config
        } else {
            current.add(config)
        }
        persistServers(current)
    }

    fun deleteServer(id: String) {
        val current = _serversFlow.value.toMutableList()
        if (current.removeAll { it.id == id }) {
            persistServers(current)
        }
    }

    private fun loadServers(): List<RemoteServerConfig> {
        val json = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return try {
            gson.fromJson<List<RemoteServerConfig>>(json, listType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistServers(list: List<RemoteServerConfig>) {
        val json = gson.toJson(list, listType)
        prefs.edit { putString(KEY_SERVERS, json) }
        _serversFlow.value = list
    }

    companion object {
        private const val PREFS_NAME = "remote_servers_prefs"
        private const val KEY_SERVERS = "saved_servers"
    }
}
