package com.example.trianglzmoviebackend.movie

/** Movie fields an admin can edit. [jsonName] is the name used in the API and in push notifications. */
enum class MovieEditableField(val jsonName: String) {
    TITLE("title"),
    OVERVIEW("overview"),
    POSTER_PATH("poster_path"),
    RELEASE_DATE("release_date"),
    VOTE_AVERAGE("vote_average"),
    GENRES("genres"),
}
