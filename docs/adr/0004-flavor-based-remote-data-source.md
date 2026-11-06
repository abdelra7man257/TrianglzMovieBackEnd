# 0004. Flavor-based remote data source switch in the app

**Status:** accepted (Android side is a separate project; this records the contract the backend serves)

## Context
Reviewers may not run the backend, so the app must keep working against TMDb directly. With the backend, the app should call it instead.

## Decision
The app defines a `MovieRemoteDataSource` interface with two implementations, `TmdbRemoteDataSource` and `CustomBackendRemoteDataSource`, bound in flavor-specific Hilt modules (`tmdb` and `custom` build flavors). Domain and use cases do not change.

The backend mirrors TMDb's envelope (`page`, `results`, `total_pages`, `total_results`) and snake_case field names, so the custom data source is a thin mapping layer. Genres are returned as names, not ids.

## Consequences
- Only the data layer changes in the app.
- The `custom` flavor needs cleartext HTTP allowed for `10.0.2.2` only, and the Firebase setup.
- The `tmdb` flavor keeps the app fully usable without the backend.
