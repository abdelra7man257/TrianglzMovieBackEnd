# Android integration guide

Follow these steps to make the Android app use this backend instead of calling TMDb directly, and to update its local cache when a movie is edited.

> The Kotlin code below is a reference written for this backend and for an app that uses **Ktor** (HTTP client), **Koin** (dependency injection), Room and kotlinx.serialization. Adapt names and packages to your app. It has not been compiled inside your project.

**What you will do**

1. Load movies (list, search, details) from the backend.
2. Keep the original TMDb setup working in its own build flavor.
3. Subscribe to a push topic. When a movie is edited, update Room from the push.

The app never edits movies. Edits are made in Swagger UI, Postman or the admin portal.

---

## What the backend gives you

**Base URL**

| App runs on | URL |
|---|---|
| Android emulator | `http://10.0.2.2:8080/api/` |
| Real phone, same Wi-Fi | `http://<computer IP>:8080/api/` |

Use the backend's `API_PORT` instead of `8080` if it was changed. Keep the trailing `/`.

**Endpoints** (no login needed)

| Method | Path | Returns |
|---|---|---|
| GET | `movies?page=1&size=20` | One page of movies, most popular first |
| GET | `movies/search?query=lion&page=1&size=20` | One page of matches |
| GET | `movies/{id}` | One movie |

`page` starts at **1**. `size` is 1 to 50. A blank `query` returns an empty page. There is a next page while `page < total_pages`.

**A movie**

```json
{
  "id": 550,
  "title": "Fight Club",
  "overview": "A ticking-time-bomb insomniac...",
  "poster_path": "/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg",
  "backdrop_path": "/hZkgoQYus5vegHoetLkCJzb17zJ.jpg",
  "release_date": "1999-10-15",
  "vote_average": 8.4,
  "vote_count": 27000,
  "popularity": 61.4,
  "genres": ["Drama", "Thriller"],
  "updated_at": "2026-11-06T19:10:00Z"
}
```

| Field | Notes |
|---|---|
| `id` | The TMDb id. Use it as the Room primary key. |
| `overview`, `poster_path`, `backdrop_path`, `release_date` | Can be `null` |
| `poster_path`, `backdrop_path` | Relative. Image URL = `https://image.tmdb.org/t/p/w500` + path |
| `genres` | Already **names**, not ids. Can be empty. |
| `updated_at` | ISO time. Changes whenever the movie is edited. |

**A page** (list and search)

```json
{ "page": 1, "results": [ { "...": "movies" } ], "total_pages": 10, "total_results": 200 }
```

**Errors** (any non-2xx)

```json
{ "status": 404, "error": "Not Found", "message": "Movie with id 999 not found", "details": [], "timestamp": "..." }
```

| Status | Map to your domain error |
|---|---|
| 404 | `NotFound` |
| 400 | `Validation` |
| 401 | `Unauthorized` (the app never edits, so this should not happen) |
| 500 | `Server` |
| No answer, timeout | `Network` |

---

## Step 1: Add the build flavors

Keep the TMDb version working in `tmdb`. Add `custom` for this backend.

```kotlin
// app/build.gradle.kts
android {
    buildFeatures { buildConfig = true }
    flavorDimensions += "backend"
    productFlavors {
        create("tmdb") {
            dimension = "backend"
            buildConfigField("String", "BASE_URL", "\"https://api.themoviedb.org/3/\"")
        }
        create("custom") {
            dimension = "backend"
            // Emulator -> your computer. Use your API_PORT if it is not 8080.
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/api/\"")
        }
    }
}
```

Add the libraries (skip any you already have, and keep the versions your project already uses):

```kotlin
dependencies {
    // Ktor client
    implementation("io.ktor:ktor-client-core:3.0.1")
    implementation("io.ktor:ktor-client-okhttp:3.0.1")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Koin
    implementation("io.insert-koin:koin-android:4.0.0")

    // Firebase push
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Fallback fetch
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
```

## Step 2: Allow plain HTTP to the emulator (custom flavor only)

The backend runs on plain HTTP locally, which Android blocks by default.

Create `app/src/custom/res/xml/network_security_config.xml`:

