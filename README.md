# SIGIL

SIGIL records study sessions started and ended by tapping an NFC tag. The Android app keeps completed sessions in a local outbox and syncs them to a Rails API. A web dashboard summarizes saved sessions.

## Components

- `android/` — Android NFC client and offline sync queue. See [Android setup](android/README.md).
- Rails API — validates completed sessions, calculates duration, and stores data in SQLite.
- Dashboard — session totals, average duration, 14-day activity, and recent sessions at `/dashboard`.

The phone reaches the API over Tailscale. The API receives completed sessions only; it does not process tap events or run automation.

## Run with Docker

From the project root, provide the Rails credentials key and start the service:

```sh
export RAILS_MASTER_KEY="$(tr -d '\n' < config/master.key)"
docker compose up --build -d
```

The API listens on port `3000`. Set `SIGIL_PORT` to change the host port:

```sh
SIGIL_PORT=8080 docker compose up --build -d
```

Check health with `curl http://localhost:3000/up`. Open `http://localhost:3000/dashboard` to view activity.

Useful commands:

```sh
docker compose logs -f sigil-api
docker compose down
```

Docker stores SQLite data in the `sigil_storage` volume. `docker compose down -v` deletes that data.

## API

`POST /sessions` accepts one completed session as JSON:

```json
{
  "client_session_id": "phone-generated-uuid",
  "started_at": "2026-09-26T14:00:00Z",
  "ended_at": "2026-09-26T14:45:00Z",
  "subject": "New Testament",
  "chapter": "Romans 8",
  "notes": "Reviewed the chapter outline",
  "metadata": { "focus_mode": true }
}
```

`client_session_id`, `started_at`, and `ended_at` are required. Subject, chapter, notes, and metadata are optional. The server calculates `duration_seconds` from the timestamps.

- `201 Created` — session saved.
- `200 OK` — duplicate session ID; safe to retry.
- `422 Unprocessable Entity` — invalid or missing values.

Example request:

```sh
curl --request POST http://localhost:3000/sessions \
  --header 'Content-Type: application/json' \
  --data '{"client_session_id":"phone-generated-uuid","started_at":"2026-09-26T14:00:00Z","ended_at":"2026-09-26T14:45:00Z"}'
```

## Development

Requirements: Ruby version from `.ruby-version`, Bundler, and (for Android) Android Studio or Gradle plus Android SDK.

Install dependencies and prepare the database:

```sh
bin/setup
```

Start Rails with `bin/dev`. Run checks with:

```sh
bin/rails test
bin/rubocop
bin/brakeman --quiet
```

The integration test for the complete API-to-dashboard path is `bin/rails test test/integration/vertical_slice_test.rb`. With the server running, its HTTP smoke check is:

```sh
SIGIL_URL=http://localhost:3000 bin/vertical_slice
```

## Android

Build and install instructions, NFC tag setup, and sync behavior are in [android/README.md](android/README.md). The preferred tag URI is `sigil://tap`. Configure the app with the Docker host's Tailscale address; do not expose its cleartext HTTP endpoint to public networks.
