package com.example.trianglzmoviebackend.notification

import com.example.trianglzmoviebackend.config.AppProperties
import com.example.trianglzmoviebackend.movie.MovieUpdatedEvent
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Sends a data-only FCM message to the movie-updates topic after an edit is committed.
 * Without Firebase credentials it only logs. A push failure never fails the edit.
 */
@Component
class MovieUpdatedPushNotifier(
    firebaseMessagingProvider: ObjectProvider<FirebaseMessaging>,
    properties: AppProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val firebaseMessaging: FirebaseMessaging? = firebaseMessagingProvider.getIfAvailable()
    private val topic = properties.firebase.topic

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onMovieUpdated(event: MovieUpdatedEvent) {
        val payload = MovieUpdatePushPayload.from(event)
        if (firebaseMessaging == null) {
            log.info("FCM log-only mode (no credentials): would send {} to topic '{}'", payload, topic)
            return
        }
        try {
            val message = Message.builder()
                .setTopic(topic)
                .putAllData(payload)
                .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
                .build()
            val messageId = firebaseMessaging.send(message)
            log.info("FCM sent for movie {} to topic '{}' (id {})", event.movieId, topic, messageId)
        } catch (e: Exception) {
            log.error("FCM send failed for movie {}", event.movieId, e)
        }
    }
}
