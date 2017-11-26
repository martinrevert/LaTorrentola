package com.martinrevert.latorrentola.model.YTS;

/**
 * Created by martin on 22/11/17.
 */

import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Data {

    @SerializedName("movie_count")
    @Expose
    private Integer movieCount;
    @SerializedName("limit")
    @Expose
    private Integer limit;
    @SerializedName("page_number")
    @Expose
    private Integer pageNumber;
    @SerializedName("movies")
    @Expose
    private List<Movie> movies = null;
    @SerializedName("@meta")
    @Expose
    private Meta meta;

    public Integer getMovieCount() {
        return movieCount;
    }

    public void setMovieCount(Integer movieCount) {
        this.movieCount = movieCount;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public List<Movie> getMovies() {
        return movies;
    }

    public void setMovies(List<Movie> movies) {
        this.movies = movies;
    }

    public Meta getMeta() {
        return meta;
    }

    public void setMeta(Meta meta) {
        this.meta = meta;
    }

}
