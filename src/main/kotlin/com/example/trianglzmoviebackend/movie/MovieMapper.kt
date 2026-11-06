package com.example.trianglzmoviebackend.movie

import org.springframework.data.domain.Page

fun Movie.toDto() = MovieDto(
    id = id,
    title = title,
    overview = overview,
    posterPath = posterPath,
    backdropPath = backdropPath,
    releaseDate = releaseDate,
    voteAverage = voteAverage,
    voteCount = voteCount,
    popularity = popularity,
    genres = genres.toList(),
    updatedAt = updatedAt,
)

fun Page<Movie>.toResponse(page: Int) = PagedResponse(
    page = page,
    results = content.map { it.toDto() },
    totalPages = totalPages,
    totalResults = totalElements,
)
