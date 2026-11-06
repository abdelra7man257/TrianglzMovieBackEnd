# 0003. FCM topic + data message, sent after commit

**Status:** accepted

## Context
An edit must make every installed app refresh its cache, in the foreground and the background, and the push must never announce data that was not saved.

## Decision
- Send to the topic `movie-updates` instead of device tokens, so no token storage or registration endpoint is needed.
- Send a **data-only** message (no `notification` block) so the app's `onMessageReceived` always runs and decides what to show.
- The payload carries only `type`, `movie_id`, `title`, `updated_at`; the app re-fetches the movie by id.
- `MovieService` publishes a `MovieUpdatedEvent`; `MovieUpdatedPushNotifier` listens with `@TransactionalEventListener(AFTER_COMMIT)`.
- A failed send is logged and does not fail the edit. Without credentials the notifier runs in log-only mode.
- Use the plain JDK HTTP transport for the Firebase SDK. Spring's dependency management upgrades Apache httpclient5, which made the SDK decompress responses twice ("Not in GZIP format").

## Consequences
- A rolled-back edit sends nothing, and a push never arrives before its data.
- Every installed app gets every update. Targeted pushes would need a device-token endpoint (possible extension).
- The event lives in the `movie` package, because `movie` publishes it and `notification` depends on `movie`, not the other way round.
