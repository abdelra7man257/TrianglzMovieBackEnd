package com.example.trianglzmoviebackend.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val tmdb: Tmdb = Tmdb(),
    val firebase: Firebase = Firebase(),
    val admin: Admin = Admin(),
) {
    data class Tmdb(
        val apiKey: String = "",
        val baseUrl: String = "https://api.themoviedb.org/3",
        val syncPages: Int = 10,
    )

    data class Firebase(
        val credentialsPath: String = "",
        val topic: String = "movie-updates",
    )

    data class Admin(
        val username: String = "admin",
        val password: String = "admin123",
    )
}
