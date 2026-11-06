# Movie Backend

Kotlin + Spring Boot backend for the movie app. It replaces the app's direct TMDb calls with our own API: movies list, pagination and search. Editing a movie (Postman, Swagger UI or the admin portal) sends an FCM push so the Android app refreshes its local cache in real time.

## Features

- **Read API:** `GET /api/movies`, `/api/movies/search?query=`, `/api/movies/{id}`. TMDb-style paging envelope, snake_case JSON.
- **Edit API:** `PATCH /api/movies/{id}` (HTTP Basic, partial update, validated). Every edit sends an FCM data message to the `movie-updates` topic after the database commit.
- **Data:** imports popular movies from TMDb (`include_adult=false`, `certification_country=US`, `certification.lte=PG-13`) and falls back to 25 dummy movies when no key is set or TMDb fails. Admin edits are never overwritten by a re-sync.
- **Admin portal:** `/admin/movies` lists, searches and edits movies (no create or delete). It uses the same service, so it sends the same push.
- **Docs and tooling:** Swagger UI, Postman collection, `api.http`.

## Tech stack

Kotlin 2.3 · Spring Boot 4.1 · Java 17 · Spring Data JPA · PostgreSQL (H2 for local) · Spring Security · Thymeleaf · Firebase Admin SDK · springdoc-openapi · JUnit 5 + MockK · Docker Compose.

## Run it

### Option 1: Docker Compose (Postgres + API)

```bash
cp .env.example .env        # then fill in TMDB_API_KEY (optional, see below)
docker compose up --build
```

The API is at <http://localhost:8080> (change `API_PORT` in `.env` if the port is taken; the same goes for `DB_PORT`). Open <http://localhost:8080/swagger-ui.html> or <http://localhost:8080/admin/movies>.

Stop with `docker compose down` (data is kept in a volume; add `-v` to wipe it).

### Option 2: Without Docker (H2 database)

```bash
set -a; . ./.env; set +a     # optional: load your TMDb key and other settings
./gradlew bootRun --args='--spring.profiles.active=local'
```

This stores data in `./data/` (git-ignored). Delete that folder to start fresh.

### Tests

```bash
./gradlew test
```

## Configuration

All settings are environment variables (see `.env.example`). Nothing secret is committed: `.env`, `secrets/`, `data/` and `google-services.json` are git-ignored.

| Variable | Default | Purpose |
|---|---|---|
| `TMDB_API_KEY` | *(empty: dummy data)* | TMDb v3 API key. Stays on the server; the app never ships it. |
| `TMDB_SYNC_PAGES` | `10` | Pages to import (20 movies each, so 200 by default). |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | local Postgres | Database. Compose sets these for you. |
| `DB_PORT` / `API_PORT` | `5432` / `8080` | Host ports used by Docker Compose. |
| `FIREBASE_CREDENTIALS_PATH` | *(empty: log-only)* | Firebase service-account JSON. |
| `FCM_TOPIC` | `movie-updates` | Topic the app subscribes to. |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | `admin` / `admin123` | Login for `PATCH` and the admin portal. **Change these outside local development.** |

### Getting a TMDb API key

Create a free account at themoviedb.org, then Settings → API → request a key. Put the v3 key in `.env` as `TMDB_API_KEY=...`. Without a key the backend works with the 25 dummy movies.

### Firebase setup (for real push notifications)

1. Create a Firebase project and add the Android app (its package name). Download `google-services.json` into the Android app (git-ignored).
2. Project settings → Service accounts → **Generate new private key**. Save it as `secrets/<any name>.json`.
3. Set `FIREBASE_CREDENTIALS_PATH`. With Docker Compose, `./secrets` is mounted at `/secrets`, so use `/secrets/<that file name>`. Without Docker, use the real file path.

Without credentials the backend runs in **log-only mode**: an edit logs the message it would have sent (`FCM log-only mode ...`) and succeeds normally.

## API

Base URL: `http://localhost:8080/api` (Android emulator: `http://10.0.2.2:8080/api`, or the port you set in `API_PORT`).

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/movies?page=1&size=20` | public | Paged list, most popular first |
| GET | `/movies/search?query=lion&page=1&size=20` | public | Case-insensitive partial title match |
| GET | `/movies/{id}` | public | Movie details |
| PATCH | `/movies/{id}` | Basic (ADMIN) | Partial update, sends FCM push |

`page` starts at 1 (clamped). `size` is 1 to 50 (clamped). A blank search query returns an empty page.

List and search response (`results` items are the same as the details response):

```json
{
  "page": 1,
  "results": [{
    "id": 550, "title": "Fight Club", "overview": "...",
    "poster_path": "/pB8B.jpg", "backdrop_path": "/hZko.jpg", "release_date": "1999-10-15",
    "vote_average": 8.4, "vote_count": 27000, "popularity": 61.4,
    "genres": ["Drama", "Thriller"], "updated_at": "2026-11-06T19:10:00Z"
  }],
  "total_pages": 10,
  "total_results": 200
}
```

Edit request (all fields optional, at least one required; `null` or missing means unchanged):

```json
{ "title": "Fight Club (Edited)", "overview": "New synopsis", "poster_path": "/new.jpg",
  "release_date": "1999-10-15", "vote_average": 9.1, "genres": ["Drama"] }