```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">10.0.2.2</domain>
        <!-- Real phone: add your computer's IP here too -->
    </domain-config>
</network-security-config>
```

Create `app/src/custom/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:networkSecurityConfig="@xml/network_security_config">
        <service
            android:name="com.example.app.fcm.MovieMessagingService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

Change `com.example.app.fcm.MovieMessagingService` to your real package (see Step 8).

## Step 3: Add the models

Use `@SerialName` for every snake_case field. You will turn on `ignoreUnknownKeys` in Step 5 so new backend fields never crash the app.

```kotlin
@Serializable
data class MoviePageResponse(
    val page: Int,
    val results: List<MovieDto>,
    @SerialName("total_pages") val totalPages: Int,
    @SerialName("total_results") val totalResults: Long,
)

@Serializable
data class MovieDto(
    val id: Long,
    val title: String,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    val popularity: Double = 0.0,
    val genres: List<String> = emptyList(),
    @SerialName("updated_at") val updatedAt: String,
)

```

## Step 4: Add the API service (Ktor)

This class calls the three endpoints. The client (Step 5) already knows the base URL, so paths are relative.

```kotlin
class CustomMovieApi(private val client: HttpClient) {

    suspend fun getMovies(page: Int, size: Int = 20): MoviePageResponse =
        client.get("movies") {
            parameter("page", page)
            parameter("size", size)
        }.body()

    suspend fun searchMovies(query: String, page: Int, size: Int = 20): MoviePageResponse =
        client.get("movies/search") {
            parameter("query", query)
            parameter("page", page)
            parameter("size", size)
        }.body()

    suspend fun getMovie(id: Long): MovieDto =
        client.get("movies/$id").body()
}
```

## Step 5: Add the data source, error mapping and Koin module

Your app should call an interface. Each flavor provides its own implementation, and the domain layer does not change.

**The interface and the backend implementation**

```kotlin
interface MovieRemoteDataSource {
    suspend fun getMovies(page: Int): PagedResult<MovieRemote>
    suspend fun search(query: String, page: Int): PagedResult<MovieRemote>
    suspend fun getMovie(id: Long): MovieRemote
}

