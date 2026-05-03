package com.martinrevert.latorrentola.model.YTS

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class Torrent(
    @SerializedName("url")
    val url: String? = null,
    @SerializedName("hash")
    val hash: String? = null,
    @SerializedName("quality")
    val quality: String? = null,
    @SerializedName("seeds")
    val seeds: Int? = null,
    @SerializedName("peers")
    val peers: Int? = null,
    @SerializedName("size")
    val size: String? = null,
    @SerializedName("size_bytes")
    val sizeBytes: String? = null,
    @SerializedName("date_uploaded")
    val dateUploaded: String? = null,
    @SerializedName("date_uploaded_unix")
    val dateUploadedUnix: Int? = null,
    @SerializedName("type")
    val type: String? = null
)
