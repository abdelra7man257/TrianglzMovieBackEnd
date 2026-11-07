package com.example.trianglzmoviebackend.movie

import kotlin.math.abs

/**
 * Rules for deciding whether an edit really changes a movie. Values that are equal after
 * normalization are the same content, so they must not cause a save or a push notification.
 */

private val WHITESPACE_RUN = Regex("\\s+")
private const val RATING_TOLERANCE = 1e-9

/** Single-line text such as a title or a genre: trimmed, and runs of whitespace become one space. */
fun String.normalizedSingleLine(): String = trim().replace(WHITESPACE_RUN, " ")

/** Multi-line text such as an overview: unified line endings, no trailing spaces per line, trimmed. */
fun String.normalizedMultiLine(): String =
    replace("\r\n", "\n").replace('\r', '\n').lines().joinToString("\n") { it.trimEnd() }.trim()

/** Genre names: normalized, blanks dropped, duplicates removed. */
fun List<String>.normalizedGenres(): List<String> =
    map { it.normalizedSingleLine() }.filter { it.isNotEmpty() }.distinct()

/** Ratings are equal when they differ only by floating-point noise. */
fun Double.sameRatingAs(other: Double): Boolean = abs(this - other) < RATING_TOLERANCE
