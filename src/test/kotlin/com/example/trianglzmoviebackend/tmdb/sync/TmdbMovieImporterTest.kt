package com.example.trianglzmoviebackend.tmdb.sync

import com.example.trianglzmoviebackend.movie.Movie
import com.example.trianglzmoviebackend.movie.MovieRepository
import com.example.trianglzmoviebackend.tmdb.client.TmdbMovie
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TmdbMovieImporterTest {
    private val repository = mockk<MovieRepository>()
    private val importer = TmdbMovieImporter(repository)
    private val genreNamesById = mapOf(18 to "Drama", 53 to "Thriller")

    private fun tmdbMovie(
        id: Long = 550,
        title: String? = "Fight Club",
        releaseDate: String? = "1999-10-15",
    ) = TmdbMovie(
        id = id, title = title, overview = "tmdb overview", posterPath = "/p.jpg", releaseDate = releaseDate,
        voteAverage = 8.4, voteCount = 27_000, popularity = 61.4, genreIds = listOf(18, 53, 999),
    )

    @Test
    fun `inserts a new movie with genre names and ignores unknown genre ids`() {
        val saved = slot<Movie>()
        every { repository.findById(550) } returns Optional.empty()
        every { repository.save(capture(saved)) } answers { saved.captured }

        val importedCount = importer.upsertAll(listOf(tmdbMovie()), genreNamesById)

        assertEquals(1, importedCount)
        assertEquals("Fight Club", saved.captured.title)
        assertEquals(LocalDate.of(1999, 10, 15), saved.captured.releaseDate)
        assertEquals(listOf("Drama", "Thriller"), saved.captured.genres)
        assertEquals(false, saved.captured.locallyModified)
    }

    @Test
    fun `empty release date becomes null`() {
        val saved = slot<Movie>()
        every { repository.findById(550) } returns Optional.empty()
        every { repository.save(capture(saved)) } answers { saved.captured }

        importer.upsertAll(listOf(tmdbMovie(releaseDate = "")), genreNamesById)

        assertNull(saved.captured.releaseDate)
    }

    @Test
    fun `movies without a title are skipped`() {
        val importedCount = importer.upsertAll(listOf(tmdbMovie(title = " ")), genreNamesById)

        assertEquals(0, importedCount)
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `unedited existing movie is fully overwritten`() {
        val existing = Movie(id = 550, title = "Old title", overview = "old")
        every { repository.findById(550) } returns Optional.of(existing)

        importer.upsertAll(listOf(tmdbMovie()), genreNamesById)

        assertEquals("Fight Club", existing.title)
        assertEquals("tmdb overview", existing.overview)
        assertEquals(8.4, existing.voteAverage)
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `admin-edited movie keeps its edits and only refreshes popularity and vote count`() {
        val edited = Movie(
            id = 550, title = "Edited title", overview = "edited overview", voteAverage = 9.9,
            locallyModified = true, genres = mutableListOf("Comedy"),
        )
        every { repository.findById(550) } returns Optional.of(edited)

        importer.upsertAll(listOf(tmdbMovie()), genreNamesById)

        assertEquals("Edited title", edited.title)
        assertEquals("edited overview", edited.overview)
        assertEquals(9.9, edited.voteAverage)
        assertEquals(listOf("Comedy"), edited.genres)
        assertEquals(61.4, edited.popularity)
        assertEquals(27_000, edited.voteCount)
    }
}
