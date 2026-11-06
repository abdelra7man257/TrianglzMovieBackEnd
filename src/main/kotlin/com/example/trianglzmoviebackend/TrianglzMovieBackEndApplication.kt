package com.example.trianglzmoviebackend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class TrianglzMovieBackEndApplication

fun main(args: Array<String>) {
    runApplication<TrianglzMovieBackEndApplication>(*args)
}
