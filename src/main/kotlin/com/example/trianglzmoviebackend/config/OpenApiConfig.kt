package com.example.trianglzmoviebackend.config

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import io.swagger.v3.core.jackson.ModelResolver
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.providers.ObjectMapperProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Swagger UI metadata and the HTTP Basic scheme behind its "Authorize" button. */
@Configuration
class OpenApiConfig {

    @Bean
    fun movieBackendOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Movie Backend API")
                .version("1.0.0")
                .description(
                    "Movies list, search and details for the Android app. " +
                        "Editing a movie (PATCH) needs the admin credentials and sends an FCM push to the app.",
                ),
        )
        .components(
            Components().addSecuritySchemes(
                BASIC_AUTH_SCHEME,
                SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic"),
            ),
        )

    /**
     * The API JSON is snake_case (Jackson 3), but the docs generator uses Jackson 2 and cannot see that setting.
     * Without this, Swagger UI would show and send camelCase names such as `voteAverage`.
     */
    @Bean
    fun snakeCaseModelResolver(objectMapperProvider: ObjectMapperProvider): ModelResolver =
        ModelResolver(
            objectMapperProvider.jsonMapper().copy().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE),
        )

    companion object {
        const val BASIC_AUTH_SCHEME = "basicAuth"
    }
}
