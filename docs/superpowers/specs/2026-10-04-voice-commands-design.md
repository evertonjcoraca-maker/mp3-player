# Everton MP3 Player — Voice Commands Design

**Date:** 2026-10-04  
**Status:** Draft for user review  
**Branch:** `feat/native-mp3-player`

## 1. Goal

Add hands-free voice control to the existing Android MP3 player, with the main use case being operation in a car without touching the screen. Manual controls must continue to work normally while voice control is enabled.

The player must keep the current playback ordering behavior: a selected track continues through its existing queue/album order and then on to the next album according to the library order already used by the app. Voice control must not change that ordering unless the user explicitly requests another album, artist, song, or shuffle-all mode.

## 2. User-visible behavior

### Voice toggle

- Add a microphone/voice-command toggle button to the main screen.
- Voice commands are enabled by default on first use after microphone permission is granted.
- Active state: button remains visible and shows voice control as ON.
- Inactive state: button remains visible and shows voice control as OFF.
- Saying **“Player, encerrar comando de voz”** stops voice listening, stops the microphone foreground service, and changes the button to OFF.
- After an explicit voice shutdown, re-enabling voice requires tapping the button manually.
- The explicit OFF state is persisted so reopening the app does not silently re-enable the microphone. Manual re-enable sets it ON again.

### Background behavior

When voice control is ON, it must continue working when:

- the app is minimized;
- the screen is off;
- music is playing in the background.

A foreground-service notification indicates that voice control is active. Turning voice control OFF removes the voice-listening notification. Music playback may continue independently.

The service is started while the app is in the foreground, satisfying Android restrictions for microphone foreground services. It is not automatically started after device reboot; the user must open the app first.

## 3. Voice architecture

Use a two-stage pipeline so the full speech recognizer is not listening continuously.

### Stage A — local wake word

- A dedicated `VoiceCommandService` runs as a foreground service with microphone type.
- It continuously listens locally for the wake word **“Player”**.
- Wake-word detection must be offline and must not require a cloud API key.
- Initial implementation: Vosk Android with a small on-device English acoustic model and a restricted keyword grammar centered on `player`.
- The wake-word engine is wrapped behind a small `WakeWordRecognizer` interface so it can be replaced later without changing command logic.

### Stage B — command recognition

After detecting **“Player”**:

1. temporarily release/pause the local wake-word microphone capture;
2. invoke Android `SpeechRecognizer` for the command phrase;
3. parse the recognized text into a typed command;
4. execute it;
5. return to local wake-word listening.

The implementation must also support a single utterance such as **“Player, próxima música”** by preserving/processing text that follows the wake word when available; if only “Player” is detected, the command recognizer opens a short command-listening window.

### Language handling

- Default command language after a fresh app start is English (`en-US`).
- Supported command languages are English (`en-US`) and Brazilian Portuguese (`pt-BR`).
- On Android API 34+, request automatic language detection/switching restricted to English and Portuguese when the installed recognizer supports it.
- On devices/recognizers that do not support automatic language switching, use the currently selected command locale.
- Language change commands are:
  - **“Player, linguagem português”** / **“Player, language Portuguese”** → set Portuguese.
  - **“Player, linguagem inglês”** / **“Player, language English”** → set English.
- The parser accepts both Portuguese and English aliases whenever the recognizer returns them.
- The selected language applies to the current running session; a fresh app start returns to English as the default.

## 4. Command grammar

The wake word **“Player”** precedes commands. The parser normalizes case, accents where appropriate, punctuation, and common filler words. Short natural variants are accepted.

### Transport

| Intent | Portuguese examples | English examples |
| --- | --- | --- |
| Play/resume | `Player, tocar música`, `Player, toca`, `Player, continuar` | `Player, play music`, `Player, play`, `Player, resume` |
| Next | `Player, próxima música`, `Player, próxima` | `Player, next song`, `Player, next` |
| Previous | `Player, música anterior`, `Player, anterior` | `Player, previous song`, `Player, previous` |
| Pause | `Player, pausar música`, `Player, pausa` | `Player, pause music`, `Player, pause` |
| Stop | `Player, parar música`, `Player, parar` | `Player, stop music`, `Player, stop` |

