package com.example.trianglzmoviebackend.movie

/** A movie as the admin portal sees it: the public data plus whether an admin has edited it. */
data class MovieAdminDto(
    val movie: MovieDto,
    val locallyModified: Boolean,
)
