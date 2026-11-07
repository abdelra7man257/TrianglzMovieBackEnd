package com.example.trianglzmoviebackend.common

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

/** TMDb-style paging envelope. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class PagedResponse<T>(
    val page: Int,
    val results: List<T>,
    val totalPages: Int,
    val totalResults: Long,
)
