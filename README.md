# Reet — Offline Music Player

A native Android offline music player with a soft rose and frosted-glass interface.

## Technology
- Kotlin + Jetpack Compose + Material 3
- AndroidX Media3 ExoPlayer
- Android MediaStore for local audio discovery
- Java 17 / Kotlin JVM target 17

## Current features
- Local audio permission and device library scan
- Tracks, Favourite, Recent and Folder tabs
- Search by song, artist and album
- Play, pause, previous, next and seek controls
- Favorites and recent history stored locally
- Offline-first: no account, streaming service, API key or Internet permission

## Android compatibility
- Minimum SDK: Android 7.0 (API 24)
- Compile SDK: Android 16 (API 36)
- Target SDK: Android 16 (API 36)
- Application ID: `com.reet.music`

## Build
Open the repository in Android Studio, sync Gradle and run the `app` configuration.

For CI, GitHub Actions installs Java 17, Gradle 8.14.3 and Android SDK 36, then builds a debug APK. The APK is published as the `reet-android-16-debug-apk` workflow artifact when the build succeeds.

## Playback architecture
- Background playback runs in Media3 MediaSessionService, separate from the Activity lifecycle.
- Lock-screen and notification controls are provided by the MediaSession.

## Planned improvements
- Album artwork and improved now-playing screen
- Playlists, sorting and richer library management
- Optional equalizer integration where supported by the device
