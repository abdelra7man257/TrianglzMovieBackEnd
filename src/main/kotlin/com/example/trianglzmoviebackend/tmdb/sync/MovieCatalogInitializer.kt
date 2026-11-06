package com.example.trianglzmoviebackend.tmdb.sync

import com.example.trianglzmoviebackend.config.AppProperties
import com.example.trianglzmoviebackend.movie.seed.DummyMovieSeeder
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * Fills the movie table on startup: TMDb import when an API key is set,
 * otherwise (or if TMDb fails and the table is empty) the dummy data.
 */
@Component
class MovieCatalogInitializer(
    private val syncService: TmdbMovieSyncService,
    private val dummyMovieSeeder: DummyMovieSeeder,
    properties: AppProperties,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tmdbApiKeyConfigured = properties.tmdb.apiKey.isNotBlank()

    override fun run(args: ApplicationArguments) {
        if (tmdbApiKeyConfigured) importFromTmdb() else log.info("TMDB_API_KEY is empty, using dummy data")
        val seededCount = dummyMovieSeeder.seedIfEmpty()
        if (seededCount > 0) log.info("Seeded {} dummy movies", seededCount)
    }

    private fun importFromTmdb() {
        try {
            log.info("TMDb sync: {} movies imported", syncService.syncPopularMovies())
        } catch (e: Exception) {
            log.error("TMDb sync failed, continuing with existing data: {}", e.message)
        }
    }
}
