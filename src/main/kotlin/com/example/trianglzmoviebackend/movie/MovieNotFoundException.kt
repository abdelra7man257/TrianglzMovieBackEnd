package com.example.trianglzmoviebackend.movie

class MovieNotFoundException(id: Long) : RuntimeException("Movie with id $id not found")
