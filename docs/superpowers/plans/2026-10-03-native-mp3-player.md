# Native MP3 Player Implementation Plan

**Goal:** Build a native Android MP3 player matching the approved dark main-screen design with folder-tree navigation, background playback, volume, and Luffy loudness control.

**Architecture:** A single AppCompat activity owns the library UI and connects to a Media3 MediaSessionService. SAF/DocumentFile handles persisted folder access, pure Java models handle ordering/grouping, and the service owns ExoPlayer plus LoudnessEnhancer.

**Tech Stack:** Java 17, Android SDK 37, Android Gradle Plugin 9.4, Media3 1.11.1, AppCompat, RecyclerView, DocumentFile, JUnit 4.

**Spec:** `docs/spec.md`

## Tasks
1. Domain ordering and queue rules with unit tests.
2. SAF library scanning and persisted roots.
3. Media3 playback service and Luffy command.
4. Approved dark main-screen UI and interactions.
5. GitHub Actions tests and APK artifact.