```

Validation: `title` 1 to 255 chars and not blank, `overview` up to 5000, `vote_average` 0 to 10, up to 10 `genres`.

Every error uses one JSON shape:

```json
{ "status": 404, "error": "Not Found", "message": "Movie with id 999 not found",
  "details": [], "timestamp": "2026-11-06T19:10:00Z" }
```

| Case | Status |
|---|---|
| Movie not found | 404 |
| Validation failed (`details` lists `field: message`), empty patch, malformed JSON, bad parameter | 400 |
| Missing or wrong credentials on `PATCH` | 401 |
| Unexpected error (logged, generic message) | 500 |

### Trying the edit endpoint

- **Swagger UI:** `/swagger-ui.html` → Authorize (admin login) → `PATCH /api/movies/{id}`.
- **Postman:** import `postman/MovieBackend.postman_collection.json` and set `baseUrl`.
- **IntelliJ / VS Code:** open `api.http` (environment in `http-client.env.json`).
- **curl:**
  ```bash
  curl -u admin:admin123 -X PATCH http://localhost:8080/api/movies/155 \
    -H 'Content-Type: application/json' -d '{"title":"The Dark Knight (Edited)"}'
  ```

## Admin portal

Open `/admin/movies` and sign in with the admin login. The list shows poster, title, year, rating and genres, an **Edited** badge on changed movies, a title search and paging. **Edit** opens a form with validation messages next to the fields. Saving uses the same service as `PATCH`, so it sets the edited flag and sends the push. The portal has no create or delete.

## How an edit reaches the app

```
PATCH or portal save → MovieService (saves, marks edited, publishes MovieUpdatedEvent)
  → after commit: MovieUpdatedPushNotifier → FCM topic "movie-updates"
  → app: onMessageReceived → re-fetch GET /api/movies/{id} → upsert into Room → UI updates
```

The push is a **data-only** message (so the app handles it in foreground and background) with string values:

```json
{ "type": "MOVIE_UPDATED", "movie_id": "550", "title": "Fight Club (Edited)", "updated_at": "2026-11-06T19:10:00Z" }
```

It is sent after the database commit, so a rolled-back edit sends nothing. A failed send is logged and never fails the edit. The payload carries only the id, and the app re-fetches the movie, so the server stays the single source of truth.

### Contract for the Android app

- Build flavor `custom` points at `http://10.0.2.2:<API_PORT>/api/` and allows cleartext HTTP for `10.0.2.2` only.
- Subscribe on start: `FirebaseMessaging.getInstance().subscribeToTopic("movie-updates")`.
- Handle `type == MOVIE_UPDATED`: enqueue unique work for `movie_id`, fetch the movie, upsert into Room.
- Map `ApiError.status` (404, 400, 401, 500) to the app's domain errors.
- Use a Google Play emulator image or a real device; FCM does not work on emulators without Google Play services.

## End-to-end demo

1. `docker compose up --build`. The logs show `TMDb sync: 200 movies imported` (or the dummy seed).
2. Put the app (`custom` flavor) in the background; the list, paging and search load from this backend.
3. In Swagger UI, authorize and `PATCH /api/movies/{id}` with a new title.
4. The backend log shows `FCM sent for movie {id} to topic 'movie-updates'`; a notification appears and the app shows the new title without a manual refresh.
5. Repeat from the admin portal, and with the app in the foreground on the detail screen.
6. Restart the backend: the edited movie is still edited (the TMDb sync does not overwrite it).

Without the Android app you can still check steps 1, 3, 4 (log line) and 6.

## Project structure

```
src/main/kotlin/com/example/trianglzmoviebackend/
├── movie/          entity, repository, service, controller, DTOs, update event; seed/ holds dummy data
├── tmdb/           client/ (TMDb HTTP client and models), sync/ (importer, sync service, startup runner)
├── notification/   FCM payload and the after-commit notifier
├── admin/          Thymeleaf portal controllers and edit form
├── config/         properties, security chains, Firebase, OpenAPI
└── common/         ApiError, PagedResponse, global exception handler
docs/adr/           architecture decision records
postman/            Postman collection
```

Layers follow the Android app's Clean Architecture: controller (HTTP only) → service (business rules, transactions, events) → repository (data access); entities are never returned from the API (DTOs + mapper).

## Architecture decisions

See [`docs/adr/`](docs/adr/README.md).

## Troubleshooting

- **Port already allocated:** set `DB_PORT` / `API_PORT` in `.env`.
- **Emulator cannot reach the API:** use `http://10.0.2.2:<API_PORT>/api/`, and allow cleartext for `10.0.2.2` in the app's network security config.
- **No push on the emulator:** use an image with Google Play, and do not force-stop the app.
- **`FCM log-only mode` in the logs:** `FIREBASE_CREDENTIALS_PATH` is empty or wrong.
- **TMDb sync failed (HTTP 401):** check `TMDB_API_KEY`. The backend keeps running with dummy data.
