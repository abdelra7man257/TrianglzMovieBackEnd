package com.example.trianglzmoviebackend.movie

import com.example.trianglzmoviebackend.common.PagedResponse
import com.example.trianglzmoviebackend.common.GlobalExceptionHandler
import com.example.trianglzmoviebackend.config.ApiAuthenticationEntryPoint
import com.example.trianglzmoviebackend.config.AppProperties
import com.example.trianglzmoviebackend.config.SecurityConfig
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalDate

@WebMvcTest(MovieController::class)
@Import(
    GlobalExceptionHandler::class,
    SecurityConfig::class,
    ApiAuthenticationEntryPoint::class,
    MovieControllerTest.Config::class,
)
class MovieControllerTest @Autowired constructor(
    private val mvc: MockMvc,
    private val service: MovieService,
) {
    @TestConfiguration
    class Config {
        @Bean
        fun movieService(): MovieService = mockk()

        @Bean
        fun appProperties() = AppProperties()
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

    private fun patchMovie(body: String, withCredentials: Boolean = true) =
        patch("/api/movies/550").contentType(MediaType.APPLICATION_JSON).content(body).let {
            if (withCredentials) it.with(httpBasic("admin", "admin123")) else it
        }

    @Test
    fun `patch without credentials returns 401 ApiError`() {
        mvc.perform(patchMovie("""{"title":"New"}""", withCredentials = false))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value(401))
    }

    @Test
    fun `patch with wrong password returns 401`() {
        mvc.perform(patchMovie("""{"title":"New"}""", withCredentials = false).with(httpBasic("admin", "wrong")))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `patch with admin credentials returns the updated movie`() {
        every { service.updateMovie(550, any()) } returns dto.copy(title = "New", voteAverage = 9.1)

        mvc.perform(patchMovie("""{"title":"New","vote_average":9.1,"release_date":"1999-10-15"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("New"))
            .andExpect(jsonPath("$.vote_average").value(9.1))
    }

    @Test
    fun `patch with invalid fields returns 400 with field errors`() {
        mvc.perform(patchMovie("""{"vote_average":11,"title":""}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(jsonPath("$.details.length()").value(2))
            .andExpect(jsonPath("$.details[?(@ =~ /vote_average.*/)]").isNotEmpty)
    }

    @Test
    fun `patch with malformed json returns 400`() {
        mvc.perform(patchMovie("{not json"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `patch of a missing movie returns 404`() {
        every { service.updateMovie(550, any()) } throws MovieNotFoundException(550)

        mvc.perform(patchMovie("""{"title":"New"}"""))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `unexpected errors return a generic 500 without leaking details`() {
        every { service.get(1) } throws IllegalStateException("jdbc:postgresql://secret-host password=hunter2")

        mvc.perform(get("/api/movies/1"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.message").value("Unexpected server error"))
            .andExpect(content().string(not(containsString("hunter2"))))
    }

    @Test
    fun `reading stays public`() {
        every { service.get(550) } returns dto

        mvc.perform(get("/api/movies/550")).andExpect(status().isOk)
    }
}
