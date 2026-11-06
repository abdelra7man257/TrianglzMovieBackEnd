package com.example.trianglzmoviebackend.tmdb.client

import com.example.trianglzmoviebackend.config.AppProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TmdbApiClientTest {
    private val secretApiKey = "super-secret-key"
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = TmdbApiClient(
        builder,
        AppProperties(tmdb = AppProperties.Tmdb(apiKey = secretApiKey, baseUrl = "https://tmdb.test/3")),
    )

    @Test
    fun `movie request carries the required content filters`() {
        server.expect(method(HttpMethod.GET))
            .andExpect(queryParam("include_adult", "false"))
            .andExpect(queryParam("certification_country", "US"))
            .andExpect(queryParam("certification.lte", "PG-13"))
            .andExpect(queryParam("sort_by", "popularity.desc"))
            .andExpect(queryParam("page", "3"))
            .andExpect(queryParam("api_key", secretApiKey))
            .andRespond(withSuccess(PAGE_JSON, MediaType.APPLICATION_JSON))

        val page = client.fetchPopularMoviesPage(3)

        server.verify()
        assertEquals(3, page.page)
        assertEquals(500, page.totalPages)
        assertEquals(listOf(18, 53), page.results.single().genreIds)
        assertEquals("1999-10-15", page.results.single().releaseDate)
    }

    @Test
    fun `genre list is mapped to names by id and unknown fields are ignored`() {
        server.expect(requestTo("https://tmdb.test/3/genre/movie/list?api_key=$secretApiKey"))
            .andRespond(withSuccess("""{"genres":[{"id":18,"name":"Drama"},{"id":53,"name":"Thriller"}],"extra":1}""", MediaType.APPLICATION_JSON))

        assertEquals(mapOf(18 to "Drama", 53 to "Thriller"), client.fetchGenreNamesById())
    }

    @Test
    fun `a failed request reports the status but never the API key`() {
        server.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        val error = assertThrows<TmdbRequestFailedException> { client.fetchGenreNamesById() }

        assertEquals(true, error.message!!.contains("401"))
        assertFalse(error.message!!.contains(secretApiKey))
        assertFalse(error.stackTraceToString().contains(secretApiKey))
    }

    private companion object {
        const val PAGE_JSON = """
            {"page":3,"total_pages":500,"total_results":10000,"results":[
              {"id":550,"title":"Fight Club","overview":"o","poster_path":"/p.jpg","backdrop_path":null,
               "release_date":"1999-10-15","vote_average":8.4,"vote_count":27000,"popularity":61.4,
               "genre_ids":[18,53],"adult":false,"original_language":"en"}]}
        """
    }
}
