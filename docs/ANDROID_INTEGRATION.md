# Android integration guide: custom movie backend

Give this file to the Android project. It describes everything the app needs to use our own backend instead of calling TMDb directly, and to refresh its local cache when a movie is edited.

> The Kotlin snippets are reference implementations written against this backend's contract. Adapt names and packages to the app's existing architecture (Clean Architecture, Hilt, Retrofit, Room). They have not been compiled inside your project.

## 1. What changes in the app

Only the **data layer** changes. Domain models and use cases stay as they are.

1. Add a build flavor `custom` next to the existing TMDb setup (flavor dimension `backend`: `tmdb`, `custom`). The `tmdb` flavor must keep working without the backend.
2. Add a remote data source implementation for the backend, selected per flavor.
3. Add Firebase Cloud Messaging: subscribe to a topic, receive a data message when a movie is edited, update Room.
4. The app **never edits** movies. Edits come from Postman, Swagger UI or the backend's admin portal.

## 2. Backend contract

### 2.1 Base URL

| Where the app runs | Base URL |
|---|---|
| Android emulator | `http://10.0.2.2:<port>/api/` |
| Real device on the same Wi-Fi | `http://<computer LAN IP>:<port>/api/` |

`<port>` is `8080` by default. It is whatever `API_PORT` is set to in the backend's `.env` (on the developer's machine it is `8081`, because 8080 is taken). Keep the trailing slash for Retrofit.

The backend uses plain HTTP locally, so the `custom` flavor must allow cleartext traffic (section 4.3).

### 2.2 Endpoints the app uses

All three are public (no login).

| Method | Path | Purpose |
|---|---|---|
| GET | `movies?page=1&size=20` | Paged list, most popular first |
| GET | `movies/search?query=lion&page=1&size=20` | Case-insensitive partial title match, paged |
| GET | `movies/{id}` | Movie details |

Rules: `page` starts at **1**; `size` is 1 to 50 (the server clamps values outside this); a blank `query` returns an empty page, not an error. Pagination is finished when `page >= total_pages`.

### 2.3 Responses

**List and search** (TMDb-style envelope, snake_case):

```json
{
  "page": 1,
  "results": [ { "...": "movie object below" } ],
  "total_pages": 10,
  "total_results": 200
}
```

**Movie object** (list items and `GET /movies/{id}` are identical):

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

| Field | Type | Notes |
|---|---|---|
| `id` | Long | The TMDb id. Use it as the Room primary key. |
| `title` | String | Never null |
| `overview` | String? | Nullable |
| `poster_path`, `backdrop_path` | String? | Nullable, **relative** paths. Build the image URL yourself: `https://image.tmdb.org/t/p/w500` + `poster_path` (use `w780` or `original` for the backdrop). |
| `release_date` | String? | `yyyy-MM-dd`, nullable |
| `vote_average` | Double | 0 to 10 |
| `vote_count` | Int | |
| `popularity` | Double | The server sorts by this, highest first |
| `genres` | List<String> | Already **names** (`"Drama"`), not TMDb ids. Can be empty. |
| `updated_at` | String | ISO-8601 UTC instant. Changes whenever the movie is edited. |

Differences from TMDb to remember: genres are names (no genre-id lookup needed), and the list has no `adult` or `genre_ids` fields.

### 2.4 Errors

Every non-2xx response has this JSON body:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Movie with id 999 not found",
  "details": [],
  "timestamp": "2026-11-06T19:10:00Z"
}
```

| Status | Meaning | Map to domain error |
|---|---|---|
| 404 | Movie not found | `NotFound` |
| 400 | Bad parameter, malformed request | `Validation` |
| 401 | Only on edit calls, which the app never makes | `Unauthorized` |
| 500 | Server error (generic message) | `Server` |
| no response / timeout / IOException | Backend unreachable | `Network` |

Show the user a friendly message, not `message` from the server.

## 3. Data layer

### 3.1 Gradle

```kotlin
// build.gradle.kts (app)
android {
    flavorDimensions += "backend"
    productFlavors {
        create("tmdb") {
            dimension = "backend"
            buildConfigField("String", "BASE_URL", "\"https://api.themoviedb.org/3/\"")
        }
        create("custom") {
            dimension = "backend"
            applicationIdSuffix = ".custom"      // optional; if used, register this id in Firebase too
            // Emulator -> host machine. Change the port to the backend's API_PORT.
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/api/\"")
        }
    }
    buildFeatures { buildConfig = true }
}

