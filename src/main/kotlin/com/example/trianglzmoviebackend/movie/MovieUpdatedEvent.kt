package com.example.trianglzmoviebackend.movie

import java.time.Instant

/** Published by [MovieService] when a movie was edited; consumed after the transaction commits. */
data class MovieUpdatedEvent(
    val movieId: Long,
    val title: String,
    val updatedAt: Instant,
)
