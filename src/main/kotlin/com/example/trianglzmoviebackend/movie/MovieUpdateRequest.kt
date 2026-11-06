package com.example.trianglzmoviebackend.movie

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Size
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import java.time.LocalDate

/** Partial update: a null or missing field means "leave unchanged". */
@Schema(description = "Fields to change. At least one is required.")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class MovieUpdateRequest(
    @field:Size(min = 1, max = 255)
    @field:Schema(example = "Fight Club (Edited)")
    val title: String? = null,
    @field:Size(max = 5000)
    @field:Schema(example = "New synopsis")
    val overview: String? = null,
    @field:Schema(example = "/new-poster.jpg")
    val posterPath: String? = null,
    @field:Schema(example = "1999-10-15")
    val releaseDate: LocalDate? = null,
    @field:DecimalMin("0.0") @field:DecimalMax("10.0")
    @field:Schema(example = "9.1")
    val voteAverage: Double? = null,
    @field:Size(max = 10)
    @field:Schema(example = "[\"Drama\", \"Thriller\"]")
    val genres: List<String>? = null,
) {
    fun hasNoChanges() =
        title == null && overview == null && posterPath == null &&
            releaseDate == null && voteAverage == null && genres == null
}
