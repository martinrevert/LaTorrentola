package com.martinrevert.latorrentola.service.storage

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Lifecycle events for external and removable storage devices (USB drives, SD cards).
 */
sealed interface StorageEvent {
    data class Mounted(val path: String?) : StorageEvent
    data class Unmounted(val path: String?) : StorageEvent
}

/**
 * Event bus emitting storage mount and unmount notifications to subscribers across the app.
 */
object StorageEvents {
    private val _events = MutableSharedFlow<StorageEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<StorageEvent> = _events.asSharedFlow()

    fun notifyMounted(path: String?) {
        _events.tryEmit(StorageEvent.Mounted(path))
    }

    fun notifyUnmounted(path: String?) {
        _events.tryEmit(StorageEvent.Unmounted(path))
    }
}
