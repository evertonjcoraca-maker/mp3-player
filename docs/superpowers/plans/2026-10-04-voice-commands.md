# Everton MP3 Player Voice Commands Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add reliable hands-free Portuguese/English voice control that keeps working with the app minimized or the screen off, while preserving the existing playback order and manual controls.

**Architecture:** Consolidate playback ownership in the existing Media3 `PlaybackService`, then add a separate microphone foreground `VoiceCommandService`. Vosk handles the local `Player` wake word; Android `SpeechRecognizer` handles the command phrase; a pure-Java parser/matcher layer converts phrases into typed commands and deterministic library actions.

**Tech Stack:** Java 17, Android SDK 36, Media3 1.11.1, AppCompat, RecyclerView, DocumentFile, JUnit 4, Vosk Android 0.3.75 + English model 0.3.75.

**Spec:** `docs/superpowers/specs/2026-10-04-voice-commands-design.md`

## Global Constraints

- Keep existing MP3/WAV library ordering and next/previous behavior unless a voice command explicitly selects song/album/artist/shuffle.
- Manual buttons and voice commands must control the same playback state.
- Default command locale on each fresh app start is `en-US`; supported locales are `en-US` and `pt-BR`.
- Voice shutdown by `Player, encerrar comando de voz` persists OFF until the user manually taps the voice button.
- Voice volume acts on app playback volume, not Android/Bluetooth system volume.
- Volume delta is exactly 10 percentage points; max is 100%; zero is 0%; mute/unmute is a separate remembered state.
- No cloud assistant, TTS, boot auto-start, or stored voice recordings.

## Review Focus

- App process/activity recreation while music is playing: service queue/current item must survive UI recreation without spawning a second player.
- Weak or ambiguous named-media matches: do not choose a surprising result outside the deterministic priority rules.
- Microphone permission denied/revoked: voice state must become OFF cleanly while manual playback keeps working.
- Wake-word/command handoff errors: never leave two microphone captures active or the service permanently deaf.
- Volume zero versus mute: zero must not create an unmute restore point; mute must restore the exact previous non-muted level.

---

### Task 1: Typed Voice Commands and Parser

**Files:**
- Create: `app/src/main/java/com/evertoncoraca/mp3player/VoiceCommand.java`
- Create: `app/src/main/java/com/evertoncoraca/mp3player/VoiceCommandParser.java`
- Create: `app/src/test/java/com/evertoncoraca/mp3player/VoiceCommandParserTest.java`

**Interfaces:**
- Produces: `VoiceCommand` with `Type`, optional `query`, optional `value`, optional `localeTag`.
- Produces: `VoiceCommandParser.parse(String text) -> VoiceCommand` or `null` for unknown input.

- [ ] **Step 1: Write failing parser tests** for Portuguese/English aliases, `Player` prefix stripping, accents/case/punctuation normalization, query extraction, +10/-10, 0/100, mute/unmute, language switching, disable voice, and unknown phrases.
- [ ] **Step 2: Run** `./gradlew test --tests '*VoiceCommandParserTest'` and verify failure because parser/model do not exist.
- [ ] **Step 3: Implement** `VoiceCommand` and `VoiceCommandParser.parse(String)` with deterministic precedence: explicit song/album/artist/search before generic `toca/play <name>`.
- [ ] **Step 4: Run** the parser tests and verify PASS.
- [ ] **Step 5: Commit** `feat: add bilingual voice command parser`.

### Task 2: Library Matching and Queue Selection

**Files:**
- Create: `app/src/main/java/com/evertoncoraca/mp3player/LibraryMatcher.java`
- Create: `app/src/main/java/com/evertoncoraca/mp3player/LibrarySelection.java`
- Create: `app/src/test/java/com/evertoncoraca/mp3player/LibraryMatcherTest.java`

**Interfaces:**
- Consumes: `Track`, ordered `List<Track>`, active-root identity.
- Produces: `findSong`, `findAlbum`, `findArtist`, `resolveGeneric`, and `shuffleAll` returning `LibrarySelection(queue, startTrack)`.

- [ ] **Step 1: Write failing tests** for accent/case/extension normalization, exact-before-partial priority, generic priority (song → artist → album, exact before partial), stable order, active-root preference, ambiguous matches, and shuffle containing every track exactly once.
- [ ] **Step 2: Run** `./gradlew test --tests '*LibraryMatcherTest'` and verify failure.
- [ ] **Step 3: Implement** `LibraryMatcher` without Android dependencies so matching remains unit-testable.
- [ ] **Step 4: Run** matcher tests and verify PASS.
- [ ] **Step 5: Commit** `feat: add deterministic voice library matching`.

