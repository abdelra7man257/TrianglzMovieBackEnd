package com.example.trianglzmoviebackend.common

class MovieNotFoundException(id: Long) : RuntimeException("Movie with id $id not found")
