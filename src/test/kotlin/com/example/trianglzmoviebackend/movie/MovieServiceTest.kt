package com.example.trianglzmoviebackend.movie

import com.example.trianglzmoviebackend.common.PagedResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MovieServiceTest {
    private val repository = mockk<MovieRepository>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val service = MovieService(repository, eventPublisher)

    private fun movie(id: Long = 1, title: String = "Fight Club") = Movie(id = id, title = title)

    @Test
    fun `list clamps page and size and sorts by popularity then id`() {
        val pageable = slot<Pageable>()
        every { repository.findAll(capture(pageable)) } returns PageImpl(listOf(movie()))

        val result = service.list(page = -3, size = 500)

        assertEquals(1, result.page)
        assertEquals(0, pageable.captured.pageNumber)
        assertEquals(MovieService.MAX_SIZE, pageable.captured.pageSize)
        val sort = pageable.captured.sort
        assertTrue(sort.getOrderFor("popularity")!!.isDescending)
        assertTrue(sort.getOrderFor("id")!!.isAscending)
    }

    @Test
    fun `list enforces minimum size`() {
        val pageable = slot<Pageable>()
        every { repository.findAll(capture(pageable)) } returns Page.empty()

        service.list(page = 1, size = 0)

        assertEquals(MovieService.MIN_SIZE, pageable.captured.pageSize)
    }

    @Test
    fun `blank search returns empty page without hitting the repository`() {
        val result = service.search("   ", 2, 20)

        assertEquals(2, result.page)
        assertTrue(result.results.isEmpty())
        assertEquals(0, result.totalResults)
        verify(exactly = 0) { repository.findByTitleContainingIgnoreCase(any(), any()) }
    }

    @Test
    fun `search trims the query`() {
        every { repository.findByTitleContainingIgnoreCase("lion", any()) } returns PageImpl(listOf(movie(title = "The Lion King")))

        val result = service.search("  lion ", 1, 20)

        assertEquals("The Lion King", result.results.single().title)
    }

    @Test
    fun `get throws when movie is missing`() {
        every { repository.findById(999) } returns Optional.empty()

        assertThrows<MovieNotFoundException> { service.get(999) }
    }

    @Test
    fun `get maps entity to dto`() {
        every { repository.findById(1) } returns Optional.of(movie().apply { genres = mutableListOf("Drama") })

        val dto = service.get(1)

        assertEquals(listOf("Drama"), dto.genres)
    }

    @Test
    fun `update changes only provided fields, marks movie as modified and publishes an event`() {
        val movie = movie().apply { overview = "old"; genres = mutableListOf("Drama") }
        val event = slot<Any>()
        every { repository.findById(1) } returns Optional.of(movie)
        every { repository.save(movie) } returns movie
        every { eventPublisher.publishEvent(capture(event)) } returns Unit

        val dto = service.updateMovie(1, MovieUpdateRequest(title = "  Fight Club (Edited) ", voteAverage = 9.1))

        assertEquals("Fight Club (Edited)", dto.title)
        assertEquals(9.1, dto.voteAverage)
        assertEquals("old", dto.overview)
        assertEquals(listOf("Drama"), dto.genres)
        assertTrue(movie.locallyModified)
        val published = event.captured as MovieUpdatedEvent
        assertEquals(1, published.movieId)
        assertEquals("Fight Club (Edited)", published.title)
    }

    @Test
    fun `update replaces genres and drops blank ones`() {
        val movie = movie()
        every { repository.findById(1) } returns Optional.of(movie)
        every { repository.save(movie) } returns movie

        val dto = service.updateMovie(1, MovieUpdateRequest(genres = listOf(" Drama ", "", "Thriller")))

        assertEquals(listOf("Drama", "Thriller"), dto.genres)
    }

    @Test
    fun `update with no fields is rejected`() {
        assertThrows<IllegalArgumentException> { service.updateMovie(1, MovieUpdateRequest()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
    }

    @Test
    fun `update with blank title is rejected`() {
        assertThrows<IllegalArgumentException> { service.updateMovie(1, MovieUpdateRequest(title = "   ")) }
    }

    @Test
    fun `update of a missing movie throws and publishes nothing`() {
        every { repository.findById(999) } returns Optional.empty()

        assertThrows<MovieNotFoundException> { service.updateMovie(999, MovieUpdateRequest(title = "x")) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
    }
}
