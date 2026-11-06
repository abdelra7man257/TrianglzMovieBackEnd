package com.example.trianglzmoviebackend.tmdb.client

/** Thrown when a TMDb call fails. The message never contains the request URL, because it carries the API key. */
class TmdbRequestFailedException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