class CustomBackendRemoteDataSource(
    private val api: CustomMovieApi,
) : MovieRemoteDataSource {

    override suspend fun getMovies(page: Int) = call { api.getMovies(page).toPagedResult() }
    override suspend fun search(query: String, page: Int) = call { api.searchMovies(query, page).toPagedResult() }
    override suspend fun getMovie(id: Long) = call { api.getMovie(id).toRemote() }

    private suspend fun <T> call(block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e                          // never turn a coroutine cancellation into an error
        } catch (e: Exception) {
            throw e.toDomainError()
        }
}
```

When converting a page, set `hasNext = page < totalPages`. Map `genres` straight to the names your UI shows.

**Error mapping.** `DomainError` here is your existing error type, the one your TMDb data source already throws (a sealed class that extends `Exception`). With `expectSuccess = true` (set below), Ktor throws on every non-2xx answer:

```kotlin
fun Throwable.toDomainError(): DomainError = when (this) {
    is ClientRequestException -> when (response.status.value) {   // 4xx
        404 -> DomainError.NotFound
        400 -> DomainError.Validation
        401 -> DomainError.Unauthorized
        else -> DomainError.Unknown
    }
    is ServerResponseException -> DomainError.Server              // 5xx
    is HttpRequestTimeoutException, is IOException -> DomainError.Network
    else -> DomainError.Unknown
}
```

**The Koin module, one per flavor.** Create a file with the **same name and same top-level `backendModule`** in both source sets: `app/src/custom/.../di/BackendModule.kt` and `app/src/tmdb/.../di/BackendModule.kt`. The shared code then just uses `backendModule`, and each flavor compiles its own.

```kotlin
// app/src/custom/.../di/BackendModule.kt
val backendModule = module {
    single { Json { ignoreUnknownKeys = true } }

    single {
        HttpClient(OkHttp) {
            expectSuccess = true                                   // throw on non-2xx
            install(ContentNegotiation) { json(get<Json>()) }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 15_000
            }
            defaultRequest { url(BuildConfig.BASE_URL) }           // ends with "/"
        }
    }

    single { CustomMovieApi(get()) }
    single<MovieRemoteDataSource> { CustomBackendRemoteDataSource(get()) }
}
```

The `tmdb` flavor's `backendModule` keeps your existing TMDb client and data source.

Start Koin once, in your `Application` class, and load the module:

```kotlin
startKoin {
    androidContext(this@App)
    modules(appModules, backendModule)    // appModules = your existing shared modules
}
```

Check: run the `customDebug` build. The list, paging and search should load from the backend.

---

## Step 6: Connect the app to Firebase

1. Open the Firebase project that the backend uses (its id is in the backend's service-account file, `project_id`).
2. Add your Android app there (same package name as the `customDebug` build).
3. Download `google-services.json` and put it in `app/`.
4. Add the `com.google.gms.google-services` plugin to the app module.
5. Add `google-services.json` to `.gitignore`.

The app and the backend **must be in the same Firebase project**. If not, the push never arrives.

## Step 7: Subscribe to the topic

Do this once the app starts, in the `custom` flavor only (for example in a class under `src/custom/...` called from `Application.onCreate`):

```kotlin
FirebaseMessaging.getInstance().subscribeToTopic("movie-updates")
```

Calling it on every start is fine. The topic name must be exactly `movie-updates`.

You only need the `POST_NOTIFICATIONS` permission (Android 13+) if you also show a visible notification. The data message arrives without it.

## Step 8: Receive the push

The backend sends a **data message** (no ready-made notification), so `onMessageReceived` runs when the app is open **and** when it is in the background. It is **not** delivered if the user force-stopped the app.

### The message

Every value is a string. A key that is **missing** means the movie has no value for it.

| Key | Example | Meaning |
|---|---|---|
| `type` | `MOVIE_UPDATED` | Check this first. Ignore other values. |
| `movie_id` | `550` | |
| `updated_at` | `2026-11-06T19:10:00Z` | |
| `updated_fields` | `title,vote_average` | Which fields really changed |
| `title` | `Fight Club (Edited)` | |
| `overview` | `A ticking-time-bomb...` | Missing if none |
| `poster_path` | `/pB8B.jpg` | Missing if none |
| `backdrop_path` | `/hZko.jpg` | Missing if none |
| `release_date` | `1999-10-15` | Missing if none |
| `vote_average` | `9.1` | |
| `vote_count` | `27000` | |
| `popularity` | `61.4` | |
| `genres` | `["Drama","Thriller"]` | JSON list of names |
| `partial` | `true` | Only present if the message was too big. `overview` is then missing. **Fetch the movie instead of using the push.** |

### Turn the message into a movie

```kotlin
fun Map<String, String>.toMovieOrNull(json: Json): MovieDto? {
    if (this["partial"] == "true") return null           // incomplete: fetch from the API
    val id = this["movie_id"]?.toLongOrNull() ?: return null
    val title = this["title"] ?: return null
    val updatedAt = this["updated_at"] ?: return null
    return MovieDto(
        id = id,
        title = title,
        overview = this["overview"],
        posterPath = this["poster_path"],
        backdropPath = this["backdrop_path"],
        releaseDate = this["release_date"],
        voteAverage = this["vote_average"]?.toDoubleOrNull() ?: 0.0,
        voteCount = this["vote_count"]?.toIntOrNull() ?: 0,
        popularity = this["popularity"]?.toDoubleOrNull() ?: 0.0,
        genres = this["genres"]
            ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
            ?: emptyList(),
        updatedAt = updatedAt,
    )
}
```

### The service

```kotlin
class MovieMessagingService : FirebaseMessagingService(), KoinComponent {

