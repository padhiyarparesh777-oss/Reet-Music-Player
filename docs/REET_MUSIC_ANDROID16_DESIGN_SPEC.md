# Reet Music — Android 16+ Product & UI Specification

## Product direction
Premium offline-first Android music player, optimized first for 6.7-inch portrait phones and responsive to other display sizes. Preserve application ID `com.reet.music`, Kotlin + Jetpack Compose + Material 3, Media3 playback service and existing working capabilities.

## Visual system
- Default: pure white (#FFFFFF), sky blue (#29B6F6), pale blue (#EAF7FF).
- Charcoal primary text and neutral grey secondary text.
- Translucent white frosted-glass cards, subtle borders, restrained shadows and consistent rounded corners.
- Minimal professional typography, generous spacing and simple line icons.
- Avoid dark/neon styling as the default, excessive gradients and clutter.

## Screen inventory
Splash/onboarding; Home dashboard; Songs; Playlists; Albums; Artists; Library/folders; Now Playing; Queue; Equalizer; Song Info; Settings; Appearance; Mini Player; Android home-screen widgets; Notification and lock-screen player.

## Core functionality
- Android MediaStore local music discovery, metadata and artwork.
- Media3 ExoPlayer + MediaSessionService for background playback and system media controls.
- Play/pause, seek, next/previous, shuffle, repeat, queue reorder/remove/play-next, sleep timer and playback resume.
- Persistent favourites, playlists, sort preferences, appearance and supported audio settings.
- Real Equalizer/BassBoost/Virtualizer effects connected to the active audio session; report unsupported effects honestly.
- Gapless, crossfade and speed controls only where supported and tested.
- Scoped-storage-compliant access, MediaStore and Storage Access Framework for user-selected folders.
- Widgets connected to real playback actions and state.
- Android notification and foreground-service compliance, audio focus, Bluetooth/headphone and call interruptions.
- Versioned local backup/restore with overwrite confirmation.

## Android 16+ and adaptation
- Compile/target API 36; preserve current minimum SDK and app identity.
- Edge-to-edge, status/navigation insets, camera cutouts and predictive back.
- 6.7-inch-first responsive layout, other sizes and font scaling.
- Adaptive 60/90/120Hz animation; do not force refresh rate.
- Contextual permission requests and lifecycle-aware background work.

## Quality gates
Inspect existing code first and preserve working features. No dead buttons, fake settings, hardcoded demo library data or misleading success states. Test local files, playback service, lock-screen/notification controls, queue, playlists, favourites, equalizer, widgets, themes, backup/restore and permission-denial flows. Run Gradle build and tests; report results honestly. Do not claim features complete until implemented and verified.
