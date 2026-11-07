package com.example.trianglzmoviebackend.admin

import com.example.trianglzmoviebackend.movie.MovieDto
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals

class AdminMovieFormTest {

    @Test
    fun `genres are split on commas, trimmed and blanks dropped`() {
        val form = AdminMovieForm(genresText = " Drama , ,Thriller,")

        assertEquals(listOf("Drama", "Thriller"), form.genres)
    }

    @Test
    fun `empty genres text means no genres`() {
        assertEquals(emptyList(), AdminMovieForm(genresText = "  ").genres)
    }

    @Test
    fun `form is prefilled from a movie and converts back to an update request`() {
        val movie = MovieDto(
            id = 1, title = "Fight Club", overview = null, posterPath = "/p.jpg", backdropPath = null,
            releaseDate = LocalDate.of(1999, 10, 15), voteAverage = 8.4, voteCount = 1, popularity = 1.0,
            genres = listOf("Drama", "Thriller"), updatedAt = Instant.EPOCH,
        )

        val form = AdminMovieForm.from(movie)
        val request = form.toUpdateRequest()

        assertEquals("Drama, Thriller", form.genresText)
        assertEquals("", form.overview)
        assertEquals("Fight Club", request.title)
        assertEquals("/p.jpg", request.posterPath)
        assertEquals(8.4, request.voteAverage)
        assertEquals(listOf("Drama", "Thriller"), request.genres)
    }
}