### Task 3: PlaybackService Becomes the Single Playback Owner

**Files:**
- Modify: `app/src/main/java/com/evertoncoraca/mp3player/PlaybackService.java`
- Modify: `app/src/main/java/com/evertoncoraca/mp3player/MediaTransportData.java`
- Modify: `app/src/main/java/com/evertoncoraca/mp3player/LibraryPrefs.java`
- Modify: `app/src/main/java/com/evertoncoraca/mp3player/MainActivity.java`
- Modify/Test: `app/src/test/java/com/evertoncoraca/mp3player/NativePlaybackQueueTest.java`
- Create: `app/src/test/java/com/evertoncoraca/mp3player/VoiceVolumeStateTest.java`

**Interfaces:**
- Produces MediaSession custom commands for queue loading, volume/Luffy, mute/unmute, and any library-selection action not expressible as a stock Player command.
- `MainActivity` becomes a controller/observer; it no longer owns `NativePlaybackEngine`.

- [ ] **Step 1: Extend queue/parity tests** to pin current start-track, next/previous, wrap behavior, and ordered completion semantics used by the existing app.
- [ ] **Step 2: Add failing pure-Java volume-state tests** for ±10 clamp, set 0/100, mute memory, unmute restore, repeated mute, and zero-not-mute.
- [ ] **Step 3: Run** `./gradlew test` and verify new tests fail before service changes.
- [ ] **Step 4: Move authoritative playback state into `PlaybackService`** using ExoPlayer/MediaSession; keep queue metadata and volume in the service and persist volume/Luffy through `LibraryPrefs`.
- [ ] **Step 5: Replace `MainActivity` playback calls** with a `MediaController`; observe current item/play state/position/volume and keep current UI behavior.
- [ ] **Step 6: Run** `./gradlew test` and `./gradlew assembleDebug`; verify all tests PASS and debug APK builds.
- [ ] **Step 7: Commit** `refactor: make playback service authoritative`.

