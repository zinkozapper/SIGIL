# SIGIL Android client

This is the first Pixel 9a client. It uses an NFC tag to toggle a local study
session, stores completed sessions in a local outbox, and submits them to the
Rails API when the network is available.

## Build and install

Open the android directory in Android Studio, or use a local Gradle installation.

Build with the Docker host's Tailscale address:

    gradle :app:assembleDebug -PsigilApiBaseUrl=http://100.x.y.z:3000/

Install on the phone:

    adb install -r app/build/outputs/apk/debug/app-debug.apk

The app intentionally allows cleartext HTTP because the first deployment uses
a private Tailscale network. Move the API to HTTPS before exposing it beyond
that private network.

## NFC tag contents

Write an NDEF URI record containing exactly:

    sigil://tap

NFC Tools and NXP TagWriter can write this as a custom URI. If your writer only
supports text records, write this exact text instead:

    SIGIL_TAP

The app recognizes both forms. The URI form is preferred because Android can
launch SIGIL directly when the tag is tapped.

## How the app works

- The first recognized tag tap saves a start timestamp locally.
- The second recognized tag tap creates one completed payload with a UUID.
- Subject, chapter, and notes are read when the second tap happens.
- The payload is placed in the outbox before any network request.
- WorkManager retries synchronization when connectivity returns.
- A duplicate API response is treated as success because Rails makes the
  client session ID idempotent.

The Android app does not send intermediate start or stop events. Only completed
sessions leave the phone.