Behavior:

- Play/resume resumes the current track if paused.
- If there is a current queue but playback was stopped, play restarts the current track from the beginning.
- If there is no current track, play starts the first track of the active library root using normal library order.
- Pause preserves current position.
- Stop preserves the queue/current track but resets the current track to the beginning.
- Next/previous use the same queue behavior as the existing player.

### Volume

Voice volume controls modify the **same app playback volume** represented by the player volume slider; they do not directly change the Android/Bluetooth system volume.

| Intent | Portuguese examples | English examples | Effect |
| --- | --- | --- | --- |
| Raise a little | `Player, aumentar um pouco o volume`, `Player, aumenta volume` | `Player, volume up`, `Player, increase volume` | +10 percentage points |
| Lower a little | `Player, diminuir um pouco o volume`, `Player, diminui volume` | `Player, volume down`, `Player, decrease volume` | -10 percentage points |
| Maximum | `Player, aumente todo o volume`, `Player, volume máximo` | `Player, full volume`, `Player, maximum volume` | set to 100% |
| Zero | `Player, diminua todo o volume`, `Player, volume zero` | `Player, zero volume` | set to 0% |
| Mute | `Player, mutar volume`, `Player, mudo` | `Player, mute`, `Player, mute volume` | temporary mute, remember previous level |
| Restore | `Player, restaurar volume` | `Player, unmute`, `Player, restore volume` | restore level saved by mute |

Rules:

- Increment/decrement clamps to 0–100%.
- Mute is distinct from setting volume to zero. `mute` remembers the pre-mute level; `restore/unmute` restores that level.
- `volume zero` changes the actual saved app volume to 0% and does not create a mute-restore state.
- Slider UI and stored preferences must immediately reflect voice-driven changes.

### Library selection and search

| Intent | Portuguese | English |
| --- | --- | --- |
| Search song | `Player, buscar música <nome>` | `Player, search song <name>` |
| Play song | `Player, tocar música <nome>` | `Player, play song <name>` |
| Play album | `Player, tocar álbum <nome>` | `Player, play album <name>` |
| Play artist | `Player, tocar artista <nome>` | `Player, play artist <name>` |
| Generic play | `Player, toca <nome>` | `Player, play <name>` |
| Shuffle all | `Player, tocar todas as músicas aleatórias` | `Player, shuffle all songs` |

Matching behavior:

- Build/search across all registered library roots.
- Normalize case, accents, punctuation, file extension, and leading/trailing whitespace for matching.
- Explicit `song`, `album`, or `artist` commands only search that category.
- Generic `toca/play <name>` resolution priority:
  1. exact normalized song title;
  2. exact normalized artist;
  3. exact normalized album;
  4. partial song title;
  5. partial artist;
  6. partial album.
- Ties use stable existing library order, preferring an exact match in the active root before other roots.
- Playing an album queues all tracks from that album in normal library order.
- Playing an artist queues that artist’s matching tracks in normal library order.
- Shuffle-all collects all playable tracks from all registered roots, shuffles the queue once, starts the first item, and continues through that shuffled queue.
- `buscar música <nome>` does not start playback. If the activity is visible it opens/populates the existing search UI. If the activity is not visible, it stores a pending search query and applies it the next time the activity becomes visible.

## 5. Playback-service consolidation

### Current implementation issue

The repository currently contains a Media3 `PlaybackService`, but `MainActivity` is still directly using `NativePlaybackEngine`. Background voice commands cannot reliably control an Activity-owned player if the Activity is stopped or destroyed.

### Required architecture

Make `PlaybackService`/`MediaSession` the single owner of playback state. Both the UI and `VoiceCommandService` control the same service through Media3 commands/controller APIs.

Preserve the existing behavior by moving or reproducing these semantics in the service layer:

- ordered queue prepared from `LibraryNode`/library order;
- next/previous behavior;
- repeat/continue behavior currently used for the active library;
- saved app volume;
- Luffy loudness setting;
- track/artist/album metadata visible to UI;
- current position and play/pause state.

