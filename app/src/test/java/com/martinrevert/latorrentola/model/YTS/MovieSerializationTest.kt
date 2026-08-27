package com.martinrevert.latorrentola.model.YTS

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class MovieSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `movie serialization and deserialization should be symmetric`() {
        val movie = Movie(
            id = 123,
            title = "Test Movie",
            year = 2024,
            genres = listOf("Action", "Adventure"),
            torrents = listOf(Torrent(quality = "1080p", hash = "ABC"))
        )

        val movieJson = json.encodeToString(Movie.serializer(), movie)
        val decodedMovie = json.decodeFromString(Movie.serializer(), movieJson)

        assertThat(decodedMovie.id).isEqualTo(movie.id)
        assertThat(decodedMovie.title).isEqualTo(movie.title)
        assertThat(decodedMovie.year).isEqualTo(movie.year)
        assertThat(decodedMovie.genres).containsExactly("Action", "Adventure")
        assertThat(decodedMovie.torrents?.get(0)?.quality).isEqualTo("1080p")
    }

    @Test
    fun `deserializing partial movie json should work`() {
        val partialJson = """{"id": 456, "title": "Partial Movie"}"""
        val movie = json.decodeFromString(Movie.serializer(), partialJson)

        assertThat(movie.id).isEqualTo(456)
        assertThat(movie.title).isEqualTo("Partial Movie")
        assertThat(movie.year).isNull()
    }
}
