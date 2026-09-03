package com.martinrevert.latorrentola.utils

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.martinrevert.latorrentola.R

sealed class UiText {
    data class DynamicString(val value: String) : UiText()
    class StringResource(
        @StringRes val resId: Int,
        vararg val args: Any
    ) : UiText()

    @Composable
    fun asString(): String {
        return when (this) {
            is DynamicString -> value
            is StringResource -> stringResource(resId, *args)
        }
    }

    fun asString(context: Context): String {
        return when (this) {
            is DynamicString -> value
            is StringResource -> context.getString(resId, *args)
        }
    }
}

object GenreTranslation {
    fun getGenreText(genre: String): UiText {
        val resId = when (genre.lowercase()) {
            "action" -> R.string.genre_action
            "adventure" -> R.string.genre_adventure
            "animation" -> R.string.genre_animation
            "biography" -> R.string.genre_biography
            "comedy" -> R.string.genre_comedy
            "crime" -> R.string.genre_crime
            "documentary" -> R.string.genre_documentary
            "drama" -> R.string.genre_drama
            "family" -> R.string.genre_family
            "fantasy" -> R.string.genre_fantasy
            "film-noir" -> R.string.genre_film_noir
            "history" -> R.string.genre_history
            "horror" -> R.string.genre_horror
            "music" -> R.string.genre_music
            "musical" -> R.string.genre_musical
            "mystery" -> R.string.genre_mystery
            "romance" -> R.string.genre_romance
            "sci-fi" -> R.string.genre_sci_fi
            "short" -> R.string.genre_short
            "sport" -> R.string.genre_sport
            "thriller" -> R.string.genre_thriller
            "war" -> R.string.genre_war
            "western" -> R.string.genre_western
            else -> null
        }
        return if (resId != null) UiText.StringResource(resId) else UiText.DynamicString(genre)
    }
}
