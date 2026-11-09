# Composer for Android (native, Kotlin)

Jetpack Compose skeleton for a native port of Better Lyrics Composer.
The web app in the repo root is untouched.

## Build locally
Needs JDK 17 and the Android SDK, then: `gradle assembleDebug` (or open `android/` in Android Studio).
APK: `app/build/outputs/apk/debug/app-debug.apk`

## Build on GitHub
Push to `master`/`main` (or run Actions -> Android APK -> Run workflow).
Download the APK from the run's Artifacts section.

## Status
- Phase 1 (done): audio import via the system file picker, Media3/ExoPlayer playback, seek bar, ±5 s skip.
- Phase 2 (done): lyrics model, TTML writer and parser, LRC/TXT import, Edit tab, TTML preview on the Export tab.
- Phase 3 (done): tap-to-sync (line and word modes, undo, end-line), save/share/copy TTML.
- Phase 4 (done): timeline editor with draggable word and line boundaries, zoom, nudge buttons.