dependencies {
    // Retrofit + kotlinx.serialization (likely already present)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Firebase Cloud Messaging
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    // WorkManager + Hilt worker (fallback refresh)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
}
```

Apply the `com.google.gms.google-services` plugin. See section 4.1 for the `google-services.json` caveat.

### 3.2 DTOs

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

@Serializable
data class ApiErrorDto(
    val status: Int,
    val error: String,
    val message: String,
    val details: List<String> = emptyList(),
    val timestamp: String? = null,
)
```

Use `Json { ignoreUnknownKeys = true }` so new server fields never break the app.

### 3.3 Retrofit API

```kotlin
interface CustomMovieApi {
    @GET("movies")
    suspend fun getMovies(
        @Query("page") page: Int,
        @Query("size") size: Int = 20,
    ): MoviePageResponse

    @GET("movies/search")
    suspend fun searchMovies(
        @Query("query") query: String,
        @Query("page") page: Int,
        @Query("size") size: Int = 20,
    ): MoviePageResponse

    @GET("movies/{id}")
    suspend fun getMovie(@Path("id") id: Long): MovieDto
}
```

Provide `Retrofit` with `BuildConfig.BASE_URL` and `json.asConverterFactory("application/json".toMediaType())`. Add reasonable OkHttp timeouts (10 to 15 s).

### 3.4 Remote data source (flavor-specific binding)

Define one abstraction and bind a different implementation per flavor, in `src/tmdb/...` and `src/custom/...` Hilt modules:

```kotlin
interface MovieRemoteDataSource {
    suspend fun getMovies(page: Int): PagedResult<MovieRemote>
    suspend fun search(query: String, page: Int): PagedResult<MovieRemote>
    suspend fun getMovie(id: Long): MovieRemote
}

class CustomBackendRemoteDataSource @Inject constructor(
    private val api: CustomMovieApi,
    private val errorMapper: ErrorMapper,
) : MovieRemoteDataSource {

    override suspend fun getMovies(page: Int) = call { api.getMovies(page).toPagedResult() }
    override suspend fun search(query: String, page: Int) = call { api.searchMovies(query, page).toPagedResult() }
    override suspend fun getMovie(id: Long) = call { api.getMovie(id).toRemote() }

    private suspend fun <T> call(block: suspend () -> T): T =
        try { block() } catch (e: Exception) { throw errorMapper.map(e) }
}
```

Map `MoviePageResponse` to the app's existing paged model: `hasNext = page < totalPages`. Map `genres` straight to the genre names the UI shows.

### 3.5 Error mapper

```kotlin
class ErrorMapper @Inject constructor(private val json: Json) {
    fun map(e: Throwable): DomainError = when (e) {
        is HttpException -> when (e.code()) {
            404 -> DomainError.NotFound
            400 -> DomainError.Validation
            401 -> DomainError.Unauthorized
            else -> DomainError.Server
        }
        is IOException -> DomainError.Network
        else -> DomainError.Unknown
    }
}
```

Use the same domain errors the TMDb implementation already throws, so the UI needs no change. Parsing `ApiErrorDto` from `e.response()?.errorBody()` is optional (useful for logging).

## 4. Firebase Cloud Messaging

### 4.1 Setup

1. In the same Firebase project the backend uses, add the Android app (its package name; with `applicationIdSuffix`, add that id as well).
2. Download `google-services.json` into `app/`. **Do not commit it** (add it to `.gitignore`).
3. Caveat: the Google Services plugin needs this file for every flavor it processes. Keep one shared `app/google-services.json`; it does no harm in the `tmdb` flavor.

### 4.2 What the backend sends

When a movie is edited, the backend sends a **data-only** message to the topic **`movie-updates`**. Every installed app receives it. Data-only means `onMessageReceived` runs in the foreground **and** the background, so the app can always update its cache.

