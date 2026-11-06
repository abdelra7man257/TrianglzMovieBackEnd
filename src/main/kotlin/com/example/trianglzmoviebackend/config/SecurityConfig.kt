package com.example.trianglzmoviebackend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {

    /** REST API: reads are public, every other method needs HTTP Basic as ADMIN. Stateless, so no CSRF. */
    @Bean
    @Order(1)
    fun apiSecurityFilterChain(http: HttpSecurity, entryPoint: ApiAuthenticationEntryPoint): SecurityFilterChain {
        http {
            securityMatcher("/api/**")
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            authorizeHttpRequests {
                authorize(HttpMethod.GET, "/api/**", permitAll)
                authorize(anyRequest, hasRole(ADMIN_ROLE))
            }
            httpBasic { authenticationEntryPoint = entryPoint }
        }
        return http.build()
    }

    /** Admin portal: form login with a session. CSRF stays enabled (Thymeleaf adds the token to forms). */
    @Bean
    @Order(2)
    fun adminPortalSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            securityMatcher("/admin/**", "/login", "/logout")
            authorizeHttpRequests {
                authorize("/login", permitAll)
                authorize(anyRequest, hasRole(ADMIN_ROLE))
            }
            formLogin {
                loginPage = "/login"
                defaultSuccessUrl("/admin/movies", true)
            }
            logout { logoutSuccessUrl = "/login?logout" }
        }
        return http.build()
    }

    /** Everything else (Swagger UI, health, API docs) is public. */
    @Bean
    @Order(3)
    fun publicSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            authorizeHttpRequests { authorize(anyRequest, permitAll) }
        }
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Bean
    fun adminUserDetailsService(properties: AppProperties, passwordEncoder: PasswordEncoder): UserDetailsService {
        val admin = User.withUsername(properties.admin.username)
            .password(passwordEncoder.encode(properties.admin.password))
            .roles(ADMIN_ROLE)
            .build()
        return InMemoryUserDetailsManager(admin)
    }

    private companion object {
        const val ADMIN_ROLE = "ADMIN"
    }
}
