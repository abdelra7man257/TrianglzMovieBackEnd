package com.example.trianglzmoviebackend.tmdb.sync

import com.example.trianglzmoviebackend.config.AppProperties
import com.example.trianglzmoviebackend.tmdb.client.TmdbApiClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** Imports the configured number of popular-movie pages from TMDb into our database. */
@Service
class TmdbMovieSyncService(
    private val tmdbApiClient: TmdbApiClient,
    private val movieImporter: TmdbMovieImporter,
    properties: AppProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val pagesToSync = properties.tmdb.syncPages

    /** @return how many movies were inserted or updated */
    fun syncPopularMovies(): Int {
        val genreNamesById = tmdbApiClient.fetchGenreNamesById()
        var importedCount = 0
        for (page in 1..pagesToSync) {
            val moviePage = tmdbApiClient.fetchPopularMoviesPage(page)
            importedCount += movieImporter.upsertAll(moviePage.results, genreNamesById)
            log.info("TMDb sync: page {}/{} done", page, minOf(pagesToSync, moviePage.totalPages))
            if (page >= moviePage.totalPages) break
        }
        return importedCount
    }
}
