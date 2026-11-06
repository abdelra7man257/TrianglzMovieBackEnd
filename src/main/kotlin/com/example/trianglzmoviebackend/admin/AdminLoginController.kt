package com.example.trianglzmoviebackend.admin

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class AdminLoginController {

    @GetMapping("/login")
    fun showLoginPage() = "login"
}
