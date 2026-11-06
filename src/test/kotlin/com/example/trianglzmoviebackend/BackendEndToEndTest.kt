package com.example.trianglzmoviebackend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.hamcrest.Matchers.containsString

/** Whole application on an in-memory database with the real security chains (no key, so dummy data). */
@SpringBootTest
@AutoConfigureMockMvc
class BackendEndToEndTest @Autowired constructor(private val mvc: MockMvc) {

    private val dummyMovieId = 9_000_001

    @Test
    fun `startup seeds dummy movies that the public API serves`() {
        mvc.perform(get("/api/movies").param("size", "5"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total_results").value(25))
            .andExpect(jsonPath("$.results.length()").value(5))
    }

    @Test
    fun `an API edit is persisted and visible through the public API and search`() {
        mvc.perform(
            patch("/api/movies/$dummyMovieId").with(httpBasic("admin", "admin123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Renamed By Test","genres":["Drama"]}"""),
        ).andExpect(status().isOk)

        mvc.perform(get("/api/movies/$dummyMovieId"))
            .andExpect(jsonPath("$.title").value("Renamed By Test"))
            .andExpect(jsonPath("$.genres[0]").value("Drama"))
        mvc.perform(get("/api/movies/search").param("query", "renamed by"))
            .andExpect(jsonPath("$.total_results").value(1))
    }

    @Test
    fun `the API rejects edits without credentials`() {
        mvc.perform(
            patch("/api/movies/$dummyMovieId").contentType(MediaType.APPLICATION_JSON).content("""{"title":"x"}"""),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `admin portal login leads to the movie list and an edit shows the edited badge`() {
        val login = mvc.perform(formLogin("/login").user("admin").password("admin123"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/movies"))
            .andReturn()
        val session = login.request.getSession(false) as MockHttpSession

        mvc.perform(get("/admin/movies").session(session))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("25 movies")))

        mvc.perform(
            post("/admin/movies/${dummyMovieId + 1}").session(session).with(csrf())
                .param("title", "Portal Edit").param("voteAverage", "7.5").param("genresText", "Comedy"),
        ).andExpect(redirectedUrl("/admin/movies"))

        mvc.perform(get("/admin/movies").session(session).param("query", "portal edit"))
            .andExpect(content().string(containsString("Portal Edit")))
            .andExpect(content().string(containsString("Edited")))
    }

    @Test
    fun `admin portal rejects wrong credentials and anonymous access`() {
        mvc.perform(formLogin("/login").user("admin").password("wrong"))
            .andExpect(redirectedUrl("/login?error"))
        mvc.perform(get("/admin/movies"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/login"))
    }
}
