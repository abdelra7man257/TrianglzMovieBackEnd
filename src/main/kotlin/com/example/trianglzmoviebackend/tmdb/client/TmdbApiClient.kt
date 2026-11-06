package com.example.trianglzmoviebackend.tmdb.client

import com.example.trianglzmoviebackend.config.AppProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

@Component
class TmdbApiClient(
    restClientBuilder: RestClient.Builder,
    properties: AppProperties,
) {
    private val apiKey = properties.tmdb.apiKey
    private val restClient = restClientBuilder.baseUrl(properties.tmdb.baseUrl).build()

    fun fetchGenreNamesById(): Map<Int, String> =
        call("genre list") {
            restClient.get()
                .uri { it.path("/genre/movie/list").queryParam("api_key", apiKey).build() }
                .retrieve()
                .body(TmdbGenreList::class.java)
        }.genres.associate { it.id to it.name }

    /** Popular movies with the content filters required by the task: no adult content, US certification up to PG-13. */
    fun fetchPopularMoviesPage(page: Int): TmdbMoviePage =
        call("movie page $page") {
            restClient.get()
                .uri {
                    it.path("/discover/movie")
                        .queryParam("api_key", apiKey)
                        .queryParam("include_adult", false)
                        .queryParam("certification_country", "US")
                        .queryParam("certification.lte", "PG-13")
                        .queryParam("sort_by", "popularity.desc")
                        .queryParam("page", page)
                        .build()
                }
                .retrieve()
                .body(TmdbMoviePage::class.java)
        }

    private fun <T : Any> call(description: String, request: () -> T?): T =
        try {
            request() ?: throw TmdbRequestFailedException("TMDb returned an empty body for $description")
        } catch (e: RestClientResponseException) {
            throw TmdbRequestFailedException("TMDb request for $description failed with HTTP ${e.statusCode.value()}")
        } catch (e: RestClientException) {
            throw TmdbRequestFailedException("TMDb request for $description failed: ${e.javaClass.simpleName}")
        }
}
