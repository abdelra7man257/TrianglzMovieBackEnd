package com.example.trianglzmoviebackend.notification

import com.example.trianglzmoviebackend.movie.MovieUpdatedEvent
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal

/**
 * Builds the FCM data payload for an edited movie. FCM requires every value to be a string, so each
 * movie field is sent as its own key with a string value (same names as the API):
 *
 * `type`, `movie_id`, `updated_at`, `updated_fields` (comma-separated names of the changed fields),
 * `title`, `overview`, `poster_path`, `backdrop_path`, `release_date` (yyyy-MM-dd), `vote_average`,
 * `vote_count`, `popularity` and `genres` (a JSON array of names, e.g. `["Drama","Thriller"]`).
 *
 * A field that has no value is left out, so a missing key means "no value".
 *
 * FCM limits a data message to 4 KB. If the message would be too large, `overview` is dropped and
 * `partial` is set to `true`; the app should then re-fetch the movie by id.
 */
@Component
class MovieUpdatePushPayloadFactory(private val jsonMapper: JsonMapper) {

    fun create(event: MovieUpdatedEvent): Map<String, String> {
        val movie = event.movie
        val payload = buildMap {
            put(TYPE_KEY, MOVIE_UPDATED_TYPE)
            put(MOVIE_ID_KEY, event.movieId.toString())
            put(UPDATED_AT_KEY, movie.updatedAt.toString())
            put(UPDATED_FIELDS_KEY, event.updatedFields.joinToString(",") { it.jsonName })
            put(TITLE_KEY, movie.title)
            movie.overview?.let { put(OVERVIEW_KEY, it) }
            movie.posterPath?.let { put(POSTER_PATH_KEY, it) }
            movie.backdropPath?.let { put(BACKDROP_PATH_KEY, it) }
            movie.releaseDate?.let { put(RELEASE_DATE_KEY, it.toString()) }
            put(VOTE_AVERAGE_KEY, movie.voteAverage.toPlainString())
            put(VOTE_COUNT_KEY, movie.voteCount.toString())
            put(POPULARITY_KEY, movie.popularity.toPlainString())
            put(GENRES_KEY, jsonMapper.writeValueAsString(movie.genres))
        }
        return if (sizeInBytes(payload) <= MAX_PAYLOAD_BYTES) payload else payload - OVERVIEW_KEY + (PARTIAL_KEY to "true")
    }

    /** No scientific notation (for example 1.0E-5), so the app can always parse the number. */
    private fun Double.toPlainString(): String {
        val text = toString()
        return if ('E' in text) BigDecimal(text).stripTrailingZeros().toPlainString() else text
    }

    private fun sizeInBytes(payload: Map<String, String>) =
        payload.entries.sumOf { it.key.toByteArray().size + it.value.toByteArray().size }

    companion object {
        const val TYPE_KEY = "type"
        const val MOVIE_ID_KEY = "movie_id"
        const val UPDATED_AT_KEY = "updated_at"
        const val UPDATED_FIELDS_KEY = "updated_fields"
        const val TITLE_KEY = "title"
        const val OVERVIEW_KEY = "overview"
        const val POSTER_PATH_KEY = "poster_path"
        const val BACKDROP_PATH_KEY = "backdrop_path"
        const val RELEASE_DATE_KEY = "release_date"
        const val VOTE_AVERAGE_KEY = "vote_average"
        const val VOTE_COUNT_KEY = "vote_count"
        const val POPULARITY_KEY = "popularity"
        const val GENRES_KEY = "genres"
        const val PARTIAL_KEY = "partial"
        const val MOVIE_UPDATED_TYPE = "MOVIE_UPDATED"

        /** FCM allows 4096 bytes for the data payload; keep a margin. */
        const val MAX_PAYLOAD_BYTES = 3800
    }
}
