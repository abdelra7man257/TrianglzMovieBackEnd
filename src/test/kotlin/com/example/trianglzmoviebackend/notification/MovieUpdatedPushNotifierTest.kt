package com.example.trianglzmoviebackend.notification

import com.example.trianglzmoviebackend.config.AppProperties
import com.example.trianglzmoviebackend.movie.MovieDto
import com.example.trianglzmoviebackend.movie.MovieEditableField
import com.example.trianglzmoviebackend.movie.MovieUpdatedEvent
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MovieUpdatedPushNotifierTest {
    private val jsonMapper = JsonMapper.builder().build()
    private val payloadFactory = MovieUpdatePushPayloadFactory(jsonMapper)
    private val properties = AppProperties(firebase = AppProperties.Firebase(topic = "movie-updates"))

    private val movie = MovieDto(
        id = 550, title = "Fight Club (Edited)", overview = "New synopsis", posterPath = "/p.jpg",
        backdropPath = "/b.jpg", releaseDate = LocalDate.of(1999, 10, 15), voteAverage = 9.1, voteCount = 27_000,
        popularity = 61.4, genres = listOf("Drama", "Thriller"), updatedAt = Instant.parse("2026-11-06T19:10:00Z"),
    )
    private val event = MovieUpdatedEvent(
        movieId = 550,
        movie = movie,
        updatedFields = listOf(MovieEditableField.TITLE, MovieEditableField.VOTE_AVERAGE),
    )

    private fun notifier(messaging: FirebaseMessaging?): MovieUpdatedPushNotifier {
        val provider = mockk<ObjectProvider<FirebaseMessaging>>()
        every { provider.getIfAvailable() } returns messaging
        return MovieUpdatedPushNotifier(provider, properties, payloadFactory)
    }

    @Test
    fun `every movie field is its own key with a string value`() {
        val payload = payloadFactory.create(event)

        assertEquals(
            mapOf(
                "type" to "MOVIE_UPDATED",
                "movie_id" to "550",
                "updated_at" to "2026-11-06T19:10:00Z",
                "updated_fields" to "title,vote_average",
                "title" to "Fight Club (Edited)",
                "overview" to "New synopsis",
                "poster_path" to "/p.jpg",
                "backdrop_path" to "/b.jpg",
                "release_date" to "1999-10-15",
                "vote_average" to "9.1",
                "vote_count" to "27000",
                "popularity" to "61.4",
                "genres" to """["Drama","Thriller"]""",
            ),
            payload,
        )
    }

    @Test
    fun `there is no nested movie object`() {
        assertFalse(payloadFactory.create(event).containsKey("movie"))
    }

    @Test
    fun `fields without a value are left out`() {
        val sparse = movie.copy(overview = null, posterPath = null, backdropPath = null, releaseDate = null)

        val payload = payloadFactory.create(event.copy(movie = sparse))

        assertFalse(payload.keys.any { it in setOf("overview", "poster_path", "backdrop_path", "release_date") })
        assertEquals("Fight Club (Edited)", payload["title"])
    }

    @Test
    fun `no genres are sent as an empty json array`() {
        assertEquals("[]", payloadFactory.create(event.copy(movie = movie.copy(genres = emptyList())))["genres"])
    }

    @Test
    fun `numbers never use scientific notation`() {
        val payload = payloadFactory.create(event.copy(movie = movie.copy(voteAverage = 0.00001, popularity = 12345678.5)))

        assertEquals("0.00001", payload["vote_average"])
        assertEquals("12345678.5", payload["popularity"])
    }

    @Test
    fun `a genre containing a comma or quote survives intact`() {
        val payload = payloadFactory.create(event.copy(movie = movie.copy(genres = listOf("Rock, Pop", "\"Odd\""))))

        assertEquals("""["Rock, Pop","\"Odd\""]""", payload["genres"])
    }

    @Test
    fun `an oversized message drops the overview and is marked partial`() {
        val payload = payloadFactory.create(event.copy(movie = movie.copy(overview = "x".repeat(5000))))

        assertFalse(payload.containsKey("overview"))
        assertEquals("true", payload["partial"])
        assertEquals("550", payload["movie_id"])
        assertEquals("Fight Club (Edited)", payload["title"])
        assertTrue(payload.entries.sumOf { it.key.toByteArray().size + it.value.toByteArray().size } < 4096)
    }

    @Test
    fun `a long but allowed overview is sent in full and not marked partial`() {
        val payload = payloadFactory.create(event.copy(movie = movie.copy(overview = "y".repeat(2000))))

        assertEquals("y".repeat(2000), payload["overview"])
        assertFalse(payload.containsKey("partial"))
    }

    @Test
    fun `without credentials it only logs and does not throw`() {
        notifier(messaging = null).onMovieUpdated(event)
    }

    @Test
    fun `with credentials it sends one message`() {
        val messaging = mockk<FirebaseMessaging>()
        every { messaging.send(any<Message>()) } returns "projects/x/messages/1"

        notifier(messaging).onMovieUpdated(event)

        verify(exactly = 1) { messaging.send(any<Message>()) }
    }

    @Test
    fun `a failing send is swallowed so the edit is not affected`() {
        val messaging = mockk<FirebaseMessaging>()
        every { messaging.send(any<Message>()) } throws IllegalStateException("fcm down")

        notifier(messaging).onMovieUpdated(event)

        verify(exactly = 1) { messaging.send(any<Message>()) }
    }
}