    private val movieRepository: MovieRepository by inject()   // writes to Room
    private val json: Json by inject()

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != "MOVIE_UPDATED") return
        val movieId = data["movie_id"]?.toLongOrNull() ?: return

        val movie = data.toMovieOrNull(json)
        if (movie != null) {
            // Already on a background thread, and fast. You have about 20 seconds.
            runBlocking { movieRepository.upsertIfNewer(movie) }
        } else {
            RefreshMovieWorker.enqueue(applicationContext, movieId)   // Step 9
        }
    }
}
```

Optional: show a notification such as "`title` was updated" from here.

## Step 9: Save to Room and the fallback fetch

**Ignore old data.** Only overwrite the row if the incoming `updated_at` is the same or newer. Add an `updatedAt` column to the Room entity.

```kotlin
suspend fun upsertIfNewer(remote: MovieDto) {
    val local = dao.getById(remote.id)
    if (local == null || Instant.parse(remote.updatedAt) >= Instant.parse(local.updatedAt)) {
        dao.upsert(remote.toEntity())
    }
}
```

**Fallback worker** — it gets its dependencies from Koin through `KoinComponent`, so no extra WorkManager setup is needed. It is used when the push has `partial=true` or cannot be read:

```kotlin
class RefreshMovieWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val remote: MovieRemoteDataSource by inject()
    private val movieRepository: MovieRepository by inject()

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_MOVIE_ID, -1).takeIf { it > 0 } ?: return Result.failure()
        return try {
            movieRepository.upsertIfNewer(remote.getMovie(id))
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: DomainError) {
            if (e == DomainError.Network) Result.retry() else Result.failure()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val KEY_MOVIE_ID = "movie_id"
        fun enqueue(context: Context, movieId: Long) {
            val request = OneTimeWorkRequestBuilder<RefreshMovieWorker>()
                .setInputData(workDataOf(KEY_MOVIE_ID to movieId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("refresh-movie-$movieId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
```

## Step 10: The screen updates by itself

If the list and the details screen read from Room (`Flow` or a Room `PagingSource`), saving the row refreshes them. You need no extra UI code. If a screen reads from memory instead, it will not update.

If you use a `RemoteMediator`, keep the backend's order (most popular first) in a column so the list order stays the same.

---

## Step 11: Test it

1. Start the backend (see the main README) with your Firebase file set up.
2. Run the `customDebug` build on an emulator **with Google Play** (or a real phone).
3. Check that the list, paging and search work.
4. Put the app in the **background**.
5. Open Swagger UI, click **Authorize** (`admin` / `admin123`), and call `PATCH /api/movies/{id}` with `{"title": "Test edit"}`.
6. Backend log: `docker compose logs api | grep FCM` shows `FCM sent for movie ...`.
7. Open the app. The new title is shown without pull-to-refresh.
8. Open that movie's detail screen, leave the app open, and edit again. The title changes live.
9. Send the same title again with extra spaces. The backend sends **no** push and nothing changes.

Tests worth writing:

- The data source with Ktor's `MockEngine`: paging, `genres` as names, `null` overview and date.
- The error mapper: 404, 400, 401, 500 (a `MockEngine` answering with that status), a timeout and `IOException`.
- `toMovieOrNull`: a full message, a missing optional key, a bad `genres` value, `partial=true`.
- `upsertIfNewer`: newer row replaces, older one is ignored.
- `RefreshMovieWorker` with `TestListenableWorkerBuilder`.

## Troubleshooting

| Problem | Cause and fix |
|---|---|
| `CLEARTEXT communication not permitted` | Step 2 is missing, or the IP is not in the config |
| Cannot connect from the emulator | Use `10.0.2.2` and the backend's `API_PORT`, not `localhost` |
| Cannot connect from a real phone | Use the computer's IP, same Wi-Fi, open the port in the firewall, add the IP in Step 2 |
| No push arrives | Emulator without Google Play, app force-stopped, not subscribed to `movie-updates`, or the app and backend use different Firebase projects |
| Backend log says `FCM log-only mode` | The backend has no Firebase file, so nothing is sent |
| Push arrives, nothing changes | `type` is not `MOVIE_UPDATED`, the incoming `updated_at` is older than the saved one, or the service is missing from the manifest |
| Overview is missing in the push | The movie has none, or `partial=true` — then fetch the movie |
| Images do not show | `poster_path` is relative. Add `https://image.tmdb.org/t/p/w500` in front |
| Genres look like numbers | They do not. The backend already sends names |
