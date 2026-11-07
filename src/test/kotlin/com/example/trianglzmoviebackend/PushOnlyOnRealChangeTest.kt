package com.example.trianglzmoviebackend

import com.example.trianglzmoviebackend.movie.MovieUpdatedEvent
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals

/**
 * Whole application: the event that triggers the FCM push must only be published when the
 * content of the movie really changes, not for whitespace or identical values.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class PushOnlyOnRealChangeTest @Autowired constructor(
    private val mvc: MockMvc,
    private val jsonMapper: JsonMapper,
) {
    @Autowired
    private lateinit var events: ApplicationEvents

    // Each test uses its own dummy movie so tests do not influence each other.
    private fun edit(movieId: Long, body: String) =
        mvc.perform(
            patch("/api/movies/$movieId").with(httpBasic("admin", "admin123"))
                .contentType(MediaType.APPLICATION_JSON).content(body),
        ).andExpect(status().isOk)

    private fun movieJson(movieId: Long) =
        jsonMapper.readTree(mvc.perform(get("/api/movies/$movieId")).andReturn().response.contentAsString)

    private fun pushCount() = events.stream(MovieUpdatedEvent::class.java).count()

    @Test
    fun `a real change publishes exactly one push event`() {
        edit(9_000_010, """{"title":"Totally New Title"}""")

        assertEquals(1, pushCount())
    }

    @Test
    fun `whitespace around values publishes no push and leaves updated_at untouched`() {
        val before = movieJson(9_000_011)
        val title = before["title"].asString()
        val overview = before["overview"].asString()

        edit(9_000_011, """{"title":"  $title  ","overview":"\n  $overview \t\n","poster_path":"   "}""")

        assertEquals(0, pushCount())
        assertEquals(before["updated_at"].asString(), movieJson(9_000_011)["updated_at"].asString())
    }

    @Test
    fun `extra spaces inside the title publish no push`() {
        val title = movieJson(9_000_012)["title"].asString()

        edit(9_000_012, """{"title":"${title.replace(" ", "   ")}"}""")

        assertEquals(0, pushCount())
    }

    @Test
    fun `submitting the identical values again publishes no push`() {
        val movie = movieJson(9_000_013)

        edit(
            9_000_013,
            """{"title":"${movie["title"].asString()}","vote_average":${movie["vote_average"].asDouble()},
               "release_date":"${movie["release_date"].asString()}"}""",
        )

        assertEquals(0, pushCount())
    }

    @Test
    fun `genres in another order or with blanks publish no push`() {
        val genresNode = movieJson(9_000_014)["genres"]
        val genres = (0 until genresNode.size()).map { genresNode[it].asString() }

        edit(9_000_014, """{"genres":[${(genres.reversed() + " ").joinToString(",") { "\"$it\"" }}]}""")

        assertEquals(0, pushCount())
    }

    @Test
    fun `a real change next to whitespace noise publishes once`() {
        val title = movieJson(9_000_016)["title"].asString()

        edit(9_000_016, """{"title":"  $title  ","vote_average":1.5}""")

        assertEquals(1, pushCount())
    }
}
