package com.example.trianglzmoviebackend.movie

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/movies")
class MovieController(private val service: MovieService) {

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ) = service.list(page, size)

    @GetMapping("/search")
    fun search(
        @RequestParam query: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ) = service.search(query, page, size)

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long) = service.get(id)

    @PatchMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: MovieUpdateRequest) =
        service.updateMovie(id, request)
}
