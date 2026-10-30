package com.martinrevert.latorrentola.ui.search

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.TMDB.TmdbPersonResult
import org.junit.Test

class ActorChipsRowTest {

    private val actor1 = TmdbPersonResult(id = 101, name = "Bryan Cranston")
    private val actor2 = TmdbPersonResult(id = 102, name = "Aaron Paul")
    private val actor3 = TmdbPersonResult(id = 103, name = "Bob Odenkirk")
    private val sampleActors = listOf(actor1, actor2, actor3)

    @Test
    fun `buildActorChipItems places Bio chip immediately before first selected actor`() {
        val items = buildActorChipItems(
            actors = sampleActors,
            selectedActor = actor1,
            hasBiography = true,
        )

        assertThat(items).hasSize(4)
        assertThat(items[0]).isEqualTo(ActorChipItem.Bio)
        assertThat(items[1]).isEqualTo(ActorChipItem.Actor(actor1))
        assertThat(items[2]).isEqualTo(ActorChipItem.Actor(actor2))
        assertThat(items[3]).isEqualTo(ActorChipItem.Actor(actor3))
    }

    @Test
    fun `buildActorChipItems moves Bio chip immediately before middle selected actor`() {
        val items = buildActorChipItems(
            actors = sampleActors,
            selectedActor = actor2,
            hasBiography = true
        )

        assertThat(items).hasSize(4)
        assertThat(items[0]).isEqualTo(ActorChipItem.Actor(actor1))
        assertThat(items[1]).isEqualTo(ActorChipItem.Bio)
        assertThat(items[2]).isEqualTo(ActorChipItem.Actor(actor2))
        assertThat(items[3]).isEqualTo(ActorChipItem.Actor(actor3))
    }

    @Test
    fun `buildActorChipItems moves Bio chip immediately before last selected actor`() {
        val items = buildActorChipItems(
            actors = sampleActors,
            selectedActor = actor3,
            hasBiography = true
        )

        assertThat(items).hasSize(4)
        assertThat(items[0]).isEqualTo(ActorChipItem.Actor(actor1))
        assertThat(items[1]).isEqualTo(ActorChipItem.Actor(actor2))
        assertThat(items[2]).isEqualTo(ActorChipItem.Bio)
        assertThat(items[3]).isEqualTo(ActorChipItem.Actor(actor3))
    }

    @Test
    fun `buildActorChipItems omits Bio chip when hasBiography is false`() {
        val items = buildActorChipItems(
            actors = sampleActors,
            selectedActor = actor2,
            hasBiography = false
        )

        assertThat(items).hasSize(3)
        assertThat(items).containsExactly(
            ActorChipItem.Actor(actor1),
            ActorChipItem.Actor(actor2),
            ActorChipItem.Actor(actor3)
        ).inOrder()
    }

    @Test
    fun `buildActorChipItems prepends Bio chip when selectedActor is not in list`() {
        val unknownActor = TmdbPersonResult(id = 999, name = "Unknown")
        val items = buildActorChipItems(
            actors = sampleActors,
            selectedActor = unknownActor,
            hasBiography = true
        )

        assertThat(items).hasSize(4)
        assertThat(items[0]).isEqualTo(ActorChipItem.Bio)
        assertThat(items.drop(1)).containsExactly(
            ActorChipItem.Actor(actor1),
            ActorChipItem.Actor(actor2),
            ActorChipItem.Actor(actor3)
        ).inOrder()
    }

    @Test
    fun `buildActorChipItems returns empty list when actors is empty`() {
        val unknownActor = TmdbPersonResult(id = 999, name = "Unknown")
        val items = buildActorChipItems(
            actors = emptyList(),
            selectedActor = unknownActor,
            hasBiography = true
        )

        assertThat(items).isEmpty()
    }
}
