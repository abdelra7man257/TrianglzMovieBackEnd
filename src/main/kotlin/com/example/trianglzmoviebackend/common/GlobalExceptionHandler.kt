package com.example.trianglzmoviebackend.common

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(MovieNotFoundException::class)
    fun notFound(e: MovieNotFoundException) = build(HttpStatus.NOT_FOUND, e.message ?: "Not found")

    @ExceptionHandler(
        MethodArgumentTypeMismatchException::class,
        MissingServletRequestParameterException::class,
        HttpMessageNotReadableException::class,
        IllegalArgumentException::class,
    )
    fun badRequest(e: Exception): ResponseEntity<ApiError> {
        val message = when (e) {
            is MethodArgumentTypeMismatchException -> "Invalid value for parameter '${e.name}'"
            is MissingServletRequestParameterException -> "Missing parameter '${e.parameterName}'"
            is HttpMessageNotReadableException -> "Malformed request body"
            else -> e.message ?: "Bad request"
        }
        return build(HttpStatus.BAD_REQUEST, message)
    }

    @ExceptionHandler(Exception::class)
    fun unexpected(e: Exception): ResponseEntity<ApiError> {
        log.error("Unexpected error", e)
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error")
    }

    private fun build(status: HttpStatus, message: String, details: List<String> = emptyList()) =
        ResponseEntity.status(status).body(ApiError(status.value(), status.reasonPhrase, message, details))
}