All values are strings:

```json
{
  "type": "MOVIE_UPDATED",
  "movie_id": "550",
  "title": "Fight Club (Edited)",
  "updated_at": "2026-11-06T19:10:00Z",
  "updated_fields": "title,vote_average",
  "movie": "{\"id\":550,\"title\":\"Fight Club (Edited)\",\"overview\":\"...\",\"poster_path\":\"/pB8B.jpg\",\"backdrop_path\":null,\"release_date\":\"1999-10-15\",\"vote_average\":9.1,\"vote_count\":27000,\"popularity\":61.4,\"genres\":[\"Drama\"],\"updated_at\":\"2026-11-06T19:10:00Z\"}"
}
```

| Key | Meaning |
|---|---|
| `type` | Always check `== "MOVIE_UPDATED"`; ignore other types |
| `movie_id` | The movie id (string) |
| `title` | New title, handy for a notification text |
| `updated_at` | When the edit happened |
| `updated_fields` | Comma-separated names of the fields that really changed: `title`, `overview`, `poster_path`, `release_date`, `vote_average`, `genres`. Can be empty. |
| `movie` | **The complete updated movie as a JSON string**, identical to the `MovieDto` from the API. **May be absent**: FCM limits a message to 4 KB, so for very long overviews the backend leaves it out. |

### 4.3 Manifest and network security (custom flavor only)

`app/src/custom/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <application android:networkSecurityConfig="@xml/network_security_config">
        <service
            android:name=".fcm.MovieMessagingService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

`app/src/custom/res/xml/network_security_config.xml` (cleartext only for the emulator host):

```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">10.0.2.2</domain>
        <!-- Real device testing: add your computer's LAN IP here -->
    </domain-config>
</network-security-config>
```

### 4.4 Subscribe to the topic

In the `custom` flavor only (for example an initializer in `src/custom/...` called from `Application.onCreate`):

```kotlin
FirebaseMessaging.getInstance().subscribeToTopic("movie-updates")
```

It is safe to call on every start. Request the `POST_NOTIFICATIONS` runtime permission on Android 13+ **only if** you show a visible notification; data messages arrive without it.

### 4.5 Receiving the message

```kotlin
@AndroidEntryPoint
class MovieMessagingService : FirebaseMessagingService() {

