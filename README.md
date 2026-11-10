# Reet Music Player

A native Android offline music player with a minimal white and sky-blue frosted-glass interface, responsive 6.7-inch-first layouts, animated now-playing screen and Media3 background playback.

## Technology
- Kotlin, Jetpack Compose and Material 3
- AndroidX Media3 ExoPlayer and MediaLibraryService
- Android MediaStore for local audio discovery and metadata
- Java 17, Gradle 8.14.3

## Features
- Offline library: Songs, New, Favourites, Albums, Artists and Folders
- Search, sorting, duplicate finder and library rescan
- Playback queue with reorder, remove and Play Next
- Shuffle, repeat modes, sleep timer and playback history
- M3U playlist import/export and local playlists
- Offline plain-text and timestamped LRC lyrics
- In-app song title, artist and album overrides; Android 11+ can request system approval to write MediaStore tags
- Custom album artwork with persisted document access
- Device-backed equalizer, bass boost and virtualizer when supported by the audio device
- Media session, lock-screen and notification controls
- Compact, medium and large home-screen widgets
- MediaLibraryService browsing structure and Android Auto media declaration
- Light-first white/sky-blue theme, dark mode, accent options and bundled wallpapers
- Offline-first: no account, streaming service, API key or network permission

## Android compatibility
- Minimum SDK: Android 7.0 (API 24)
- Compile SDK: Android 16 (API 36)
- Target SDK: Android 16 (API 36)
- Application ID: `com.reet.music`
- Current app version: **1.4.1** (versionCode 15)

## Build and APKs
GitHub Actions builds both debug and release APKs when the persistent signing keystore is configured. Open the repository's Actions tab, select the latest successful **Android Build** run, then download:
- `reet-android-16-release-apk` for the release APK
- `reet-android-16-debug-apk` for the debug APK

The release artifact is signed using the repository's configured signing key. Keep that keystore and its credentials safe; future updates must use the same signing identity to install over an existing release.

## Playback and car integration
Background playback and browsing use AndroidX Media3 `MediaLibraryService`. Android Auto media support is declared through `automotive_app_desc.xml`; actual car/head-unit compatibility still needs a physical Android Auto test.

## Device testing still recommended
Test equalizer behavior with music playing, widget resizing and actions across launchers, M3U import/export, metadata permission acceptance/decline, artwork persistence after restart, and Android Auto browsing on a compatible head unit.
