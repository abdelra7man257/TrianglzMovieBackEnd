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
        assertEquals("Fight Club (Edited)", published.movie.title)
        assertEquals(9.1, published.movie.voteAverage)
        assertEquals(listOf(MovieEditableField.TITLE, MovieEditableField.VOTE_AVERAGE), published.updatedFields)
    }

    @Test
    fun `event lists only the fields whose value really changed`() {
        val movie = movie().apply { overview = "same"; voteAverage = 7.0; genres = mutableListOf("Drama") }
        val event = slot<Any>()
        every { repository.findById(1) } returns Optional.of(movie)
        every { repository.save(movie) } returns movie
        every { eventPublisher.publishEvent(capture(event)) } returns Unit

        service.updateMovie(
            1,
            MovieUpdateRequest(title = "Fight Club", overview = "same", voteAverage = 7.5, genres = listOf("Drama")),
        )

        assertEquals(listOf(MovieEditableField.VOTE_AVERAGE), (event.captured as MovieUpdatedEvent).updatedFields)
    }

    @Test
    fun `every editable field is reported when all of them change`() {
        val movie = movie().apply { overview = "a"; posterPath = "/a.jpg"; genres = mutableListOf("Drama") }
        val event = slot<Any>()
        every { repository.findById(1) } returns Optional.of(movie)
        every { repository.save(movie) } returns movie
        every { eventPublisher.publishEvent(capture(event)) } returns Unit

        service.updateMovie(
            1,
            MovieUpdateRequest(
                title = "New", overview = "b", posterPath = "/b.jpg", releaseDate = java.time.LocalDate.of(2000, 1, 1),
                voteAverage = 5.0, genres = listOf("Comedy"),
            ),
        )

        assertEquals(MovieEditableField.entries, (event.captured as MovieUpdatedEvent).updatedFields)
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

    // --- A save only counts (and only sends a push) when the content really changes ---

    private fun stubMovie(movie: Movie) {
        every { repository.findById(movie.id) } returns Optional.of(movie)
        every { repository.save(movie) } returns movie
        every { eventPublisher.publishEvent(any<Any>()) } returns Unit
    }

    private fun assertNothingHappened(movie: Movie, updatedAtBefore: java.time.Instant) {
        assertEquals(false, movie.locallyModified)
        assertEquals(updatedAtBefore, movie.updatedAt)
        verify(exactly = 0) { repository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
    }

    @Test
    fun `whitespace around title, overview and poster path is not a change`() {
        val movie = movie().apply { overview = "Line one\nLine two"; posterPath = "/p.jpg" }
        val before = movie.updatedAt
        stubMovie(movie)

        service.updateMovie(
            1,
            MovieUpdateRequest(title = "  Fight Club \t", overview = "\n Line one  \r\nLine two   ", posterPath = " /p.jpg "),
        )

        assertNothingHappened(movie, before)
    }

    @Test
    fun `repeated inner spaces in a title are not a change`() {
        val movie = movie(title = "Fight Club")
        val before = movie.updatedAt
        stubMovie(movie)

        service.updateMovie(1, MovieUpdateRequest(title = "Fight   Club"))

        assertNothingHappened(movie, before)
    }

    @Test
    fun `a stored title with odd spacing equals the clean one`() {
        val movie = movie(title = "Fight  Club ")
        val before = movie.updatedAt
        stubMovie(movie)

        service.updateMovie(1, MovieUpdateRequest(title = "Fight Club"))

        assertNothingHappened(movie, before)
    }

    @Test
    fun `identical values are not a change`() {
        val movie = movie().apply { voteAverage = 8.4; releaseDate = java.time.LocalDate.of(1999, 10, 15) }
        val before = movie.updatedAt
        stubMovie(movie)

        service.updateMovie(
            1,
            MovieUpdateRequest(title = "Fight Club", voteAverage = 8.4, releaseDate = java.time.LocalDate.of(1999, 10, 15)),
        )

        assertNothingHappened(movie, before)
    }

    @Test
    fun `floating point noise in the rating is not a change`() {
        val movie = movie().apply { voteAverage = 0.1 + 0.2 }
        val before = movie.updatedAt
        stubMovie(movie)

        service.updateMovie(1, MovieUpdateRequest(voteAverage = 0.3))

        assertNothingHappened(movie, before)
    }

    @Test
    fun `an empty overview equals a missing one`() {
        val movie = movie()
        val before = movie.updatedAt
        stubMovie(movie)

        service.updateMovie(1, MovieUpdateRequest(overview = "", posterPath = ""))

        assertNothingHappened(movie, before)
    }

    @Test
    fun `genres in a different order, with blanks or duplicates are not a change`() {
        val movie = movie().apply { genres = mutableListOf("Drama", "Thriller") }
        val before = movie.updatedAt
        stubMovie(movie)

        service.updateMovie(1, MovieUpdateRequest(genres = listOf(" Thriller", "", "Drama ", "Drama")))

        assertNothingHappened(movie, before)
        assertEquals(listOf("Drama", "Thriller"), movie.genres)
    }

    @Test
    fun `a really different value is a change and publishes one event`() {
        val movie = movie()
        stubMovie(movie)

        service.updateMovie(1, MovieUpdateRequest(title = "Fight Club 2"))

        assertTrue(movie.locallyModified)
        verify(exactly = 1) { eventPublisher.publishEvent(any<Any>()) }
    }

    @Test
    fun `a case change is a real change`() {
        val movie = movie(title = "fight club")
        stubMovie(movie)

        service.updateMovie(1, MovieUpdateRequest(title = "Fight Club"))

        verify(exactly = 1) { eventPublisher.publishEvent(any<Any>()) }
    }

    @Test
    fun `a new genre is a real change`() {
        val movie = movie().apply { genres = mutableListOf("Drama") }
        stubMovie(movie)

        service.updateMovie(1, MovieUpdateRequest(genres = listOf("Drama", "Comedy")))

        assertEquals(listOf("Drama", "Comedy"), movie.genres)
        verify(exactly = 1) { eventPublisher.publishEvent(any<Any>()) }
    }

    @Test
    fun `whitespace noise next to a real change stores the clean value and publishes once`() {
        val movie = movie()
        stubMovie(movie)

        service.updateMovie(1, MovieUpdateRequest(title = "  Fight Club  ", voteAverage = 1.5))

        assertEquals("Fight Club", movie.title)
        assertEquals(1.5, movie.voteAverage)
        verify(exactly = 1) { eventPublisher.publishEvent(any<Any>()) }
    }
}
