# SIGIL Android client

This is the first Pixel 9a client. It starts and stops a local study session
with on-screen buttons or an NFC tag, stores completed sessions in a local
outbox, and submits them to the Rails API when the network is available.

## Build and install

Open the android directory in Android Studio, or use a local Gradle installation.

Build the app:

    gradle :app:assembleDebug

Install on the phone:

    adb install -r app/build/outputs/apk/debug/app-debug.apk

The app intentionally allows cleartext HTTP because the first deployment uses
a private Tailscale network. Move the API to HTTPS before exposing it beyond
that private network.

On the main screen with no session in progress, enter the server address and
port in the separate sync fields (for example, `100.x.y.z` and `3000`). Save
them to sync pending sessions. The address remains saved across app restarts.
HTTP is used unless you enter an `https://` URL.
Use **Test connection** to check the entered address without saving it. The
button checks the server's `/up` health endpoint; save the address separately
to use it for syncing.

## Optional NFC tag contents

Write an NDEF URI record containing exactly:

    sigil://tap

NFC Tools and NXP TagWriter can write this as a custom URI. If your writer only
supports text records, write this exact text instead:

    SIGIL_TAP

The app recognizes both forms. The URI form is preferred because Android can
launch SIGIL directly when the tag is tapped.

## How the app works

- Press Start or tap a recognized tag to save a start timestamp locally.
- Pause freezes the timer; Resume restarts it. Paused time is excluded from the
  recorded duration, including when a session is stopped while paused.
  After a device reboot, timing uses the device clock until the session ends.
- Press Stop or tap the tag again to stop the timer. Enter optional subject,
  chapter, and notes, then submit with the button or another tag tap.
- The completed payload is placed in the outbox before any network request.
- WorkManager retries synchronization when connectivity returns.
- A duplicate API response is treated as success because Rails makes the
  client session ID idempotent.
- Cancel discards an active or stopped session. On the sync status screen, it
  also returns to the main screen. It does not undo a submitted session.

The Android app does not send intermediate start or stop events. Only completed
sessions leave the phone.
