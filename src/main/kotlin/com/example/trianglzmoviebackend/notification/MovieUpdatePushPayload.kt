package com.example.trianglzmoviebackend.notification

import com.example.trianglzmoviebackend.movie.MovieUpdatedEvent

/** FCM data-message payload. FCM requires all values to be strings. */
object MovieUpdatePushPayload {
    const val TYPE_KEY = "type"
    const val MOVIE_ID_KEY = "movie_id"
    const val TITLE_KEY = "title"
    const val UPDATED_AT_KEY = "updated_at"
    const val MOVIE_UPDATED_TYPE = "MOVIE_UPDATED"

    fun from(event: MovieUpdatedEvent): Map<String, String> = mapOf(
        TYPE_KEY to MOVIE_UPDATED_TYPE,
        MOVIE_ID_KEY to event.movieId.toString(),
        TITLE_KEY to event.title,
        UPDATED_AT_KEY to event.updatedAt.toString(),
    )
}
