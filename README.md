# TypeAssist

A free, zero-cost typing assistant for Android. It runs quietly in the
background and works inside **any** app - WhatsApp, Gmail, LinkedIn, SMS,
anywhere you type - without replacing your keyboard.

## What it does

- Watches the text you type in any app (via an Android Accessibility Service).
- After you pause typing, it checks spelling/grammar using the free
  [LanguageTool](https://languagetool.org) API and shows a small floating
  card with an **Apply fix** button.
- Tap **✨ Rewrite** to send the message to Google's free-tier **Gemini**
  API and get a more natural, "humanized" version - tone adapts to the app
  (casual for WhatsApp, professional for Gmail/LinkedIn).
- Tap **Use this** to replace your text with the rewritten version.
- Open the app and tap **Voice Assistant** to speak a request in Tamil,
  English, or mixed Tanglish (e.g. "enaku oru simple leave mail draft
  pannunga") and get the requested content written out in English, ready
  to copy into any app.

Nothing is checked until you pause typing, and only the text in the field
you're actively editing is ever sent to LanguageTool/Gemini.

### Offline behaviour

- **No internet:** grammar checking automatically falls back to Android's
  built-in, fully on-device spell checker (the same one your keyboard uses).
  It catches misspelled words, but not sentence-structure/grammar mistakes
  the way LanguageTool can - that's the trade-off for working with zero
  setup and zero connection.
- **✨ Rewrite** always needs internet (it calls Gemini) - offline, the
  button is greyed out and tapping it just explains that a connection is
  needed. There's no zero-cost way to run a rewriting AI model fully on
  a phone without a multi-GB download, so this stays online-only.

## Project structure

- `lib/` - Flutter app: a simple settings screen to enable the accessibility
  service and see its status.
- `android/app/src/main/kotlin/com/salimsrk/typeassist/` - the actual native
  Android logic:
  - `TypingAssistService.kt` - the Accessibility Service (the "brain").
  - `GrammarApi.kt` - LanguageTool integration (no key needed).
  - `RewriteApi.kt` - Gemini integration (needs a free API key, see below).
  - `OverlayManager.kt` - draws the floating suggestion card.
  - `MainActivity.kt` - Flutter <-> Android bridge for the settings screen.

## Setting up the Gemini API key (required for Rewrite)

The key is **never committed to this repo** (it's public) - it's injected at
build time from a GitHub Actions secret.

1. Get a free key at <https://aistudio.google.com/apikey> (starts with
   `AIzaSy...`).
2. In this repo: **Settings -> Secrets and variables -> Actions -> New
   repository secret**.
3. Name: `GEMINI_API_KEY`, value: your key. Save.

For a local build on your own machine:

- Native side (Rewrite button): put it in `android/local.properties`
  (already git-ignored):
  ```
  GEMINI_API_KEY=AIzaSy...
  ```
- Dart side (Voice Assistant): pass it on the command line instead, since
  Dart code can't read `local.properties`:
  ```
  flutter build apk --release --dart-define=GEMINI_API_KEY=AIzaSy...
  # or, while developing:
  flutter run --dart-define=GEMINI_API_KEY=AIzaSy...
  ```

Grammar/spelling checking (LanguageTool) needs no key at all.

## Building the APK

Every push to `main` triggers **GitHub Actions** (`.github/workflows/build.yml`),
which builds a release APK and attaches it to a new **GitHub Release** -
completely free on a public repo. Download it from the repo's **Releases**
tab once the workflow finishes (Actions tab -> latest run -> green check).

To build locally instead (needs the Flutter SDK + Android SDK installed):

```bash
flutter pub get
flutter build apk --release
# APK at build/app/outputs/flutter-apk/app-release.apk
```

## Installing on your phone

1. Download the APK (from the GitHub Release, or transfer the locally-built
   one).
2. On your phone, allow "Install unknown apps" for the browser/file manager
   you used, then open the APK to install.
3. Open **TypeAssist** and tap **Enable TypeAssist** - this opens Android's
   Accessibility settings. Turn the **TypeAssist** toggle on.
4. Go to WhatsApp/Gmail/any app and start typing - the suggestion card
   appears above the keyboard when something's worth flagging.
5. For voice commands, open **TypeAssist -> Voice Assistant** and allow
   the microphone permission when asked (needed once).

## Notes / limitations

- The release APK is signed with the Flutter debug keystore, which is fine
  for personal sideloading and is what keeps this at zero cost. Add a real
  signing config before publishing anywhere public (e.g. the Play Store).
- LanguageTool's free public API is rate-limited (~20 requests/minute) -
  plenty for one person's normal typing.
- Gemini's free tier has a daily quota; if Rewrite stops working, you've
  likely hit it for the day.
