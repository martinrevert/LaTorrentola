package com.martinrevert.latorrentola.model.TMDB

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TmdbActorTest {

    @Test
    fun `isDirectorCredit correctly identifies director and series director roles`() {
        val directorMovie = TmdbCrewCredit(id = 1, job = "Director", department = "Directing")
        val seriesDirector = TmdbCrewCredit(id = 2, job = "Series Director", department = "Directing")
        val coDirector = TmdbCrewCredit(id = 3, job = "Co-Director", department = "Directing")

        assertThat(directorMovie.isDirectorCredit()).isTrue()
        assertThat(seriesDirector.isDirectorCredit()).isTrue()
        assertThat(coDirector.isDirectorCredit()).isTrue()
    }

    @Test
    fun `isDirectorCredit rejects assistant casting and non-director directing roles`() {
        val assistant = TmdbCrewCredit(id = 1, job = "Assistant Director", department = "Directing")
        val secondUnit = TmdbCrewCredit(id = 2, job = "Second Unit Director", department = "Directing")
        val casting = TmdbCrewCredit(id = 3, job = "Casting Director", department = "Production")
        val art = TmdbCrewCredit(id = 4, job = "Art Director", department = "Art")
        val writer = TmdbCrewCredit(id = 5, job = "Writer", department = "Writing")
        val scriptSupervisor = TmdbCrewCredit(id = 6, job = "Script Supervisor", department = "Directing")
        val blankJob = TmdbCrewCredit(id = 7, job = "", department = "Directing")

        assertThat(assistant.isDirectorCredit()).isFalse()
        assertThat(secondUnit.isDirectorCredit()).isFalse()
        assertThat(casting.isDirectorCredit()).isFalse()
        assertThat(art.isDirectorCredit()).isFalse()
        assertThat(writer.isDirectorCredit()).isFalse()
        assertThat(scriptSupervisor.isDirectorCredit()).isFalse()
        assertThat(blankJob.isDirectorCredit()).isFalse()
    }

    @Test
    fun `combineActingAndDirectingCredits merges pure director filmography`() {
        val nolanCredits = TmdbCreditsResponse(
            cast = emptyList(),
            crew = listOf(
                TmdbCrewCredit(id = 1, title = "Memento", releaseDate = "2000-10-11", job = "Director", mediaType = "movie"),
                TmdbCrewCredit(id = 2, title = "Inception", releaseDate = "2010-07-16", job = "Director", mediaType = "movie"),
                TmdbCrewCredit(id = 3, title = "Oppenheimer", releaseDate = "2023-07-21", job = "Director", mediaType = "movie"),
                TmdbCrewCredit(id = 4, title = "Assistant Work", releaseDate = "1997-01-01", job = "Assistant Director", mediaType = "movie")
            )
        )

        val combined = nolanCredits.combineActingAndDirectingCredits()

        assertThat(combined.map { it.id }).containsExactly(3, 2, 1).inOrder()
        assertThat(combined.map { it.character }).containsExactly("Director", "Director", "Director").inOrder()
    }

    @Test
    fun `combineActingAndDirectingCredits merges dual director and actor appearances without duplicate cards`() {
        val tarantinoCredits = TmdbCreditsResponse(
            cast = listOf(
                TmdbCastCredit(id = 10, title = "Pulp Fiction", releaseDate = "1994-10-14", character = "Jimmie Dimmick", mediaType = "movie"),
                TmdbCastCredit(id = 20, title = "From Dusk Till Dawn", releaseDate = "1996-01-19", character = "Richard Gecko", mediaType = "movie")
            ),
            crew = listOf(
                TmdbCrewCredit(id = 10, title = "Pulp Fiction", releaseDate = "1994-10-14", job = "Director", mediaType = "movie"),
                TmdbCrewCredit(id = 30, title = "Inglourious Basterds", releaseDate = "2009-08-21", job = "Director", mediaType = "movie")
            )
        )

        val combined = tarantinoCredits.combineActingAndDirectingCredits()

        assertThat(combined.map { it.id }).containsExactly(30, 20, 10).inOrder()
        val pulpFiction = combined.first { it.id == 10 }
        assertThat(pulpFiction.character).isEqualTo("Director / Jimmie Dimmick")
        val inglourious = combined.first { it.id == 30 }
        assertThat(inglourious.character).isEqualTo("Director")
        val duskTillDawn = combined.first { it.id == 20 }
        assertThat(duskTillDawn.character).isEqualTo("Richard Gecko")
    }

    @Test
    fun `combineActingAndDirectingCredits does not falsely label multi-role actor as director`() {
        val myersCredits = TmdbCreditsResponse(
            cast = listOf(
                TmdbCastCredit(id = 100, title = "Austin Powers", releaseDate = "1997-05-02", character = "Austin Powers", mediaType = "movie"),
                TmdbCastCredit(id = 100, title = "Austin Powers", releaseDate = "1997-05-02", character = "Dr. Evil", mediaType = "movie")
            ),
            crew = emptyList()
        )

        val combined = myersCredits.combineActingAndDirectingCredits()

        assertThat(combined).hasSize(1)
        val credit = combined.first()
        assertThat(credit.id).isEqualTo(100)
        assertThat(credit.character).doesNotContain("Director")
        assertThat(credit.character).isEqualTo("Austin Powers")
    }
}
