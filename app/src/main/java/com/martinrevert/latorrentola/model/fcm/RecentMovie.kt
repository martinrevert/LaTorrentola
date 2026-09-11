package com.martinrevert.latorrentola.model.fcm

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class RecentMovie(
    @SerializedName("movieId")
    val movieId: Int,
    @SerializedName("title")
    val title: String? = null,
    @SerializedName("notifiedAt")
    val notifiedAt: String? = null
)
