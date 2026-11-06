package com.example.trianglzmoviebackend.movie.seed

import com.example.trianglzmoviebackend.movie.Movie
import com.example.trianglzmoviebackend.movie.MovieRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DummyMovieSeederTest {
    private val repository = mockk<MovieRepository>()
    private val seeder = DummyMovieSeeder(repository)

    @Test
    fun `seeds dummy movies with non-colliding ids when the table is empty`() {
        val saved = slot<List<Movie>>()
        every { repository.count() } returns 0
        every { repository.saveAll(capture(saved)) } answers { saved.captured }

        val seededCount = seeder.seedIfEmpty()

        assertEquals(25, seededCount)
        assertTrue(saved.captured.all { it.id >= DummyMovieSeeder.FIRST_DUMMY_ID })
        assertEquals(25, saved.captured.map { it.id }.toSet().size)
        assertTrue(saved.captured.all { it.genres.isNotEmpty() })
    }

    @Test
    fun `does nothing when movies already exist`() {
        every { repository.count() } returns 5

        assertEquals(0, seeder.seedIfEmpty())
        verify(exactly = 0) { repository.saveAll(any<List<Movie>>()) }
    }
}
