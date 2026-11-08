# Voice to AI to Voice specification

## Scope
Native Java Android application, Android 8.0+ (API 26), target API 35. No local LLM or Q-Manifold dependency.

## Primary flow
1. Configure OpenAI-compatible chat endpoint, model ID, and optional API key.
2. Tap to speak and grant microphone permission.
3. Android SpeechRecognizer produces a final transcript; partial results appear while listening.
4. Automatically POST a non-streaming request with exactly one system message and the current user text. No prior conversation is attached.
5. Parse the first assistant choice. Ignore separate reasoning fields and remove recognized reasoning delimiters from answer content.
6. Display and speak the final response with Android TextToSpeech, chunking long responses within engine limits.

## Controls
- Primary button: start or cancel listening, cancel the HTTP request, stop speech.
- Convo mode: new in-memory session; after final TTS chunk completes successfully, reopen microphone. Include every previous user and assistant turn in every request.
- Talk to yourself: opening voice input, assistant response, then generate a user follow-up from complete history with a temporary system instruction. Filter reasoning and store that result as role=user, speak it, then request/speak the assistant answer. Repeat until stopped. Never reopen the mic for subsequent self-talk turns.
- STOP and a second tap on the active mode button: cancel all work, invalidate callbacks, clear the active session. Switching modes starts fresh.
- Editable transcript and send text button (always a new conversation).
- Replay final answer.
- Server settings with explicit opt-in for plaintext HTTP on a trusted network.

## Reliability
Network work runs off the UI thread. Connect timeout 20 seconds; response read timeout 120 seconds. No automatic request retries. Generation tokens discard canceled or obsolete callbacks. Session history is complete and not truncated; server context-limit errors stop the session. response size limited to 2 MB. Stop all activity and clear session history when leaving app, including rotation. Errors stop automatic modes. Only the final speech chunk completion advances a loop; errors and stale callbacks never advance it. Destroy microphone and TTS resources with activity. Orientation changes preserve in-memory UI. Handle missing speech services, microphone denial, no recognized speech, server authentication errors, network failures, empty responses, and output truncation.

## Security and privacy
Android Keystore AES-GCM protects stored API keys. Disable app backups. Do not persist audio or conversation history. Disable redirects to avoid forwarding credentials. Reject credentials embedded in URLs, query strings, fragments, and invalid schemes. Never display server error bodies or log API keys.

## Verification and release
JUnit tests for payload independence, URL security and normalization, reasoning exclusion, content arrays, empty/truncated output, and speech chunks. Android release lint and release APK build must pass. GitHub Actions publishes APK, source ZIP, spec, and checksum to a versioned release on main. Hardware voice integration is not asserted by unit tests.
