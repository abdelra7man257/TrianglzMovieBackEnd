package com.example.trianglzmoviebackend.notification

import com.example.trianglzmoviebackend.movie.MovieUpdatedEvent
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * Builds the FCM data payload for an edited movie. FCM requires every value to be a string.
 *
 * Keys: `type`, `movie_id`, `title`, `updated_at`, `updated_fields` (comma-separated names of the
 * changed fields) and `movie` (the complete updated movie as the same JSON the API returns).
 * FCM limits a data message to 4 KB, so when the message would be too large `movie` is left out
 * and the app re-fetches the movie by id.
 */
@Component
class MovieUpdatePushPayloadFactory(private val jsonMapper: JsonMapper) {

    fun create(event: MovieUpdatedEvent): Map<String, String> {
        val summary = mapOf(
            TYPE_KEY to MOVIE_UPDATED_TYPE,
            MOVIE_ID_KEY to event.movieId.toString(),
            TITLE_KEY to event.movie.title,
            UPDATED_AT_KEY to event.movie.updatedAt.toString(),
            UPDATED_FIELDS_KEY to event.updatedFields.joinToString(",") { it.jsonName },
        )
        val withMovie = summary + (MOVIE_KEY to jsonMapper.writeValueAsString(event.movie))
        return if (sizeInBytes(withMovie) <= MAX_PAYLOAD_BYTES) withMovie else summary
    }

    private fun sizeInBytes(payload: Map<String, String>) =
        payload.entries.sumOf { it.key.toByteArray().size + it.value.toByteArray().size }

    companion object {
        const val TYPE_KEY = "type"
        const val MOVIE_ID_KEY = "movie_id"
        const val TITLE_KEY = "title"
        const val UPDATED_AT_KEY = "updated_at"
        const val UPDATED_FIELDS_KEY = "updated_fields"
        const val MOVIE_KEY = "movie"
        const val MOVIE_UPDATED_TYPE = "MOVIE_UPDATED"

        /** FCM allows 4096 bytes for the data payload; keep a margin. */
        const val MAX_PAYLOAD_BYTES = 3800
    }
}
