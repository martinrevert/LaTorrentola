package com.martinrevert.latorrentola.model.YTS;

/**
 * Created by martin on 22/11/17.
 */

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Entity
public class Movie {
    @PrimaryKey
    @SerializedName("id")
    @Expose
    private Integer id;
    @ColumnInfo
    @SerializedName("url")
    @Expose
    private String url;
    @ColumnInfo
    @SerializedName("imdb_code")
    @Expose
    private String imdbCode;
    @ColumnInfo
    @SerializedName("title")
    @Expose
    private String title;
    @ColumnInfo
    @SerializedName("title_english")
    @Expose
    private String titleEnglish;
    @ColumnInfo
    @SerializedName("title_long")
    @Expose
    private String titleLong;
    @ColumnInfo
    @SerializedName("slug")
    @Expose
    private String slug;
    @ColumnInfo
    @SerializedName("year")
    @Expose
    private Integer year;
    @ColumnInfo
    @SerializedName("rating")
    @Expose
    private String rating;
    @ColumnInfo
    @SerializedName("runtime")
    @Expose
    private String runtime;
    @ColumnInfo
    @SerializedName("genres")
    @Expose
    private List<String> genres = null;
    @ColumnInfo
    @SerializedName("summary")
    @Expose
    private String summary;
    @ColumnInfo
    @SerializedName("description_full")
    @Expose
    private String descriptionFull;
    @ColumnInfo
    @SerializedName("synopsis")
    @Expose
    private String synopsis;
    @ColumnInfo
    @SerializedName("yt_trailer_code")
    @Expose
    private String ytTrailerCode;
    @ColumnInfo
    @SerializedName("language")
    @Expose
    private String language;
    @ColumnInfo
    @SerializedName("mpa_rating")
    @Expose
    private String mpaRating;
    @ColumnInfo
    @SerializedName("background_image")
    @Expose
    private String backgroundImage;
    @ColumnInfo
    @SerializedName("background_image_original")
    @Expose
    private String backgroundImageOriginal;
    @ColumnInfo
    @SerializedName("small_cover_image")
    @Expose
    private String smallCoverImage;
    @ColumnInfo
    @SerializedName("medium_cover_image")
    @Expose
    private String mediumCoverImage;
    @ColumnInfo
    @SerializedName("large_cover_image")
    @Expose
    private String largeCoverImage;
    @ColumnInfo
    @SerializedName("state")
    @Expose
    private String state;
    @ColumnInfo
    @SerializedName("torrents")
    @Expose
    private List<Torrent> torrents = null;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getImdbCode() {
        return imdbCode;
    }

    public void setImdbCode(String imdbCode) {
        this.imdbCode = imdbCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitleEnglish() {
        return titleEnglish;
    }

    public void setTitleEnglish(String titleEnglish) {
        this.titleEnglish = titleEnglish;
    }

    public String getTitleLong() {
        return titleLong;
    }

    public void setTitleLong(String titleLong) {
        this.titleLong = titleLong;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getRating() {
        return rating;
    }

    public void setRating(String rating) {
        this.rating = rating;
    }

    public String getRuntime() {
        return runtime;
    }

    public void setRuntime(String runtime) {
        this.runtime = runtime;
    }

    public List<String> getGenres() {
        return genres;
    }

    public void setGenres(List<String> genres) {
        this.genres = genres;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDescriptionFull() {
        return descriptionFull;
    }

    public void setDescriptionFull(String descriptionFull) {
        this.descriptionFull = descriptionFull;
    }

    public String getSynopsis() {
        return synopsis;
    }

    public void setSynopsis(String synopsis) {
        this.synopsis = synopsis;
    }

    public String getYtTrailerCode() {
        return ytTrailerCode;
    }

    public void setYtTrailerCode(String ytTrailerCode) {
        this.ytTrailerCode = ytTrailerCode;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getMpaRating() {
        return mpaRating;
    }

    public void setMpaRating(String mpaRating) {
        this.mpaRating = mpaRating;
    }

    public String getBackgroundImage() {
        return backgroundImage;
    }

    public void setBackgroundImage(String backgroundImage) {
        this.backgroundImage = backgroundImage;
    }

    public String getBackgroundImageOriginal() {
        return backgroundImageOriginal;
    }

    public void setBackgroundImageOriginal(String backgroundImageOriginal) {
        this.backgroundImageOriginal = backgroundImageOriginal;
    }

    public String getSmallCoverImage() {
        return smallCoverImage;
    }

    public void setSmallCoverImage(String smallCoverImage) {
        this.smallCoverImage = smallCoverImage;
    }

    public String getMediumCoverImage() {
        return mediumCoverImage;
    }

    public void setMediumCoverImage(String mediumCoverImage) {
        this.mediumCoverImage = mediumCoverImage;
    }

    public String getLargeCoverImage() {
        return largeCoverImage;
    }

    public void setLargeCoverImage(String largeCoverImage) {
        this.largeCoverImage = largeCoverImage;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public List<Torrent> getTorrents() {
        return torrents;
    }

    public void setTorrents(List<Torrent> torrents) {
        this.torrents = torrents;
    }

}
