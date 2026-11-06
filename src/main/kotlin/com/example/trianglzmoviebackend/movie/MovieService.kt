package com.example.trianglzmoviebackend.movie

import com.example.trianglzmoviebackend.common.PagedResponse
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class MovieService(
    private val repository: MovieRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {

    @Transactional(readOnly = true)
    fun list(page: Int, size: Int): PagedResponse<MovieDto> {
        val p = clampPage(page)
        return repository.findAll(pageable(p, size)).toResponse(p)
    }

    @Transactional(readOnly = true)
    fun search(query: String, page: Int, size: Int): PagedResponse<MovieDto> {
        val p = clampPage(page)
        val q = query.trim()
        if (q.isEmpty()) return PagedResponse(p, emptyList(), 0, 0)
        return repository.findByTitleContainingIgnoreCase(q, pageable(p, size)).toResponse(p)
    }

    /** Admin portal listing: all movies, or only title matches when [query] is not blank. */
    @Transactional(readOnly = true)
    fun listForAdmin(query: String, page: Int, size: Int): PagedResponse<MovieAdminDto> {
        val p = clampPage(page)
        val trimmedQuery = query.trim()
        val movies =
            if (trimmedQuery.isEmpty()) repository.findAll(pageable(p, size))
            else repository.findByTitleContainingIgnoreCase(trimmedQuery, pageable(p, size))
        return PagedResponse(
            page = p,
            results = movies.content.map { MovieAdminDto(it.toDto(), it.locallyModified) },
            totalPages = movies.totalPages,
            totalResults = movies.totalElements,
        )
    }

    @Transactional(readOnly = true)
    fun get(id: Long): MovieDto =
        repository.findById(id).orElseThrow { MovieNotFoundException(id) }.toDto()

    /** Applies a partial edit, marks the movie as locally modified and announces the change. */
    @Transactional
    fun updateMovie(id: Long, request: MovieUpdateRequest): MovieDto {
        require(!request.hasNoChanges()) { "At least one field must be provided" }
        require(request.title?.isBlank() != true) { "Title must not be blank" }

        val movie = repository.findById(id).orElseThrow { MovieNotFoundException(id) }
        movie.applyEdit(request)
        movie.locallyModified = true
        movie.updatedAt = Instant.now()
        val saved = repository.save(movie)

        eventPublisher.publishEvent(MovieUpdatedEvent(saved.id, saved.title, saved.updatedAt))
        return saved.toDto()
    }

    private fun Movie.applyEdit(request: MovieUpdateRequest) {
        request.title?.let { title = it.trim() }
        request.overview?.let { overview = it }
        request.posterPath?.let { posterPath = it }
        request.releaseDate?.let { releaseDate = it }
        request.voteAverage?.let { voteAverage = it }
        request.genres?.let { newGenres -> genres = newGenres.map(String::trim).filter(String::isNotEmpty).toMutableList() }
    }

    private fun clampPage(page: Int) = page.coerceAtLeast(1)

    private fun pageable(page: Int, size: Int) = PageRequest.of(
        page - 1,
        size.coerceIn(MIN_SIZE, MAX_SIZE),
        Sort.by(Sort.Order.desc("popularity"), Sort.Order.asc("id")),
    )

    companion object {
        const val MIN_SIZE = 1
        const val MAX_SIZE = 50
    }
}
