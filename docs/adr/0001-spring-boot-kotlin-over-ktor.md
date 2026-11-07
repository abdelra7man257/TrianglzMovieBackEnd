# 0001. Spring Boot (Kotlin) over Ktor

**Status:** accepted

## Context
REST API, persistence with paging, validation, security, an admin UI and Swagger. The team already writes Kotlin for the Android app.

## Decision
Use Spring Boot with Kotlin.

## Consequences
- Same language as the app: data classes, null safety, MockK for tests.
- Spring Data gives paging and derived queries; Spring Security, Bean Validation, Thymeleaf and springdoc cover the rest with little wiring.
- More auto-configuration to learn than Ktor, and a heavier runtime. Accepted for the amount of built-in functionality.
- Spring Boot 4 splits auto-configuration into modules, so a few starters (`restclient`, `security-test`, `data-jpa-test`) are added explicitly.
