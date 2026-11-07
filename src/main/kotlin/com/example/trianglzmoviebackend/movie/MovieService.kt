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

    /**
     * Applies a partial edit. Only a real change in content is saved and announced: values that
     * differ only by whitespace, genre order or null-versus-empty are ignored. In that case nothing
     * is saved, the movie is not marked as edited and no push notification is sent.
     */
    @Transactional
    fun updateMovie(id: Long, request: MovieUpdateRequest): MovieDto {
        require(!request.hasNoChanges()) { "At least one field must be provided" }
        require(request.title?.isBlank() != true) { "Title must not be blank" }

        val movie = repository.findById(id).orElseThrow { MovieNotFoundException(id) }
        val changedFields = movie.applyEdit(request)
        if (changedFields.isEmpty()) return movie.toDto()

        movie.locallyModified = true
        movie.updatedAt = Instant.now()
        val updatedMovie = repository.save(movie).toDto()

        eventPublisher.publishEvent(MovieUpdatedEvent(updatedMovie.id, updatedMovie, changedFields))
        return updatedMovie
    }

    /** Copies the fields whose normalized value differs and returns which ones they were. */
    private fun Movie.applyEdit(request: MovieUpdateRequest): List<MovieEditableField> {
        val changedFields = mutableListOf<MovieEditableField>()

        request.title?.normalizedSingleLine()?.let { newTitle ->
            if (newTitle != title.normalizedSingleLine()) { title = newTitle; changedFields += MovieEditableField.TITLE }
        }
        request.overview?.normalizedMultiLine()?.let { newOverview ->
            if (newOverview != overview.orEmpty().normalizedMultiLine()) {
                overview = newOverview; changedFields += MovieEditableField.OVERVIEW
            }
        }
        request.posterPath?.trim()?.let { newPosterPath ->
            if (newPosterPath != posterPath.orEmpty().trim()) {
                posterPath = newPosterPath; changedFields += MovieEditableField.POSTER_PATH
            }
        }
        request.releaseDate?.let { newReleaseDate ->
            if (newReleaseDate != releaseDate) { releaseDate = newReleaseDate; changedFields += MovieEditableField.RELEASE_DATE }
        }
        request.voteAverage?.let { newVoteAverage ->
            if (!newVoteAverage.sameRatingAs(voteAverage)) {
                voteAverage = newVoteAverage; changedFields += MovieEditableField.VOTE_AVERAGE
            }
        }
        request.genres?.normalizedGenres()?.let { newGenres ->
            if (newGenres.toSet() != genres.normalizedGenres().toSet()) {
                genres = newGenres.toMutableList(); changedFields += MovieEditableField.GENRES
            }
        }
        return changedFields
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
