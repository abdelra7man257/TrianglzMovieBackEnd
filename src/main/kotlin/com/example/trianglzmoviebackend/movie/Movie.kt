package com.example.trianglzmoviebackend.movie

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import org.hibernate.annotations.BatchSize
import java.time.Instant
import java.time.LocalDate

/** `id` is the TMDb id (not generated) so a re-sync updates the same row. */
@Entity
@Table(name = "movies", indexes = [Index(name = "idx_movies_popularity", columnList = "popularity")])
class Movie(
    @Id
    var id: Long,

    @Column(nullable = false)
    var title: String,

    @Column(columnDefinition = "TEXT")
    var overview: String? = null,

    var posterPath: String? = null,
    var backdropPath: String? = null,
    var releaseDate: LocalDate? = null,
    var voteAverage: Double = 0.0,
    var voteCount: Int = 0,
    var popularity: Double = 0.0,

    /** true after an admin edit; TMDb sync must not overwrite edited fields. */
    var locallyModified: Boolean = false,

    var updatedAt: Instant = Instant.now(),

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "movie_genres", joinColumns = [JoinColumn(name = "movie_id")])
    @Column(name = "genre")
    @BatchSize(size = 50)
    var genres: MutableList<String> = mutableListOf(),
)
