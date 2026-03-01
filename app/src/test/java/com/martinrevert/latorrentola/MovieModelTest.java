package com.martinrevert.latorrentola;

import static com.google.common.truth.Truth.assertThat;

import com.martinrevert.latorrentola.model.YTS.Movie;

import org.junit.Test;

public class MovieModelTest {

    @Test
    public void movie_creation_isCorrect() {
        Movie movie = new Movie();
        movie.setId(123);
        movie.setTitle("Inception");
        movie.setYear(2010);
        movie.setRating("8.8");

        assertThat(movie.getId()).isEqualTo(123);
        assertThat(movie.getTitle()).isEqualTo("Inception");
        assertThat(movie.getYear()).isEqualTo(2010);
        assertThat(movie.getRating()).isEqualTo("8.8");
    }
}
