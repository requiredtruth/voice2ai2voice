# Voice → AI → Voice

A small native Android app. Tap **Tap to speak**, say a question, and hear the AI's final answer. The normal voice button and **Send text as a new conversation** always start fresh. Use **Convo mode** to keep talking with context, or **Talk to yourself** for an automatic conversation.

## Install and connect

Download `voice2ai2voice.apk` from [Releases](https://github.com/requiredtruth/voice2ai2voice/releases/latest). Requires Android 8.0 or newer. Allow installation from your browser/file manager when Android asks.

Open **Server settings** and enter:

- Server base URL, such as `https://your-server.example/v1`. Full `/v1/chat/completions` URLs also work.
- Exact model ID served by your endpoint.
- API key, if required. Local servers can leave this blank.

For a LAN server, use its LAN address, not `localhost` (which means your phone). HTTP requires explicitly enabling the trusted-local-server option. HTTPS is the default.

Grant microphone permission. Android speech recognition transcribes your voice, the transcript is sent automatically, and Android text-to-speech reads the final answer. You can type and send instead, replay an answer, or use the main button to cancel. Leaving the app stops recording, requests, and playback.

## Conversation controls

- **Convo mode**: speak your opening message. After the entire AI answer finishes playing, the microphone opens again for your reply. Every request includes all user and assistant messages from this session in order.
- **Talk to yourself**: speak the opening message once. After the first answer, the AI generates and speaks a user follow-up, answers it, and continues alternating both sides. No further microphone input is needed. Generated follow-ups are stored as user messages; answers are assistant messages. The complete conversation is sent with each request.
- Tap the active mode button again or **STOP** to end it. STOP cancels recording, network requests, playback, and automatic continuation. Starting another mode starts a fresh session.
- Leaving the app, rotating it, recognition errors/silence, server errors (including a full server context window), or playback failures stop the loop. History is held only in memory and cleared when the session ends. History is never silently trimmed. Self-talk keeps making server requests until stopped and may incur server usage costs.

## Reasoning handling

Only `choices[0].message.content` is used. Separate `reasoning`/`reasoning_content` fields are ignored; text content parts are supported. Tagged `<think>`, `<thinking>`, `<analysis>`, and `<reasoning>` blocks are removed, including unfinished blocks. Supported Harmony analysis/final markers are handled. Reasoning-only, empty, or output-limit-truncated responses produce an error instead of voice. Untagged reasoning embedded by a server into ordinary answer text cannot be reliably distinguished; use a correctly configured model/template. The system instruction requests a concise final answer. The request reserves 4,096 output tokens.

## Privacy

No chat database, analytics, or audio files. The current transcript and answer remain on screen while the app is open. API keys are encrypted using Android Keystore; app backup is disabled. Android's speech recognition or voice engine may use its own network services. The AI server receives recognized text, a short system instruction, and your configured model ID. Convo and self-talk modes also send every message in the active session; self-talk adds a temporary instruction when generating the user side. API keys are sent only to the configured endpoint; HTTP redirects are disabled.

## Build and releases

JDK 17, Gradle 8.11.1, Android SDK 35:

```sh
gradle :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

Main pushes run tests, release lint, build the signed APK, and publish a versioned GitHub release with APK, source ZIP, specification, and SHA-256 checksum. The sideload APK uses the repository's CI-generated Android development signing key, kept in an Actions artifact (never attached to a release). Public repository artifacts are downloadable by signed-in GitHub users; this development key is for sideload testing, not production distribution. Each run restores and refreshes that key for consistent updates. Repository administrators should securely back up the `android-signing-key` Actions artifact within 90 days and migrate to a dedicated production signing secret for long-term distribution. If it expires after a release, automation fails rather than publishing an incompatible update.

Automated tests cover complete conversation history, self-talk user/assistant payloads, mode resets, new-conversation payloads, URL validation, reasoning filtering, empty/truncated responses, and long speech chunks. Hardware speech recognition, permission UI, voice quality, and real server authentication require device testing.
