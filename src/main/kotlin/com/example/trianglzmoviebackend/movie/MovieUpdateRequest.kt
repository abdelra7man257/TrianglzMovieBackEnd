package com.example.trianglzmoviebackend.movie

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Size
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import java.time.LocalDate

/** Partial update: a null or missing field means "leave unchanged". */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class MovieUpdateRequest(
    @field:Size(min = 1, max = 255)
    val title: String? = null,
    @field:Size(max = 5000)
    val overview: String? = null,
    val posterPath: String? = null,
    val releaseDate: LocalDate? = null,
    @field:DecimalMin("0.0") @field:DecimalMax("10.0")
    val voteAverage: Double? = null,
    @field:Size(max = 10)
    val genres: List<String>? = null,
) {
    fun hasNoChanges() =
        title == null && overview == null && posterPath == null &&
            releaseDate == null && voteAverage == null && genres == null
}
