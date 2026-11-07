package com.example.trianglzmoviebackend.tmdb.sync

import com.example.trianglzmoviebackend.config.AppProperties
import com.example.trianglzmoviebackend.movie.seed.DummyMovieSeeder
import com.example.trianglzmoviebackend.tmdb.client.TmdbRequestFailedException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments

class MovieCatalogInitializerTest {
    private val syncService = mockk<TmdbMovieSyncService>()
    private val seeder = mockk<DummyMovieSeeder>()
    private val args = DefaultApplicationArguments()

    private fun initializer(apiKey: String) =
        MovieCatalogInitializer(syncService, seeder, AppProperties(tmdb = AppProperties.Tmdb(apiKey = apiKey)))

    @Test
    fun `without an API key it seeds dummy data and never calls TMDb`() {
        every { seeder.seedIfEmpty() } returns 25

        initializer(apiKey = "").run(args)

        verify(exactly = 0) { syncService.syncPopularMovies() }
        verify(exactly = 1) { seeder.seedIfEmpty() }
    }

    @Test
    fun `with an API key it imports from TMDb`() {
        every { syncService.syncPopularMovies() } returns 200
        every { seeder.seedIfEmpty() } returns 0

        initializer(apiKey = "key").run(args)

        verify(exactly = 1) { syncService.syncPopularMovies() }
    }

    @Test
    fun `when TMDb fails it does not crash and falls back to dummy data`() {
        every { syncService.syncPopularMovies() } throws TmdbRequestFailedException("HTTP 401")
        every { seeder.seedIfEmpty() } returns 25

        initializer(apiKey = "key").run(args)

        verify(exactly = 1) { seeder.seedIfEmpty() }
    }
}
