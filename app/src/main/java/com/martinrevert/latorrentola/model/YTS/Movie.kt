package com.martinrevert.latorrentola.model.YTS

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Entity(tableName = "movies")
@Serializable
data class Movie(
    @PrimaryKey
    @SerializedName("id")
    val id: Int,
    @ColumnInfo(name = "url")
    @SerializedName("url")
    val url: String? = null,
    @ColumnInfo(name = "imdb_code")
    @SerializedName("imdb_code")
    val imdbCode: String? = null,
    @ColumnInfo(name = "title")
    @SerializedName("title")
    val title: String? = null,
    @ColumnInfo(name = "title_english")
    @SerializedName("title_english")
    val titleEnglish: String? = null,
    @ColumnInfo(name = "title_long")
    @SerializedName("title_long")
    val titleLong: String? = null,
    @ColumnInfo(name = "slug")
    @SerializedName("slug")
    val slug: String? = null,
    @ColumnInfo(name = "year")
    @SerializedName("year")
    val year: Int? = null,
    @ColumnInfo(name = "rating")
    @SerializedName("rating")
    val rating: String? = null,
    @ColumnInfo(name = "runtime")
    @SerializedName("runtime")
    val runtime: String? = null,
    @ColumnInfo(name = "genres")
    @SerializedName("genres")
    val genres: List<String>? = null,
    @ColumnInfo(name = "summary")
    @SerializedName("summary")
    val summary: String? = null,
    @ColumnInfo(name = "description_full")
    @SerializedName("description_full")
    val descriptionFull: String? = null,
    @ColumnInfo(name = "synopsis")
    @SerializedName("synopsis")
    val synopsis: String? = null,
    @ColumnInfo(name = "yt_trailer_code")
    @SerializedName("yt_trailer_code")
    val ytTrailerCode: String? = null,
    @ColumnInfo(name = "language")
    @SerializedName("language")
    val language: String? = null,
    @ColumnInfo(name = "mpa_rating")
    @SerializedName("mpa_rating")
    val mpaRating: String? = null,
    @ColumnInfo(name = "background_image")
    @SerializedName("background_image")
    val backgroundImage: String? = null,
    @ColumnInfo(name = "background_image_original")
    @SerializedName("background_image_original")
    val backgroundImageOriginal: String? = null,
    @ColumnInfo(name = "small_cover_image")
    @SerializedName("small_cover_image")
    val smallCoverImage: String? = null,
    @ColumnInfo(name = "medium_cover_image")
    @SerializedName("medium_cover_image")
    val mediumCoverImage: String? = null,
    @ColumnInfo(name = "large_cover_image")
    @SerializedName("large_cover_image")
    val largeCoverImage: String? = null,
    @ColumnInfo(name = "state")
    @SerializedName("state")
    val state: String? = null,
    @ColumnInfo(name = "torrents")
    @SerializedName("torrents")
    val torrents: List<Torrent>? = null
)
