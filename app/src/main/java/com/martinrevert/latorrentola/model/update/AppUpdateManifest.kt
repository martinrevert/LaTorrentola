package com.martinrevert.latorrentola.model.update

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

/**
 * Represents the self-hosted release APK manifest served at `manifest.json`.
 *
 * Uses dual serialization (`@Serializable` and `@SerializedName`) to remain resilient
 * under R8 obfuscation and shrinking.
 */
@Serializable
data class AppUpdateManifest(
    @SerializedName("versionCode")
    val versionCode: Int = 0,
    @SerializedName("versionName")
    val versionName: String = "",
    @SerializedName("apkFileName")
    val apkFileName: String = "",
    @SerializedName("apkUrl")
    val apkUrl: String = "",
    @SerializedName("sha256Url")
    val sha256Url: String = "",
    @SerializedName("sha256")
    val sha256: String = "",
    @SerializedName("fileSize")
    val fileSize: Long = 0L,
    @SerializedName("forceUpdate")
    val forceUpdate: Boolean = true,
    @SerializedName("publishedAt")
    val publishedAt: String = ""
)
