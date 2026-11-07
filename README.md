# Movie Backend

A Kotlin + Spring Boot backend for the movie app. It serves the movies list, paging and search, and lets an admin edit a movie. Every real edit sends a push notification (FCM) so the Android app can update its local cache.

**What it does**

- Serves movies: list, search and details (`/api/movies`).
- Fills its database from TMDb on startup. If there is no TMDb key (or TMDb fails), it uses 25 sample movies.
- Lets an admin edit a movie from Swagger UI, Postman or a web portal. TMDb re-syncs never overwrite an edited movie.
- Sends an FCM push **only when the content really changed**.

**Stack:** Kotlin 2.3, Spring Boot 4.1, Java 17, PostgreSQL (H2 for local), Spring Security, Thymeleaf, Firebase Admin SDK, Docker Compose.

---

## 1. Run it with Docker (recommended)

You need Docker. Nothing else.

1. Copy the settings file:
   ```bash
   cp .env.example .env
   ```
2. Open `.env` and set `TMDB_API_KEY=<your key>` (see [Get a TMDb key](#get-a-tmdb-key)). You can leave it empty to use the 25 sample movies.
3. Start everything:
   ```bash
   docker compose up --build
   ```
4. Wait until the log shows `TMDb sync: 200 movies imported` (or `Seeded 25 dummy movies`).
5. Open <http://localhost:8080/swagger-ui.html>.

If port 8080 or 5432 is already used on your machine, set `API_PORT` and/or `DB_PORT` in `.env` (for example `API_PORT=8081`) and use that port in all URLs below.

Stop it with `docker compose down`. Your data stays in a Docker volume. Use `docker compose down -v` to delete the data too.

## 2. Run it without Docker

You need Java 17. This uses a file database in `./data` (git-ignored).

1. *(Optional)* load your settings:
   ```bash
   set -a; . ./.env; set +a
   ```
2. Start:
   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=local'
   ```
3. Open <http://localhost:8080/swagger-ui.html>.

> If your `.env` has `FIREBASE_CREDENTIALS_PATH=/secrets/...`, that path only exists inside Docker. Without Docker, set it to the real file path (for example `$PWD/secrets/<file>.json`) or clear it. A wrong path stops the app at startup.

To start with fresh data, delete the `data` folder.

## 3. Settings

All settings are environment variables (put them in `.env`). Nothing secret is committed: `.env`, `secrets/`, `data/` and `google-services.json` are git-ignored.

| Variable | Default | What it does |
|---|---|---|
| `TMDB_API_KEY` | empty | TMDb v3 key. Empty means sample data. The key stays on the server. |
| `TMDB_SYNC_PAGES` | `10` | Pages to import (20 movies each, so 200). |
| `DB_USERNAME` / `DB_PASSWORD` | `movies` / `movies` | Database login (Docker). |
| `DB_PORT` | `5432` | Postgres port on your machine (Docker). |
| `API_PORT` | `8080` | API port on your machine (Docker). |
| `FIREBASE_CREDENTIALS_PATH` | empty | Firebase service-account file. Empty means push is only logged. |
| `FCM_TOPIC` | `movie-updates` | Topic the Android app subscribes to. |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | `admin` / `admin123` | Login for editing and the portal. **Change these outside local use.** |

### Get a TMDb key

1. Create a free account at <https://www.themoviedb.org>.
2. Go to Settings → API and request a key.
3. Put the **API Key (v3)** in `.env` as `TMDB_API_KEY=...`.

### Set up Firebase (for real push notifications)

1. Create a project at <https://console.firebase.google.com> and add your Android app (its package name).
2. Download `google-services.json` and put it in the Android app (do not commit it).
3. In Firebase: Project settings → Service accounts → **Generate new private key**. Save the file in the `secrets/` folder of this project.
4. In `.env` set the path **as seen inside Docker**:
   ```
   FIREBASE_CREDENTIALS_PATH=/secrets/<your file name>.json
   ```
5. Restart: `docker compose up -d`.

Without this the backend still works. When you edit a movie it only logs `FCM log-only mode ...`.

---

## 4. Where things are

Default port 8080 (use your `API_PORT` if you changed it).

| What | URL |
|---|---|
| Swagger UI (try the API) | <http://localhost:8080/swagger-ui.html> |
| API | <http://localhost:8080/api/movies> |
| Admin portal | <http://localhost:8080/admin/movies> |
| Health check | <http://localhost:8080/actuator/health> |
| From the Android emulator | `http://10.0.2.2:8080/api/` |

## 5. API

| Method | Path | Login needed | What it does |
|---|---|---|---|
| GET | `/api/movies?page=1&size=20` | no | Movies, most popular first |
| GET | `/api/movies/search?query=lion&page=1&size=20` | no | Search by title (case-insensitive, partial) |
| GET | `/api/movies/{id}` | no | One movie |
| PATCH | `/api/movies/{id}` | yes (Basic) | Edit a movie |

Paging: `page` starts at 1. `size` is 1 to 50. Values outside this are corrected. A blank search returns an empty page, not an error.

**A movie** (all names are snake_case):

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

A list or search returns:

```json
{ "page": 1, "results": [ { "...": "movies as above" } ], "total_pages": 10, "total_results": 200 }
```

**Edit a movie** — send only the fields you want to change (at least one):

```bash
curl -u admin:admin123 -X PATCH http://localhost:8080/api/movies/550 \
  -H 'Content-Type: application/json' \
  -d '{"title": "Fight Club (Edited)", "vote_average": 9.1}'
```

| Field | Rules |
|---|---|
| `title` | 1 to 255 characters, not blank |
| `overview` | up to 5000 characters |
| `poster_path` | text |
| `release_date` | `yyyy-MM-dd` |
| `vote_average` | 0 to 10 |
| `genres` | list of names, up to 10 |

**Errors** always look like this:

```json
{ "status": 404, "error": "Not Found", "message": "Movie with id 999 not found", "details": [], "timestamp": "2026-11-06T19:10:00Z" }
```

| Status | When |
|---|---|
| 400 | Invalid value (`details` lists `field: message`), empty edit, bad JSON, bad parameter |
| 401 | Missing or wrong login on an edit |
| 404 | Movie not found |
| 500 | Unexpected error (generic message, details only in the server log) |

### Other ways to call the API

- **Swagger UI:** click **Authorize**, enter the admin login, then use `PATCH /api/movies/{id}`.
- **Postman:** import `postman/MovieBackend.postman_collection.json` and set the `baseUrl` variable.
- **IntelliJ / VS Code:** open `api.http` (settings in `http-client.env.json`).

---

## 6. When is a push sent?

Only when an edit **really changes the content**. These are treated as *no change*, so nothing is saved and no push is sent:

- spaces at the start or end of a title, genre, overview or poster path
- repeated spaces inside a title or genre (`"Fight   Club"`)
- different line endings or trailing spaces in the overview
- an empty overview or poster path instead of a missing one
- the same genres in another order, or with blanks or duplicates
- the same rating (ignoring tiny rounding differences)

These **are** real changes: a different title (also capital letters), a new or removed genre, a different rating, date, overview or poster.

After a real change the movie is marked as *edited*, `updated_at` changes, and the push is sent **after** the database save succeeded. If sending fails, the edit still succeeds and the error is logged.

### What the push contains

The push is a **data message** to the topic `movie-updates`. Each movie field is its own key. All values are strings.

| Key | Example | Notes |
|---|---|---|
| `type` | `MOVIE_UPDATED` | Always this value |
| `movie_id` | `550` | |
| `updated_at` | `2026-11-06T19:10:00Z` | |
| `updated_fields` | `title,vote_average` | Fields that really changed |
| `title` | `Fight Club (Edited)` | |
| `overview` | `A ticking-time-bomb...` | Left out if the movie has none |
| `poster_path` | `/pB8B.jpg` | Left out if none |
| `backdrop_path` | `/hZko.jpg` | Left out if none |
| `release_date` | `1999-10-15` | Left out if none |
| `vote_average` | `9.1` | |
| `vote_count` | `27000` | |
| `popularity` | `61.4` | |
| `genres` | `["Drama","Thriller"]` | JSON list of names |
| `partial` | `true` | Only present if the message was too big. Then `overview` is missing and the app should fetch the movie. |

A key that is missing means the movie has no value for it. For the Android steps see [`docs/ANDROID_INTEGRATION.md`](docs/ANDROID_INTEGRATION.md).

---

## 7. Admin portal

1. Open <http://localhost:8080/admin/movies>. You are sent to the login page.
2. Sign in with `admin` / `admin123` (or your `ADMIN_USERNAME` / `ADMIN_PASSWORD`).
3. Use the search box to find a movie. Edited movies have an **Edited** badge.
4. Click **Edit**, change the fields and click **Save**.
5. Errors show next to the field. Genres are typed separated by commas (up to 10).

The portal can only edit. It cannot create or delete movies. Saving uses the same rules as the API, so an unchanged save sends no push.

## 8. Test the whole flow

1. Start the backend (section 1) with your Firebase file set up.
2. Run the Android app on an emulator **with Google Play** (or a real device). Make sure it uses `http://10.0.2.2:<API_PORT>/api/`.
3. Put the app in the background.
4. In Swagger UI, authorize and call `PATCH /api/movies/{id}` with a new title.
5. Check the backend log: `docker compose logs api | grep FCM` should show `FCM sent for movie ...`.
6. Open the app. The new title is there without a manual refresh.
7. Send the **same** title again with extra spaces. No new `FCM sent` line appears.
8. Restart the backend (`docker compose restart api`). The edited movie is still edited.

## 9. Run the tests

```bash
./gradlew test
```

## 10. Project layout

```
src/main/kotlin/com/example/trianglzmoviebackend/
├── movie/          movies: entity, repository, service, controller, DTOs; seed/ = sample data
├── tmdb/           client/ = TMDb HTTP client, sync/ = import on startup
├── notification/   builds and sends the FCM push
├── admin/          admin portal (Thymeleaf)
├── config/         settings, security, Firebase, API docs
└── common/         error format, paging envelope, exception handler
postman/            Postman collection
api.http            ready-made requests
```

How it is layered: controller (HTTP only) → service (rules, saving, events) → repository (database). The API never returns database entities, only DTOs.

## 11. Troubleshooting

| Problem | Fix |
|---|---|
| `port is already allocated` | Set `API_PORT` / `DB_PORT` in `.env` and restart |
| App stops at startup with a file-not-found error for Firebase | `FIREBASE_CREDENTIALS_PATH` is wrong. Inside Docker use `/secrets/<file>`; without Docker use the real path or clear it |
| Log says `FCM log-only mode` | No Firebase credentials are set, so nothing is really sent |
| `TMDb sync failed ... HTTP 401` | Wrong `TMDB_API_KEY`. The app keeps running with sample data |
| Emulator cannot reach the API | Use `http://10.0.2.2:<API_PORT>/api/`, not `localhost` |
| Edit returns 401 | Wrong admin login |
| Edit returns 200 but no push | The content did not really change (see section 6) |
| Push is sent but the phone gets nothing | Emulator without Google Play, app force-stopped, or the app is in a different Firebase project |