### Task 4: Voice Preferences, Permissions, and Foreground Service Shell

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/evertoncoraca/mp3player/LibraryPrefs.java`
- Create: `app/src/main/java/com/evertoncoraca/mp3player/VoiceCommandService.java`
- Create: `app/src/main/java/com/evertoncoraca/mp3player/VoiceState.java`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces persisted `voiceEnabled()` / `setVoiceEnabled(boolean)`.
- Produces start/stop service actions and observable ON/OFF state.

- [ ] **Step 1: Add manifest declarations** for `RECORD_AUDIO`, `FOREGROUND_SERVICE_MICROPHONE`, and `VoiceCommandService` with `foregroundServiceType="microphone"`.
- [ ] **Step 2: Implement voice preference/state** with first-use default ON only after permission grant and persistent explicit OFF after voice shutdown.
- [ ] **Step 3: Implement service shell** that creates a foreground notification, exposes start/stop actions, and releases resources on stop.
- [ ] **Step 4: Run** `./gradlew assembleDebug`; verify manifest merge/build PASS.
- [ ] **Step 5: Commit** `feat: add voice foreground service shell`.

### Task 5: Local Wake Word with Vosk

**Files:**
- Modify: `app/build.gradle`
- Create: `app/src/main/java/com/evertoncoraca/mp3player/WakeWordRecognizer.java`
- Create: `app/src/main/java/com/evertoncoraca/mp3player/VoskWakeWordRecognizer.java`
- Modify: `app/src/main/java/com/evertoncoraca/mp3player/VoiceCommandService.java`

**Interfaces:**
- `WakeWordRecognizer.start(Listener)`, `stop()`, `release()`.
- Listener emits wake-word detection and optional trailing text.

- [ ] **Step 1: Add dependencies** `com.alphacephei:vosk-android:0.3.75` and `com.alphacephei:vosk-model-en:0.3.75` from Maven Central.
- [ ] **Step 2: Implement Vosk recognizer** with a restricted grammar centered on `player`; keep the implementation behind `WakeWordRecognizer`.
- [ ] **Step 3: Integrate lifecycle** so the service starts one wake-word capture, stops it before full command recognition, and resumes it after completion/error.
- [ ] **Step 4: Add guards** preventing overlapping recognizers and ensure release on service shutdown.
- [ ] **Step 5: Run** `./gradlew test` and `./gradlew assembleDebug`; verify PASS.
- [ ] **Step 6: Commit** `feat: add offline player wake word`.

### Task 6: Android Speech Recognition and Command Execution

**Files:**
- Create: `app/src/main/java/com/evertoncoraca/mp3player/AndroidCommandRecognizer.java`
- Create: `app/src/main/java/com/evertoncoraca/mp3player/VoiceCommandExecutor.java`
- Create: `app/src/main/java/com/evertoncoraca/mp3player/LibraryRepository.java`
- Modify: `app/src/main/java/com/evertoncoraca/mp3player/VoiceCommandService.java`
- Modify: `app/src/main/java/com/evertoncoraca/mp3player/PlaybackService.java`

**Interfaces:**
- `AndroidCommandRecognizer.listen(String localeTag, Callback)`; callback returns best recognized phrase or error.
- `VoiceCommandExecutor.execute(VoiceCommand)` routes transport/volume through MediaSession and named-media actions through `LibraryRepository` + `LibraryMatcher`.
- `LibraryRepository` scans/caches all persisted roots in stable root order.

- [ ] **Step 1: Implement command recognizer** using free-form speech, current locale, and API-34 language detection/switching only when supported.
- [ ] **Step 2: Implement library repository** reusing `LibraryScanner`, including all registered roots and active-root preference metadata.
- [ ] **Step 3: Implement executor** for play/resume, pause, stop, next, previous, ±10, 0/100, mute/unmute, song/album/artist, generic play, search, shuffle, language change, and disable voice.
- [ ] **Step 4: Ensure `DISABLE_VOICE`** persists OFF, stops recognition and foreground voice service, but leaves music playback untouched.
- [ ] **Step 5: Run** `./gradlew test` and `./gradlew assembleDebug`; verify PASS.
- [ ] **Step 6: Commit** `feat: execute voice commands through media session`.

### Task 7: Main-Screen Voice Toggle and UI Synchronization

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/java/com/evertoncoraca/mp3player/MainActivity.java`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Manual toggle starts voice only from a visible Activity after microphone permission is granted.
- Activity observes service voice ON/OFF state and playback/search state.

- [ ] **Step 1: Add a visible voice button** to the top bar; ON and OFF must be visually distinct and the button never disappears.
- [ ] **Step 2: Add runtime microphone-permission flow**; denial leaves voice OFF and manual music controls available.
- [ ] **Step 3: Wire manual toggle** to start/stop `VoiceCommandService`; manual re-enable is the only way back after explicit voice shutdown.
- [ ] **Step 4: Synchronize voice-driven UI changes** for play state, track metadata, progress, volume slider/value, and pending search query/results.
- [ ] **Step 5: Run** `./gradlew assembleDebug`; verify PASS.
- [ ] **Step 6: Commit** `feat: add voice toggle and synchronized player UI`.

### Task 8: End-to-End Verification and Cleanup

**Files:**
- Modify/remove: `app/src/main/java/com/evertoncoraca/mp3player/NativePlaybackEngine.java` only after service parity is confirmed.
- Modify: `README.md`
- Modify CI workflow if required for new dependency/build checks.

**Interfaces:**
- Final app has one authoritative player and one independently switchable voice service.

- [ ] **Step 1: Run full automated checks** with `./gradlew test` and `./gradlew assembleDebug`; both must PASS.
- [ ] **Step 2: Perform real-device checks**: background/screen-off next, pause/resume, volume ±10/full/zero, mute/unmute, named song/album/artist, shuffle-all, language switching, voice shutdown, manual re-enable, and buttons with voice ON/OFF.
- [ ] **Step 3: Verify failure cases**: denied/revoked microphone permission, unknown phrase, recognizer error, ambiguous media name, Activity recreation during playback.
- [ ] **Step 4: Remove `NativePlaybackEngine`** only if no production references remain and all parity/device checks pass.
- [ ] **Step 5: Update README** with voice permission, `Player` wake word, background notification behavior, command-language switch, and voice OFF/re-enable behavior.
- [ ] **Step 6: Re-run** `./gradlew test` and `./gradlew assembleDebug`; verify PASS.
- [ ] **Step 7: Commit** `feat: complete background voice control`.