    @Inject lateinit var movieRepository: MovieRepository   // writes to Room
    @Inject lateinit var json: Json

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != "MOVIE_UPDATED") return
        val movieId = data["movie_id"]?.toLongOrNull() ?: return

        val movie = data["movie"]?.let {
            runCatching { json.decodeFromString<MovieDto>(it) }.getOrNull()
        }

        if (movie != null) {
            // Fast path: everything needed is in the message. Do the write off the main thread.
            CoroutineScope(Dispatchers.IO).launch { movieRepository.upsertIfNewer(movie) }
        } else {
            // Fallback: no `movie` (message too large or unparsable) -> fetch it by id.
            RefreshMovieWorker.enqueue(applicationContext, movieId)
        }

        // Optional: show a visible notification such as "<title> was updated".
    }
}
```

`onMessageReceived` has about 20 seconds; keep it short. If you want to be safe for slow disks, enqueue the worker in both cases and treat the message payload only as an optimisation.

**Ignore stale data.** Only overwrite the Room row when the incoming `updated_at` is the same or newer than the stored one (ISO-8601 strings in UTC compare correctly as `Instant`). This protects against out-of-order or repeated pushes:

```kotlin
suspend fun upsertIfNewer(remote: MovieDto) {
    val local = dao.getById(remote.id)
    if (local == null || Instant.parse(remote.updatedAt) >= Instant.parse(local.updatedAt)) {
        dao.upsert(remote.toEntity())
    }
}
```

Add an `updatedAt` column to the Room entity for this.

### 4.6 Fallback worker

```kotlin
@HiltWorker
class RefreshMovieWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val remote: MovieRemoteDataSource,
    private val movieRepository: MovieRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_MOVIE_ID, -1).takeIf { it > 0 } ?: return Result.failure()
        return try {
            movieRepository.upsertIfNewer(remote.getMovie(id))
            Result.success()
        } catch (e: DomainError.Network) { Result.retry() }
        catch (e: Exception) { Result.failure() }
    }

    companion object {
        const val KEY_MOVIE_ID = "movie_id"
        fun enqueue(context: Context, movieId: Long) {
            val request = OneTimeWorkRequestBuilder<RefreshMovieWorker>()
                .setInputData(workDataOf(KEY_MOVIE_ID to movieId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            // One pending refresh per movie: repeated pushes replace each other.
            WorkManager.getInstance(context)
                .enqueueUniqueWork("refresh-movie-$movieId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
```

### 4.7 The UI updates by itself

If the list and the details screen read from Room through `Flow` (or a Room `PagingSource`), upserting the row invalidates them and Compose recomposes. **No extra UI code is needed.** If a screen reads from memory instead, it will not update.

If you use Paging with a `RemoteMediator`, keep the server's order (`popularity` descending) or store it in a column, so the list order matches the backend.

## 5. Testing checklist

Start the backend (`docker compose up --build`), then:

1. Run the `customDebug` variant on an emulator **with Google Play** (FCM does not work on emulators without Google Play services, or on force-stopped apps). Check that the list, paging and search load.
2. Put the app in the **background**.
3. Open Swagger UI at `http://localhost:<port>/swagger-ui.html`, click **Authorize** (`admin` / `admin123`), and call `PATCH /api/movies/{id}` with e.g. `{"title": "Test edit"}`.
4. The backend log shows `FCM sent for movie {id} to topic 'movie-updates'`.
5. Open the app: the new title is there **without** pull-to-refresh.
6. Repeat with the app in the **foreground** on that movie's detail screen: the title changes live.
7. Repeat from the admin portal at `http://localhost:<port>/admin/movies` (`admin` / `admin123`).

Unit tests worth writing:

- `CustomBackendRemoteDataSource` with MockWebServer: paging mapping, `genres` as names, null `overview` and `release_date`.
- `ErrorMapper`: 404, 400, 401, 500 and `IOException`.
- Message handling: with `movie` present (upsert, no worker), without `movie` (worker enqueued), wrong `type` (ignored), stale `updated_at` (ignored).
- `RefreshMovieWorker` with `TestListenableWorkerBuilder`.

Quick contract check from a terminal:

```bash
curl "http://localhost:8080/api/movies?page=1&size=2"
curl "http://localhost:8080/api/movies/search?query=dark"
curl "http://localhost:8080/api/movies/550"
```

## 6. Common problems

| Symptom | Cause and fix |
|---|---|
| `CLEARTEXT communication not permitted` | `network_security_config` missing or does not list `10.0.2.2` (or your LAN IP) |
| Connection refused / timeout on the emulator | Wrong port (use the backend's `API_PORT`), backend not running, or you used `localhost` instead of `10.0.2.2` |
| Real device cannot connect | Use the computer's LAN IP, same Wi-Fi, allow the port through the firewall, add the IP to the network security config |
| No push arrives | Emulator image without Google Play; app force-stopped; not subscribed to `movie-updates`; backend running in log-only mode (check for `FCM log-only mode` in its log) |
| Push arrives but nothing changes | The `type` is not `MOVIE_UPDATED`, `movie` JSON failed to parse (check `ignoreUnknownKeys`), or the incoming `updated_at` is older than the local row |
| `updated_fields` is empty | The edit did not change any value; still safe to upsert |
| Images missing | `poster_path` is relative; prepend `https://image.tmdb.org/t/p/w500` |
| Genres look like numbers | They are not: the backend already sends names |
| Edits disappear after a backend restart | They do not: edited movies are protected from the TMDb re-sync. If they vanish, the backend database volume was wiped. |

## 7. Quick reference

- Topic: `movie-updates`
- Message `type`: `MOVIE_UPDATED`
- Endpoints: `GET /api/movies`, `GET /api/movies/search`, `GET /api/movies/{id}`
- Swagger UI: `/swagger-ui.html` · Admin portal: `/admin/movies`
- Default admin login (local only): `admin` / `admin123`
