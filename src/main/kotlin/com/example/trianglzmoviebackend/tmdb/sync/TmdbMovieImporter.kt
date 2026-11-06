package com.example.trianglzmoviebackend.tmdb.sync

import com.example.trianglzmoviebackend.movie.Movie
import com.example.trianglzmoviebackend.movie.MovieRepository
import com.example.trianglzmoviebackend.tmdb.client.TmdbMovie
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Upserts a page of TMDb movies. Each call is one transaction.
 * Movies edited by an admin (`locallyModified`) only get their popularity and vote count refreshed.
 */
@Component
class TmdbMovieImporter(private val repository: MovieRepository) {

    @Transactional
    fun upsertAll(tmdbMovies: List<TmdbMovie>, genreNamesById: Map<Int, String>): Int {
        var importedCount = 0
        for (tmdbMovie in tmdbMovies) {
            if (tmdbMovie.title.isNullOrBlank()) continue
            val existing = repository.findById(tmdbMovie.id).orElse(null)
            when {
                existing == null -> repository.save(tmdbMovie.toNewMovie(genreNamesById))
                existing.locallyModified -> existing.refreshPopularityAndVoteCount(tmdbMovie)
                else -> existing.overwriteFrom(tmdbMovie, genreNamesById)
            }
            importedCount++
        }
        return importedCount
    }

    private fun TmdbMovie.toNewMovie(genreNamesById: Map<Int, String>) = Movie(
        id = id,
        title = title.orEmpty(),
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate.toLocalDateOrNull(),
        voteAverage = voteAverage ?: 0.0,
        voteCount = voteCount ?: 0,
        popularity = popularity ?: 0.0,
        genres = genreIds.mapNotNull(genreNamesById::get).toMutableList(),
    )

    private fun Movie.overwriteFrom(tmdbMovie: TmdbMovie, genreNamesById: Map<Int, String>) {
        title = tmdbMovie.title.orEmpty()
        overview = tmdbMovie.overview
        posterPath = tmdbMovie.posterPath
        backdropPath = tmdbMovie.backdropPath
        releaseDate = tmdbMovie.releaseDate.toLocalDateOrNull()
        genres = tmdbMovie.genreIds.mapNotNull(genreNamesById::get).toMutableList()
        voteAverage = tmdbMovie.voteAverage ?: voteAverage
        refreshPopularityAndVoteCount(tmdbMovie)
        updatedAt = Instant.now()
    }

    private fun Movie.refreshPopularityAndVoteCount(tmdbMovie: TmdbMovie) {
        voteCount = tmdbMovie.voteCount ?: voteCount
        popularity = tmdbMovie.popularity ?: popularity
    }

    private fun String?.toLocalDateOrNull(): LocalDate? =
        if (isNullOrBlank()) null else try { LocalDate.parse(this) } catch (_: DateTimeParseException) { null }
}
