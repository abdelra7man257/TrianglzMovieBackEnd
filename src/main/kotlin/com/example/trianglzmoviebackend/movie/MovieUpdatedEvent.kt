package com.example.trianglzmoviebackend.movie

/**
 * Published by [MovieService] when a movie really changed; consumed after the transaction commits.
 *
 * @property movie the complete movie after the edit
 * @property updatedFields the fields whose value changed (never empty)
 */
data class MovieUpdatedEvent(
    val movieId: Long,
    val movie: MovieDto,
    val updatedFields: List<MovieEditableField>,
)
