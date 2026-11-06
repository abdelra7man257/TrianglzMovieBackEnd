package com.example.trianglzmoviebackend.movie.seed

import com.example.trianglzmoviebackend.movie.Movie
import com.example.trianglzmoviebackend.movie.MovieRepository
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * Fallback data used when no TMDb key is configured or TMDb is unreachable.
 * Ids start at [FIRST_DUMMY_ID] so they never collide with real TMDb ids.
 */
@Component
class DummyMovieSeeder(private val repository: MovieRepository) {

    /** @return how many movies were inserted (0 if the table already had data) */
    fun seedIfEmpty(): Int {
        if (repository.count() > 0) return 0
        val movies = DUMMY_MOVIES.mapIndexed { index, sample ->
            Movie(
                id = FIRST_DUMMY_ID + index,
                title = sample.title,
                overview = "Sample synopsis for ${sample.title}. This is dummy data used when TMDb is not available.",
                releaseDate = LocalDate.of(sample.year, 1 + index % 12, 1 + index % 28),
                voteAverage = sample.rating,
                voteCount = 1_000 + index * 137,
                popularity = 100.0 - index * 3.5,
                genres = sample.genres.toMutableList(),
            )
        }
        repository.saveAll(movies)
        return movies.size
    }

    private data class SampleMovie(val title: String, val year: Int, val rating: Double, val genres: List<String>)

    companion object {
        const val FIRST_DUMMY_ID = 9_000_001L

        private val DUMMY_MOVIES = listOf(
            SampleMovie("The Lion King", 1994, 8.3, listOf("Animation", "Family", "Drama")),
            SampleMovie("Finding Nemo", 2003, 7.8, listOf("Animation", "Family")),
            SampleMovie("Toy Story", 1995, 8.0, listOf("Animation", "Comedy", "Family")),
            SampleMovie("Back to the Future", 1985, 8.3, listOf("Adventure", "Comedy", "Science Fiction")),
            SampleMovie("Jurassic Park", 1993, 8.0, listOf("Adventure", "Science Fiction")),
            SampleMovie("The Princess Bride", 1987, 7.9, listOf("Adventure", "Fantasy", "Romance")),
            SampleMovie("Forrest Gump", 1994, 8.5, listOf("Comedy", "Drama", "Romance")),
            SampleMovie("The Karate Kid", 1984, 7.2, listOf("Drama", "Family")),
            SampleMovie("Ratatouille", 2007, 7.8, listOf("Animation", "Comedy", "Family")),
            SampleMovie("WALL-E", 2008, 8.1, listOf("Animation", "Science Fiction", "Family")),
            SampleMovie("Up", 2009, 8.0, listOf("Animation", "Adventure", "Family")),
            SampleMovie("Spirited Away", 2001, 8.5, listOf("Animation", "Fantasy", "Family")),
            SampleMovie("The Goonies", 1985, 7.3, listOf("Adventure", "Comedy", "Family")),
            SampleMovie("E.T. the Extra-Terrestrial", 1982, 7.9, listOf("Science Fiction", "Family")),
            SampleMovie("Hidden Figures", 2016, 7.9, listOf("Drama", "History")),
            SampleMovie("Apollo 13", 1995, 7.6, listOf("Drama", "History")),
            SampleMovie("The Incredibles", 2004, 7.7, listOf("Action", "Animation", "Family")),
            SampleMovie("How to Train Your Dragon", 2010, 7.8, listOf("Animation", "Fantasy", "Family")),
            SampleMovie("Moana", 2016, 7.6, listOf("Animation", "Adventure", "Family")),
            SampleMovie("Coco", 2017, 8.2, listOf("Animation", "Family", "Music")),
            SampleMovie("Paddington 2", 2017, 7.5, listOf("Comedy", "Family", "Adventure")),
            SampleMovie("Big Hero 6", 2014, 7.7, listOf("Action", "Animation", "Family")),
            SampleMovie("The Truman Show", 1998, 8.1, listOf("Comedy", "Drama")),
            SampleMovie("Kung Fu Panda", 2008, 7.3, listOf("Action", "Animation", "Comedy")),
            SampleMovie("Groundhog Day", 1993, 7.6, listOf("Comedy", "Fantasy", "Romance")),
        )
    }
}
