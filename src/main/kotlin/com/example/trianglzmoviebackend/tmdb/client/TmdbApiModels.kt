package com.example.trianglzmoviebackend.tmdb.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

/** Response models for the TMDb v3 endpoints we use. Unknown fields are ignored. */

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class TmdbMoviePage(
    val page: Int,
    val results: List<TmdbMovie> = emptyList(),
    val totalPages: Int = 0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class TmdbMovie(
    val id: Long,
    val title: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    /** TMDb sometimes returns an empty string instead of null. */
    val releaseDate: String? = null,
    val voteAverage: Double? = null,
    val voteCount: Int? = null,
    val popularity: Double? = null,
    val genreIds: List<Int> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbGenreList(val genres: List<TmdbGenre> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbGenre(val id: Int, val name: String)
