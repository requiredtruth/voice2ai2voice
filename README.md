# Voice → AI → Voice

A small native Android app. Tap **Tap to speak**, say a question, and hear the AI's final answer. Every request starts a **new conversation** with no previous questions attached.

## Install and connect

Download `voice2ai2voice.apk` from [Releases](https://github.com/requiredtruth/voice2ai2voice/releases/latest). Requires Android 8.0 or newer. Allow installation from your browser/file manager when Android asks.

Open **Server settings** and enter:

- Server base URL, such as `https://your-server.example/v1`. Full `/v1/chat/completions` URLs also work.
- Exact model ID served by your endpoint.
- API key, if required. Local servers can leave this blank.

For a LAN server, use its LAN address, not `localhost` (which means your phone). HTTP requires explicitly enabling the trusted-local-server option. HTTPS is the default.

Grant microphone permission. Android speech recognition transcribes your voice, the transcript is sent automatically, and Android text-to-speech reads the final answer. You can type and send instead, replay an answer, or use the main button to cancel. Leaving the app stops recording, requests, and playback.

## Reasoning handling

Only `choices[0].message.content` is used. Separate `reasoning`/`reasoning_content` fields are ignored; text content parts are supported. Tagged `<think>`, `<thinking>`, `<analysis>`, and `<reasoning>` blocks are removed, including unfinished blocks. Supported Harmony analysis/final markers are handled. Reasoning-only, empty, or output-limit-truncated responses produce an error instead of voice. Untagged reasoning embedded by a server into ordinary answer text cannot be reliably distinguished; use a correctly configured model/template. The system instruction requests a concise final answer. The request reserves 4,096 output tokens.

## Privacy

No chat database, analytics, or audio files. The current transcript and answer remain on screen while the app is open. API keys are encrypted using Android Keystore; app backup is disabled. Android's speech recognition or voice engine may use its own network services. The AI server receives recognized text, a short system instruction, and your configured model ID. API keys are sent only to the configured endpoint; HTTP redirects are disabled.

## Build and releases

JDK 17, Gradle 8.11.1, Android SDK 35:

```sh
gradle :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

Main pushes run tests, release lint, build the signed APK, and publish a versioned GitHub release with APK, source ZIP, specification, and SHA-256 checksum. The sideload APK uses the repository's CI-generated Android development signing key, kept in an Actions artifact (never attached to a release). Public repository artifacts are downloadable by signed-in GitHub users; this development key is for sideload testing, not production distribution. Each run restores and refreshes that key for consistent updates. Repository administrators should securely back up the `android-signing-key` Actions artifact within 90 days and migrate to a dedicated production signing secret for long-term distribution. If it expires after a release, automation fails rather than publishing an incompatible update.

Automated tests cover new-conversation payloads, URL validation, reasoning filtering, empty/truncated responses, and long speech chunks. Hardware speech recognition, permission UI, voice quality, and real server authentication require device testing.
