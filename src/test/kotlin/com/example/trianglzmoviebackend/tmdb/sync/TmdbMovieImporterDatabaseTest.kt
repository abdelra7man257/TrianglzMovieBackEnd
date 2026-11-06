package com.example.trianglzmoviebackend.tmdb.sync

import com.example.trianglzmoviebackend.movie.MovieRepository
import com.example.trianglzmoviebackend.tmdb.client.TmdbMovie
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs the importer against a real (H2) database to prove edits survive a re-sync. */
@DataJpaTest
@Import(TmdbMovieImporter::class)
class TmdbMovieImporterDatabaseTest @Autowired constructor(
    private val importer: TmdbMovieImporter,
    private val repository: MovieRepository,
    private val entityManager: EntityManager,
) {
    private val genres = mapOf(18 to "Drama", 53 to "Thriller", 35 to "Comedy")

    private fun tmdbMovie(title: String, popularity: Double, voteCount: Int, genreIds: List<Int>) = TmdbMovie(
        id = 550, title = title, overview = "tmdb", releaseDate = "1999-10-15",
        voteAverage = 8.4, voteCount = voteCount, popularity = popularity, genreIds = genreIds,
    )

    private fun reload() = run {
        entityManager.flush()
        entityManager.clear()
        repository.findById(550).get()
    }

    @Test
    fun `an unedited movie is fully refreshed on re-sync`() {
        importer.upsertAll(listOf(tmdbMovie("Fight Club", 10.0, 100, listOf(18, 53))), genres)
        reload()

        importer.upsertAll(listOf(tmdbMovie("Fight Club Remastered", 20.0, 200, listOf(35))), genres)
        val movie = reload()

        assertEquals("Fight Club Remastered", movie.title)
        assertEquals(listOf("Comedy"), movie.genres)
        assertEquals(20.0, movie.popularity)
    }

    @Test
    fun `an admin-edited movie keeps its edits across a re-sync but gets fresh popularity`() {
        importer.upsertAll(listOf(tmdbMovie("Fight Club", 10.0, 100, listOf(18, 53))), genres)
        val edited = reload().apply {
            title = "Fight Club (Edited)"
            genres = mutableListOf("Action")
            locallyModified = true
        }
        repository.saveAndFlush(edited)
        entityManager.clear()

        importer.upsertAll(listOf(tmdbMovie("Fight Club", 99.0, 777, listOf(35))), genres)
        val movie = reload()

        assertEquals("Fight Club (Edited)", movie.title)
        assertEquals(listOf("Action"), movie.genres)
        assertTrue(movie.locallyModified)
        assertEquals(99.0, movie.popularity)
        assertEquals(777, movie.voteCount)
        assertEquals(1, repository.count())
    }
}
