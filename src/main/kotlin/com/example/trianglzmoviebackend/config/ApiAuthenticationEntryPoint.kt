package com.example.trianglzmoviebackend.config

import com.example.trianglzmoviebackend.common.ApiError
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/** Answers unauthenticated API calls with the standard ApiError JSON instead of an empty 401. */
@Component
class ApiAuthenticationEntryPoint(private val jsonMapper: JsonMapper) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        val status = HttpStatus.UNAUTHORIZED
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"movie-admin\"")
        jsonMapper.writeValue(
            response.outputStream,
            ApiError(status.value(), status.reasonPhrase, "Authentication is required to edit movies"),
        )
    }
}
