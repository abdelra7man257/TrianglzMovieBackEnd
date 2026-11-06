# 0002. Own database filled from TMDb, not a pure proxy

**Status:** accepted

## Context
Edits must persist and survive restarts. A proxy that forwards to TMDb would lose every edit on the next request.

## Decision
Store movies in our own database (PostgreSQL, H2 for local). On startup, import popular movies from TMDb with the required filters (`include_adult=false`, `certification_country=US`, `certification.lte=PG-13`). The TMDb id is the primary key, so a re-sync updates the same row. Fall back to dummy data when no API key is set or TMDb fails.

A `locally_modified` flag marks admin-edited movies. A re-sync refreshes only their popularity and vote count, so edits are never overwritten.

## Consequences
- The TMDb key lives only on the server, not in the APK.
- Search and paging are served from our database.
- Data can be up to one sync old. A scheduled re-sync is a possible extension.
- Schema is managed by `ddl-auto=update` for now; Flyway migrations are a possible extension.
