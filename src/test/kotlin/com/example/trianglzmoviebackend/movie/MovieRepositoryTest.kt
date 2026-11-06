package com.example.trianglzmoviebackend.movie

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.data.domain.PageRequest
import kotlin.test.assertEquals

@DataJpaTest
class MovieRepositoryTest @Autowired constructor(private val repository: MovieRepository) {

    @Test
    fun `search is case-insensitive, partial and paged`() {
        repository.saveAll(
            listOf(
                Movie(id = 1, title = "The Lion King"),
                Movie(id = 2, title = "Lionheart"),
                Movie(id = 3, title = "Fight Club"),
            ),
        )

        val page = repository.findByTitleContainingIgnoreCase("LION", PageRequest.of(0, 1))

        assertEquals(2, page.totalElements)
        assertEquals(2, page.totalPages)
        assertEquals(1, page.content.size)
    }

    @Test
    fun `genres are persisted`() {
        repository.saveAndFlush(Movie(id = 5, title = "X", genres = mutableListOf("Action", "Drama")))

        assertEquals(listOf("Action", "Drama"), repository.findById(5).get().genres.sorted())
    }
}
