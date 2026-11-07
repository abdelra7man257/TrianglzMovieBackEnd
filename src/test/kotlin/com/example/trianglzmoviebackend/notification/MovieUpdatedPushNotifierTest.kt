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
    fun `payload keeps the four basic fields and adds the changed field names`() {
        val payload = payloadFactory.create(event)

        assertEquals("MOVIE_UPDATED", payload["type"])
        assertEquals("550", payload["movie_id"])
        assertEquals("Fight Club (Edited)", payload["title"])
        assertEquals("2026-11-06T19:10:00Z", payload["updated_at"])
        assertEquals("title,vote_average", payload["updated_fields"])
    }

    @Test
    fun `payload carries the complete updated movie in the same json as the API`() {
        val movieJson = jsonMapper.readTree(payloadFactory.create(event).getValue("movie"))

        assertEquals(550, movieJson["id"].asInt())
        assertEquals("Fight Club (Edited)", movieJson["title"].asString())
        assertEquals("New synopsis", movieJson["overview"].asString())
        assertEquals("/p.jpg", movieJson["poster_path"].asString())
        assertEquals("/b.jpg", movieJson["backdrop_path"].asString())
        assertEquals("1999-10-15", movieJson["release_date"].asString())
        assertEquals(9.1, movieJson["vote_average"].asDouble())
        assertEquals(27_000, movieJson["vote_count"].asInt())
        assertEquals(61.4, movieJson["popularity"].asDouble())
        assertEquals("""["Drama","Thriller"]""", movieJson["genres"].toString())
        assertEquals("2026-11-06T19:10:00Z", movieJson["updated_at"].asString())
    }

    @Test
    fun `missing optional values stay null inside the movie json`() {
        val sparse = movie.copy(overview = null, posterPath = null, backdropPath = null, releaseDate = null)
        val movieJson = jsonMapper.readTree(payloadFactory.create(event.copy(movie = sparse)).getValue("movie"))

        assertTrue(movieJson["poster_path"].isNull)
        assertTrue(movieJson["release_date"].isNull)
    }

    @Test
    fun `an oversized movie is left out so the message stays under the FCM limit`() {
        val payload = payloadFactory.create(event.copy(movie = movie.copy(overview = "x".repeat(5000))))

        assertFalse(payload.containsKey("movie"))
        assertEquals("550", payload["movie_id"])
        assertEquals("title,vote_average", payload["updated_fields"])
        assertTrue(payload.entries.sumOf { it.key.toByteArray().size + it.value.toByteArray().size } < 4096)
    }

    @Test
    fun `a long but allowed overview is still sent in full`() {
        val payload = payloadFactory.create(event.copy(movie = movie.copy(overview = "y".repeat(2000))))

        assertTrue(payload.containsKey("movie"))
        assertTrue(payload.getValue("movie").contains("y".repeat(2000)))
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
