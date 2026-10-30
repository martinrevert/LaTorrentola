package com.martinrevert.latorrentola.model.TMDB

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a streaming platform/provider returned by TMDB watch provider endpoints.
 *
 * @property providerId TMDB identifier for the watch provider.
 * @property providerName Official display name of the provider.
 * @property logoPath Relative path to the provider logo on TMDB CDN.
 * @property displayPriority Relative sorting priority within the requested region.
 */
@Serializable
data class TmdbWatchProvider(
    @SerializedName("provider_id")
    @SerialName("provider_id")
    val providerId: Int,

    @SerializedName("provider_name")
    @SerialName("provider_name")
    val providerName: String,

    @SerializedName("logo_path")
    @SerialName("logo_path")
    val logoPath: String? = null,

    @SerializedName("display_priority")
    @SerialName("display_priority")
    val displayPriority: Int = 0
) {
    /** High-resolution logo URL served from the TMDB image CDN. */
    val fullLogoUrl: String?
        get() = logoPath?.takeIf(String::isNotBlank)?.let { "https://image.tmdb.org/t/p/w300$it" }
}

/** Response wrapper for TMDB watch providers endpoints. */
@Serializable
data class TmdbWatchProvidersResponse(
    @SerializedName("results")
    @SerialName("results")
    val results: List<TmdbWatchProvider>? = emptyList()
)