`NativePlaybackEngine` should no longer be the authoritative player once parity is verified. It can then be removed or retained only temporarily during migration tests.

This consolidation is required so voice commands still work with the Activity absent while preserving a single queue and a single current track.

## 6. Command execution boundary

Introduce a small command model, for example:

- `PLAY`
- `NEXT`
- `PREVIOUS`
- `PAUSE`
- `STOP`
- `VOLUME_DELTA(+10/-10)`
- `VOLUME_SET(0/100)`
- `MUTE`
- `UNMUTE`
- `PLAY_SONG(query)`
- `PLAY_ALBUM(query)`
- `PLAY_ARTIST(query)`
- `SEARCH_SONG(query)`
- `SHUFFLE_ALL`
- `SET_LANGUAGE(locale)`
- `DISABLE_VOICE`

`VoiceCommandParser` converts recognized text to this command model. Command parsing must be unit-testable without Android audio APIs.

`VoiceCommandExecutor` applies commands through the playback/media-session layer and library/search layer. Recognition code must not directly manipulate `MainActivity` widgets.

## 7. Android permissions and service declarations

Update the manifest for voice support:

- `android.permission.RECORD_AUDIO`
- `android.permission.FOREGROUND_SERVICE_MICROPHONE`
- existing foreground/media playback permissions remain
- `VoiceCommandService` declared with `android:foregroundServiceType="microphone"`

At runtime:

- request microphone permission while the Activity is visible;
- do not start microphone capture if permission is denied;
- request notification permission where applicable, but voice-service correctness must not depend on a user granting optional notification display permission beyond Android foreground-service requirements.

## 8. Reliability and safety behavior

- Ignore recognition results that do not map to a known command.
- Do not guess a track/album/artist when the match is weak or ambiguous beyond the deterministic matching rules above.
- A recognition error returns the service to wake-word listening rather than disabling voice control.
- If Android revokes microphone access or the recognizer becomes unavailable, set voice state to OFF and surface a clear status when the Activity is visible.
- Prevent multiple overlapping recognition sessions.
- Release microphone, recognizer, model, and executor resources on service shutdown.
- No voice audio recordings are stored by the app.

## 9. UI state synchronization

The Activity observes voice service state and playback state rather than owning them.

At minimum the UI must update when voice commands change:

- play/pause state;
- current track title/artist/cover;
- progress where available;
- volume slider/value;
- microphone toggle ON/OFF;
- search query/results when `SEARCH_SONG` is invoked and Activity is visible.

Manual buttons continue to send the equivalent playback commands and work whether voice control is ON or OFF.

## 10. Tests / acceptance criteria

### Parser tests

Cover Portuguese and English aliases, natural short variants, normalization, parameter extraction, and unknown phrases.

### Library-match tests

Cover exact/partial song, artist, album, generic resolution priority, stable ordering, and shuffle queue creation.

### Volume tests

Cover +10/-10 clamping, 100%, 0%, mute memory, unmute restore, and distinction between mute and zero-volume.

### Playback parity tests

Verify service-owned playback preserves the current queue ordering and next/previous semantics before removing Activity-owned playback.

### Device acceptance checks

On a real Android device:

1. grant microphone permission;
2. verify voice button ON;
3. minimize app and issue `Player, próxima música`;
4. turn screen off and issue pause/resume/next commands;
5. verify 10% volume changes and full/zero volume;
6. verify mute/unmute restoration;
7. play a named song, album, and artist;
8. run shuffle-all;
9. switch Portuguese/English and test commands;
10. say `Player, encerrar comando de voz` and verify listening stops/button becomes OFF;
11. verify voice cannot be reactivated by speech while OFF;
12. manually tap the button and verify listening resumes;
13. verify all screen buttons continue to work while voice is ON and while it is OFF.

## 11. Non-goals for this version

- No cloud assistant integration.
- No always-listening full-vocabulary cloud transcription.
- No custom TTS spoken responses.
- No automatic start after device boot.
- No changes to the visual theme beyond adding/reflecting the voice toggle and status.
- No change to the existing library ordering rules except when the user explicitly requests album/artist/song/shuffle playback.
