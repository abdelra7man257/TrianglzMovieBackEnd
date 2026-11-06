package com.example.trianglzmoviebackend.tmdb.sync

import com.example.trianglzmoviebackend.config.AppProperties
import com.example.trianglzmoviebackend.tmdb.client.TmdbApiClient
import com.example.trianglzmoviebackend.tmdb.client.TmdbMovie
import com.example.trianglzmoviebackend.tmdb.client.TmdbMoviePage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TmdbMovieSyncServiceTest {
    private val client = mockk<TmdbApiClient>()
    private val importer = mockk<TmdbMovieImporter>()
    private val genres = mapOf(18 to "Drama")

    private fun service(syncPages: Int) = TmdbMovieSyncService(
        client, importer, AppProperties(tmdb = AppProperties.Tmdb(apiKey = "key", syncPages = syncPages)),
    )

    private fun page(number: Int, totalPages: Int) =
        TmdbMoviePage(number, listOf(TmdbMovie(id = number.toLong(), title = "Movie $number")), totalPages)

    @Test
    fun `imports the configured number of pages`() {
        every { client.fetchGenreNamesById() } returns genres
        every { client.fetchPopularMoviesPage(any()) } answers { page(firstArg(), totalPages = 500) }
        every { importer.upsertAll(any(), genres) } returns 1

        val importedCount = service(syncPages = 3).syncPopularMovies()

        assertEquals(3, importedCount)
        verify(exactly = 3) { client.fetchPopularMoviesPage(any()) }
    }

    @Test
    fun `stops when TMDb has fewer pages than configured`() {
        every { client.fetchGenreNamesById() } returns genres
        every { client.fetchPopularMoviesPage(any()) } answers { page(firstArg(), totalPages = 2) }
        every { importer.upsertAll(any(), genres) } returns 1

        service(syncPages = 10).syncPopularMovies()

        verify(exactly = 2) { client.fetchPopularMoviesPage(any()) }
    }
}
