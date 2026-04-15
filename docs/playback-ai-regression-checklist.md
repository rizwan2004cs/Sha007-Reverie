# Playback and AI Regression Checklist

Use this checklist on an Android 12+ device after installing a debug or release APK.

## Playback background stability

- Start playback, press Home, wait at least 3 minutes, and confirm playback continues without a crash.
- Start playback, turn the screen off, wait at least 3 minutes, and confirm playback continues.
- Start playback, open Recents, swipe the app away, and confirm behavior matches the `Keep playing after closing` setting.
- Repeat the Recents test with `Keep playing after closing` disabled and confirm playback stops cleanly without a crash.
- Start playback, trigger notification controls twice in quick succession, and confirm the service does not stop itself while buffering.

## Route and focus handling

- Start playback on speaker, connect wired or Bluetooth headphones, and confirm playback behavior matches the reconnect setting.
- While playing on headphones, disconnect the output device and confirm the app pauses or resumes as expected instead of silently stopping.
- Start playback, take transient audio focus with another app, then return focus and confirm playback recovery is correct.
- If crossfade is enabled, skip tracks several times and confirm playback does not stop during player swaps.

## AI playlist generation

- Configure Gemini with a valid API key and a supported model, then generate an AI playlist and confirm songs are returned.
- Configure DeepSeek with a valid API key and a supported model, then generate an AI playlist and confirm songs are returned.
- Clear the API key and confirm the app shows the missing-key message instead of a generic failure.
- Enter an invalid API key and confirm the app shows an authentication error.
- Select an unsupported or stale model and confirm the app shows a model-specific error.
- If the provider rate-limits the request, confirm the app shows a retry-oriented rate-limit error.

## AI metadata generation

- Run metadata generation for one song with Gemini and confirm valid JSON metadata is applied.
- Run metadata generation for one song with DeepSeek and confirm valid JSON metadata is applied.
- Confirm malformed provider output surfaces a parse/response error instead of crashing the flow.

## Automation checks

- Run `.\gradlew.bat --no-daemon :app:compileDebugUnitTestKotlin`.
- Run `.\gradlew.bat --no-daemon :app:testDebugUnitTest`.
