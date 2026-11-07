package com.example.trianglzmoviebackend.movie

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import java.time.Instant
import java.time.LocalDate

/** snake_case to match TMDb (set explicitly so it also holds in test slices). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class MovieDto(
    val id: Long,
    val title: String,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: LocalDate?,
    val voteAverage: Double,
    val voteCount: Int,
    val popularity: Double,
    val genres: List<String>,
    val updatedAt: Instant,
)
