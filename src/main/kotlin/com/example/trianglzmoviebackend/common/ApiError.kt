package com.example.trianglzmoviebackend.common

import java.time.Instant

data class ApiError(
    val status: Int,
    val error: String,
    val message: String,
    val details: List<String> = emptyList(),
    val timestamp: Instant = Instant.now(),
)
