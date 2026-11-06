package com.example.trianglzmoviebackend.movie

import com.example.trianglzmoviebackend.common.MovieNotFoundException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MovieService(private val repository: MovieRepository) {

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

    @Transactional(readOnly = true)
    fun get(id: Long): MovieDto =
        repository.findById(id).orElseThrow { MovieNotFoundException(id) }.toDto()

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
