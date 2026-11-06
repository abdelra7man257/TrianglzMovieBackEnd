package com.example.trianglzmoviebackend.movie

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface MovieRepository : JpaRepository<Movie, Long> {
    fun findByTitleContainingIgnoreCase(query: String, pageable: Pageable): Page<Movie>
}
