package com.example.trianglzmoviebackend.config

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/** Opening the bare server URL lands on the API documentation. */
@Controller
class RootRedirectController {

    @GetMapping("/")
    fun redirectToSwaggerUi() = "redirect:/swagger-ui.html"
}
