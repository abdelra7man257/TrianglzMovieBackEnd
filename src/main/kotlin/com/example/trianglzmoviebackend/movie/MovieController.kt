package com.example.trianglzmoviebackend.movie

import com.example.trianglzmoviebackend.common.ApiError
import com.example.trianglzmoviebackend.common.PagedResponse
import com.example.trianglzmoviebackend.config.OpenApiConfig
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/movies")
@Tag(name = "Movies", description = "Browse, search and edit movies")
class MovieController(private val service: MovieService) {

    @GetMapping
    @Operation(summary = "List movies", description = "Paged, sorted by popularity (highest first).")
    fun list(
        @Parameter(description = "Page number, starting at 1") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "Page size, 1 to 50") @RequestParam(defaultValue = "20") size: Int,
    ): PagedResponse<MovieDto> = service.list(page, size)

    @GetMapping("/search")
    @Operation(
        summary = "Search movies by title",
        description = "Case-insensitive partial match. A blank query returns an empty page.",
    )
    fun search(
        @Parameter(description = "Text to look for in the title", example = "lion") @RequestParam query: String,
        @Parameter(description = "Page number, starting at 1") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "Page size, 1 to 50") @RequestParam(defaultValue = "20") size: Int,
    ): PagedResponse<MovieDto> = service.search(query, page, size)

    @GetMapping("/{id}")
    @Operation(summary = "Get movie details")
    @ApiResponse(responseCode = "200", description = "The movie")
    @ApiResponse(
        responseCode = "404", description = "Movie not found",
        content = [Content(schema = Schema(implementation = ApiError::class))],
    )
    fun get(@Parameter(description = "TMDb movie id", example = "550") @PathVariable id: Long): MovieDto =
        service.get(id)

    @PatchMapping("/{id}")
    @Operation(
        summary = "Edit a movie",
        description = "Partial update: omitted or null fields stay unchanged. " +
            "Marks the movie as edited (TMDb sync will not overwrite it) and sends an FCM push to the app.",
        security = [SecurityRequirement(name = OpenApiConfig.BASIC_AUTH_SCHEME)],
    )
    @ApiResponse(responseCode = "200", description = "The updated movie")
    @ApiResponse(
        responseCode = "400", description = "Validation failed or no fields provided",
        content = [Content(schema = Schema(implementation = ApiError::class))],
    )
    @ApiResponse(
        responseCode = "401", description = "Missing or wrong admin credentials",
        content = [Content(schema = Schema(implementation = ApiError::class))],
    )
    @ApiResponse(
        responseCode = "404", description = "Movie not found",
        content = [Content(schema = Schema(implementation = ApiError::class))],
    )
    fun update(
        @Parameter(description = "TMDb movie id", example = "550") @PathVariable id: Long,
        @Valid @RequestBody request: MovieUpdateRequest,
    ): MovieDto = service.updateMovie(id, request)
}
