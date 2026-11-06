package com.example.trianglzmoviebackend.notification

import com.example.trianglzmoviebackend.config.AppProperties
import com.example.trianglzmoviebackend.movie.MovieUpdatedEvent
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.time.Instant
import kotlin.test.assertEquals

class MovieUpdatedPushNotifierTest {
    private val event = MovieUpdatedEvent(550, "Fight Club (Edited)", Instant.parse("2026-11-06T19:10:00Z"))
    private val properties = AppProperties(firebase = AppProperties.Firebase(topic = "movie-updates"))

    private fun notifier(messaging: FirebaseMessaging?): MovieUpdatedPushNotifier {
        val provider = mockk<ObjectProvider<FirebaseMessaging>>()
        every { provider.getIfAvailable() } returns messaging
        return MovieUpdatedPushNotifier(provider, properties)
    }

    @Test
    fun `payload carries type, id, title and timestamp as strings`() {
        val payload = MovieUpdatePushPayload.from(event)

        assertEquals(
            mapOf(
                "type" to "MOVIE_UPDATED",
                "movie_id" to "550",
                "title" to "Fight Club (Edited)",
                "updated_at" to "2026-11-06T19:10:00Z",
            ),
            payload,
        )
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
