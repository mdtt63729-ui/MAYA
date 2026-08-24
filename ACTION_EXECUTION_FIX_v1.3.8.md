# MAYA v1.3.8 — Action Execution Fix

## Fixed

### Contacts / calling
- Contact matching now preserves Unicode letters, so Bengali/Hindi/non-Latin contact names are searchable.
- Exact, starts-with, contains and small Levenshtein-distance matching are supported.
- `READ_CONTACTS` is checked before contact lookup.
- `CALL_PHONE` is checked before direct calling.
- Calls use `ACTION_CALL` with the resolved number instead of merely opening the dialer.

### WhatsApp
- Message parser now understands both:
  - `send hello to Rahul on WhatsApp`
  - `Rahul কে hello message পাঠাও`
- Contact names are resolved from the device contacts.
- Local 10-digit numbers are converted to the device/SIM country calling code when supported.
- WhatsApp uses a native `SENDTO` flow first and a `wa.me` fallback.
- Accessibility automation waits for WhatsApp's UI to render and retries the Send button instead of assuming that opening the chat sent the message.
- Additional WhatsApp send IDs/content descriptions are supported.

### Google Search
- No longer relies on `ACTION_WEB_SEARCH` as the primary path.
- Uses an explicit Google search URL and Google app when available.
- Falls back to a normal browser or `ACTION_WEB_SEARCH`.

### YouTube
- `YouTube search <query>` is routed to `SEARCH_APP`.
- Uses the YouTube app's search Intent when available.
- Falls back to the official YouTube search URL.
- `open YouTube` remains an app launch action.

### Command parsing
- Added deterministic parsing for call, message, Google search, YouTube search, media, app launch and Play Store commands.
- AI fallback remains available for ambiguous natural-language commands.
- Executor reports success only when the Android launch/dispatch operation succeeds.

### Personality
- MAYA's voice personality now naturally supports affectionate, caring nicknames such as Babu/Sona when appropriate, while remaining non-sexual and respectful.

## Android limitation
- A third-party app cannot bypass Android runtime permissions or another app's UI/security boundaries. Calling requires `CALL_PHONE`; contact lookup requires `READ_CONTACTS`; automatic WhatsApp Send requires MAYA's Accessibility Automation service to be enabled.
- These are platform requirements, not AI failures.

## Verification
- Modified Kotlin files: structural brace/parenthesis/bracket sanity check passed.
- No video/GIF assets were added for action execution.
- Version: 1.3.8 / versionCode 20.
- A full Gradle CI build still must be run by the repository's GitHub Actions workflow because this isolated environment does not contain the Gradle 9.3.1 distribution.
