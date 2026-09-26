package com.martinrevert.latorrentola.model.YTS

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

/**
 * A YTS movie record used by API responses, persistence, and navigation.
 *
 * @property id YTS movie identifier.
 * @property url Canonical movie page URL.
 * @property imdbCode IMDb title identifier, when available.
 * @property title Localized or original movie title from the API.
 * @property titleEnglish English movie title, when provided.
 * @property titleLong Extended title, commonly including release year and quality.
 * @property slug URL-friendly movie title.
 * @property year Release year.
 * @property rating Aggregate rating supplied by YTS.
 * @property runtime Movie duration as supplied by the API.
 * @property genres Movie genre names.
 * @property summary Short movie synopsis.
 * @property descriptionFull Full movie description.
 * @property synopsis Alternative synopsis text.
 * @property ytTrailerCode YouTube trailer video identifier.
 * @property language Original or primary language code.
 * @property mpaRating Motion Picture Association age rating.
 * @property backgroundImage Background artwork URL.
 * @property backgroundImageOriginal Original-resolution background artwork URL.
 * @property smallCoverImage Small poster URL.
 * @property mediumCoverImage Medium poster URL.
 * @property largeCoverImage Large poster URL.
 * @property state Availability state reported by YTS.
 * @property torrents Available torrent encodings and tracker metadata.
 * @property cast Cast members supplied with the movie record.
 * @property dateUploadedUnix Upload time as Unix seconds.
 */
@IgnoreExtraProperties
@Serializable
data class Movie(
    @SerializedName("id")
    val id: Int = 0,
    @SerializedName("url")
    val url: String? = null,
    @SerializedName("imdb_code")
    val imdbCode: String? = null,
    @SerializedName("title")
    val title: String? = null,
    @SerializedName("title_english")
    val titleEnglish: String? = null,
    @SerializedName("title_long")
    val titleLong: String? = null,
    @SerializedName("slug")
    val slug: String? = null,
    @SerializedName("year")
    val year: Int? = null,
    @SerializedName("rating")
    val rating: String? = null,
    @SerializedName("runtime")
    val runtime: String? = null,
    @SerializedName("genres")
    val genres: List<String>? = null,
    @SerializedName("summary")
    val summary: String? = null,
    @SerializedName("description_full")
    val descriptionFull: String? = null,
    @SerializedName("synopsis")
    val synopsis: String? = null,
    @SerializedName("yt_trailer_code")
    val ytTrailerCode: String? = null,
    @SerializedName("language")
    val language: String? = null,
    @SerializedName("mpa_rating")
    val mpaRating: String? = null,
    @SerializedName("background_image")
    val backgroundImage: String? = null,
    @SerializedName("background_image_original")
    val backgroundImageOriginal: String? = null,
    @SerializedName("small_cover_image")
    val smallCoverImage: String? = null,
    @SerializedName("medium_cover_image")
    val mediumCoverImage: String? = null,
    @SerializedName("large_cover_image")
    val largeCoverImage: String? = null,
    @SerializedName("state")
    val state: String? = null,
    @SerializedName("torrents")
    val torrents: List<Torrent>? = null,
    @SerializedName("cast")
    val cast: List<Cast>? = null,
    @SerializedName("date_uploaded_unix")
    val dateUploadedUnix: Long? = null
)
