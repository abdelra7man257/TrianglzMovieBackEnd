package com.example.trianglzmoviebackend.admin

import com.example.trianglzmoviebackend.common.PagedResponse
import com.example.trianglzmoviebackend.config.ApiAuthenticationEntryPoint
import com.example.trianglzmoviebackend.config.AppProperties
import com.example.trianglzmoviebackend.config.SecurityConfig
import com.example.trianglzmoviebackend.movie.MovieAdminDto
import com.example.trianglzmoviebackend.movie.MovieDto
import com.example.trianglzmoviebackend.movie.MovieNotFoundException
import com.example.trianglzmoviebackend.movie.MovieService
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import java.time.Instant
import java.time.LocalDate

@WebMvcTest(AdminMovieController::class, AdminLoginController::class)
@Import(SecurityConfig::class, ApiAuthenticationEntryPoint::class, AdminMovieControllerTest.Config::class)
class AdminMovieControllerTest @Autowired constructor(
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

    @BeforeEach
    fun resetServiceMock() {
        clearMocks(service)
    }

    private val movie = MovieDto(
        id = 550, title = "Fight Club", overview = "o", posterPath = "/p.jpg", backdropPath = null,
        releaseDate = LocalDate.of(1999, 10, 15), voteAverage = 8.4, voteCount = 1, popularity = 1.0,
        genres = listOf("Drama"), updatedAt = Instant.EPOCH,
    )

    @Test
    fun `anonymous users are redirected to the login page`() {
        mvc.perform(get("/admin/movies"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/login"))
    }

    @Test
    fun `login page is public`() {
        mvc.perform(get("/login")).andExpect(status().isOk).andExpect(view().name("login"))
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `list shows movies with the edited badge`() {
        every { service.listForAdmin("", 1, 20) } returns
            PagedResponse(1, listOf(MovieAdminDto(movie, locallyModified = true)), 1, 1)

        mvc.perform(get("/admin/movies"))
            .andExpect(status().isOk)
            .andExpect(view().name("admin/movies"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Fight Club")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Edited")))
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `search passes the query and page to the service`() {
        every { service.listForAdmin("fight", 2, 20) } returns PagedResponse(2, emptyList(), 2, 21)

        mvc.perform(get("/admin/movies").param("query", " fight ").param("page", "2"))
            .andExpect(status().isOk)
            .andExpect(model().attribute("query", "fight"))
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `edit form is prefilled`() {
        every { service.get(550) } returns movie

        mvc.perform(get("/admin/movies/550/edit"))
            .andExpect(status().isOk)
            .andExpect(view().name("admin/edit"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"Fight Club\"")))
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `editing a missing movie redirects to the list with an error`() {
        every { service.get(999) } throws MovieNotFoundException(999)

        mvc.perform(get("/admin/movies/999/edit"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/movies"))
            .andExpect(flash().attribute("errorMessage", "Movie with id 999 not found"))
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `valid save calls the service and redirects with a success message`() {
        every { service.updateMovie(550, any()) } returns movie.copy(title = "Fight Club (Edited)")

        mvc.perform(
            post("/admin/movies/550").with(csrf())
                .param("title", "Fight Club (Edited)").param("voteAverage", "9.1")
                .param("genresText", "Drama, Thriller"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/movies"))
            .andExpect(flash().attributeExists("successMessage"))
        verify(exactly = 1) { service.updateMovie(550, any()) }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `invalid save shows the form again with field errors and does not call the service`() {
        mvc.perform(post("/admin/movies/550").with(csrf()).param("title", "").param("voteAverage", "11"))
            .andExpect(status().isOk)
            .andExpect(view().name("admin/edit"))
            .andExpect(model().attributeHasFieldErrors("form", "title", "voteAverage"))
        verify(exactly = 0) { service.updateMovie(any(), any()) }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `more than ten genres is rejected`() {
        val genres = (1..11).joinToString(",") { "G$it" }

        mvc.perform(post("/admin/movies/550").with(csrf()).param("title", "x").param("voteAverage", "5").param("genresText", genres))
            .andExpect(model().attributeHasFieldErrors("form", "genresText"))
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `save without a CSRF token is forbidden`() {
        mvc.perform(post("/admin/movies/550").param("title", "x").param("voteAverage", "5"))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `users without the admin role are forbidden`() {
        mvc.perform(get("/admin/movies")).andExpect(status().isForbidden)
    }
}
