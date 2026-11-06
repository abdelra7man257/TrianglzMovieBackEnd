package com.example.trianglzmoviebackend.movie

import com.example.trianglzmoviebackend.common.GlobalExceptionHandler
import com.example.trianglzmoviebackend.common.MovieNotFoundException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalDate

@WebMvcTest(MovieController::class)
@Import(GlobalExceptionHandler::class, MovieControllerTest.Config::class)
class MovieControllerTest @Autowired constructor(
    private val mvc: MockMvc,
    private val service: MovieService,
) {
    @TestConfiguration
    class Config {
        @Bean
        fun movieService(): MovieService = mockk()
    }

    private val dto = MovieDto(
        id = 550, title = "Fight Club", overview = "o", posterPath = "/p.jpg", backdropPath = null,
        releaseDate = LocalDate.of(1999, 10, 15), voteAverage = 8.4, voteCount = 100, popularity = 61.4,
        genres = listOf("Drama"), updatedAt = Instant.parse("2026-11-06T19:10:00Z"),
    )

    @Test
    fun `list returns TMDb-style snake_case envelope`() {
        every { service.list(1, 20) } returns PagedResponse(1, listOf(dto), 10, 200)

        mvc.perform(get("/api/movies"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.total_pages").value(10))
            .andExpect(jsonPath("$.total_results").value(200))
            .andExpect(jsonPath("$.results[0].poster_path").value("/p.jpg"))
            .andExpect(jsonPath("$.results[0].release_date").value("1999-10-15"))
            .andExpect(jsonPath("$.results[0].vote_average").value(8.4))
            .andExpect(jsonPath("$.results[0].updated_at").value("2026-11-06T19:10:00Z"))
            .andExpect(jsonPath("$.results[0].genres[0]").value("Drama"))
    }

    @Test
    fun `search passes query and paging`() {
        every { service.search("fight", 2, 5) } returns PagedResponse(2, listOf(dto), 3, 11)

        mvc.perform(get("/api/movies/search").param("query", "fight").param("page", "2").param("size", "5"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page").value(2))
    }

    @Test
    fun `missing movie returns 404 ApiError`() {
        every { service.get(999) } throws MovieNotFoundException(999)

        mvc.perform(get("/api/movies/999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.message").value("Movie with id 999 not found"))
    }

    @Test
    fun `bad id type returns 400 ApiError`() {
        mvc.perform(get("/api/movies/abc"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
    }

    @Test
    fun `search without query returns 400`() {
        mvc.perform(get("/api/movies/search"))
            .andExpect(status().isBadRequest)
    }
}
