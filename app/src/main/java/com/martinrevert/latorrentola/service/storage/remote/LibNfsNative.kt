package com.martinrevert.latorrentola.service.storage.remote

import android.util.Log

/**
 * JNI wrapper for [sahlberg/libnfs], providing native C client integration
 * for NFSv3, NFSv4.0, NFSv4.1, and NFSv4.2 protocol operations.
 */
object LibNfsNative {

    private const val TAG = "LibNfsNative"

    /**
     * Global lock for context-creating discovery and export enumeration calls
     * ([nativeFindLocalServers], [nativeGetExports]) to prevent concurrent mutations
     * of libnfs's static RPC XID salt and overlapping UDP broadcast sockets.
     */
    val discoveryLock = Any()

    val isAvailable: Boolean by lazy {
        try {
            System.loadLibrary("latorrentola_nfs")
            true
        } catch (e: Throwable) {
            try {
                Log.d(TAG, "Native latorrentola_nfs not loaded: ${e.message}")
            } catch (_: Throwable) {}
            false
        }
    }

    /**
     * Thread-safe helper that invokes [nativeFindLocalServers] while holding [discoveryLock].
     */
    fun findLocalServersSynchronized(): Array<String>? =
        synchronized(discoveryLock) {
            nativeFindLocalServers()
        }

    /**
     * Thread-safe helper that invokes [nativeGetExports] while holding [discoveryLock].
     */
    fun getExportsSynchronized(server: String): Array<String>? =
        synchronized(discoveryLock) {
            nativeGetExports(server)
        }

    external fun nativeInitContext(nfsVersion: Int, uid: Int, gid: Int): Long
    external fun nativeMount(ctxPtr: Long, server: String, exportPath: String): Int
    external fun nativeGetExports(server: String): Array<String>?
    external fun nativeListEntries(ctxPtr: Long, dirPath: String): Array<String>?
    external fun nativeOpen(ctxPtr: Long, filePath: String): Long
    external fun nativePread(ctxPtr: Long, fhPtr: Long, offset: Long, length: Int, outBuffer: ByteArray): Int
    external fun nativeClose(ctxPtr: Long, fhPtr: Long)
    external fun nativeDestroyContext(ctxPtr: Long)
    external fun nativeGetErrorMessage(ctxPtr: Long): String?
    external fun nativeStatSize(ctxPtr: Long, filePath: String): Long
    external fun nativeWriteFile(ctxPtr: Long, filePath: String, data: ByteArray): Boolean
    external fun nativeFindLocalServers(): Array<String>?
}
