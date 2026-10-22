# SIGIL API backend

SIGIL receives one completed study session from the Android client. Rails
validates the timestamps, calculates duration_seconds, and stores the record in
SQLite.

## Start with Docker

From the project directory on the Docker host:

    export RAILS_MASTER_KEY="$(tr -d '\n' < config/master.key)"
    docker compose up --build -d

The API is available on port 3000 by default. Override it with SIGIL_PORT:

    SIGIL_PORT=8080 docker compose up --build -d

Check the server:

    curl http://localhost:3000/up

The Android phone should call the Docker host through its Tailscale address,
for example http://100.x.y.z:3000/sessions.

Useful Docker commands:

    docker compose logs -f sigil-api
    docker compose down

The named sigil_storage volume keeps SQLite data when the container is removed.
Use docker compose down -v only when you intend to delete that database.

## Test the API

    curl --request POST http://localhost:3000/sessions \
      --header 'Content-Type: application/json' \
      --data '{
        "client_session_id": "phone-generated-uuid",
        "started_at": "2026-09-26T14:00:00Z",
        "ended_at": "2026-09-26T14:45:00Z",
        "subject": "New Testament",
        "chapter": "Romans 8",
        "notes": "Reviewed the chapter outline",
        "metadata": {
          "focus_mode": true,
          "lighting_profile": "study"
        }
      }'

A new session returns 201, a retry with the same client_session_id returns 200,
and invalid timestamps return 422.

## Rails code map

- config/routes.rb maps POST /sessions to the controller.
- app/controllers/study_sessions_controller.rb handles JSON, strong parameters,
  HTTP status codes, and idempotent retries.
- app/models/study_session.rb contains validations and the callback that
  calculates duration before saving.
- db/migrate/ describes database changes; the unique index protects against
  concurrent duplicate submissions.
- test/integration/study_sessions_test.rb tests the public API request flow.
- compose.yaml builds the Docker image, exposes the API, persists SQLite, and
  checks the /up health endpoint.

With Ruby and Bundler available, run:

    bin/rails db:migrate
    bin/rails test
    bin/rubocop
    bin/brakeman --quiet

## Vertical slice check

The Rails integration test covers the full request-to-dashboard path:

    bin/rails test test/integration/vertical_slice_test.rb

With the API running, the smoke command exercises the same path over HTTP:

    SIGIL_URL=http://localhost:3000 bin/vertical_slice

It submits one completed session, checks the server-calculated duration, retries
the same client session ID, and confirms the session appears on `/dashboard`.

## Dashboard

Open http://localhost:3000/dashboard from a browser that can reach the Docker
host. It shows total study time, session count, average duration, a 14-day
activity chart, and recent completed sessions.

## Android client

The Android MVP lives in android/. Read android/README.md for building the
APK and writing the NFC tag. The tag should contain the NDEF URI sigil://tap.
The client stores completed sessions locally before attempting synchronization,
so a temporary Tailscale outage does not lose study data.


