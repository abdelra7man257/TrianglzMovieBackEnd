package com.example.trianglzmoviebackend.admin

import com.example.trianglzmoviebackend.movie.MovieDto
import com.example.trianglzmoviebackend.movie.MovieUpdateRequest
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

/** Backing object of the edit form. Genres are typed as one comma-separated text field. */
class AdminMovieForm(
    @field:NotBlank(message = "Title is required")
    @field:Size(max = 255, message = "Title must be at most 255 characters")
    var title: String = "",

    @field:Size(max = 5000, message = "Overview must be at most 5000 characters")
    var overview: String = "",

    @field:Size(max = 255, message = "Poster path must be at most 255 characters")
    var posterPath: String = "",

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    var releaseDate: LocalDate? = null,

    @field:NotNull(message = "Rating is required")
    @field:DecimalMin(value = "0.0", message = "Rating must be between 0 and 10")
    @field:DecimalMax(value = "10.0", message = "Rating must be between 0 and 10")
    var voteAverage: Double? = null,

    @field:Size(max = 500, message = "Genres text is too long")
    var genresText: String = "",
) {
    val genres: List<String>
        get() = genresText.split(",").map(String::trim).filter(String::isNotEmpty)

    fun toUpdateRequest() = MovieUpdateRequest(
        title = title.trim(),
        overview = overview.trim(),
        posterPath = posterPath.trim(),
        releaseDate = releaseDate,
        voteAverage = voteAverage,
        genres = genres,
    )

    companion object {
        const val MAX_GENRES = 10

        fun from(movie: MovieDto) = AdminMovieForm(
            title = movie.title,
            overview = movie.overview.orEmpty(),
            posterPath = movie.posterPath.orEmpty(),
            releaseDate = movie.releaseDate,
            voteAverage = movie.voteAverage,
            genresText = movie.genres.joinToString(", "),
        )
    }
}
