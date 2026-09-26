package com.martinrevert.latorrentola.model.YTS

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

/**
 * Torrent metadata for one encoding of a YTS movie.
 *
 * @property url Download URL for the torrent file.
 * @property hash Torrent info hash.
 * @property quality Video quality label.
 * @property seeds Number of reported seeders.
 * @property peers Number of reported peers.
 * @property size Human-readable torrent size.
 * @property sizeBytes Torrent size in bytes as reported by the API.
 * @property dateUploaded Human-readable upload date.
 * @property dateUploadedUnix Upload time as Unix seconds.
 * @property type Torrent encoding type, such as bluray or web.
 */
@IgnoreExtraProperties
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
