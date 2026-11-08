# Reet — Offline Music Player

A native Android music player with a clean white-glass interface and soft rose accents.

## Stack
- Kotlin
- Jetpack Compose + Material 3
- AndroidX Media3 ExoPlayer
- Android MediaStore for local audio discovery

## Current starter scope
- Runtime permission request for local audio
- Scan songs stored on the device
- Display a local music library
- Play, pause, previous and next controls
- Offline-first: no account, streaming service or API key

## Build
Open the repository in Android Studio, sync Gradle, then run the `app` configuration.

- App name: Reet
- Application ID: `com.reet.music`
- Minimum Android: 7.0 (API 24)
- Target SDK: 35

## Roadmap
- Background playback with MediaSessionService and notification controls
- Album artwork, search and sorting
- Favorites and playlists persisted with Room
- Full now-playing screen, seek bar and queue
- Equalizer integration where supported by the device


## CI
GitHub Actions builds a debug APK on every push to `main`.
