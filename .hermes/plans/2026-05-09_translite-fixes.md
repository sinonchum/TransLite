# TransLite v1.2.0 Improvements Plan

## Issues Identified

### Bug 1: Model not bundled (HuggingFace blocked in China)
- `GemmaTranslator.downloadLanguage()` downloads from huggingface.co → blocked in China
- Fix: Bundle model in `assets/`, copy to filesDir on first run
- User places `translategemma-4b-it-q4_k_m.gguf` in `app/src/main/assets/` before building

### Bug 2: Floating ball has no visible close button + settings toggle broken
- Expanded panel has close button (line 221) but floating ball itself has no stop mechanism
- `isFloatingActive` in MainActivity is a plain `var`, not `mutableStateOf` → Compose never recomposes
- Fix: Use `mutableStateOf`, add long-press-to-stop on ball, persist state in SharedPreferences

### Bug 3: Language pack download does nothing
- UI calls `engine.downloadLanguage()` but model is single multilingual (not per-language)
- Download fails silently (HuggingFace blocked)
- Fix: Show single "Translation Model" status, reflect bundled model state

### Bug 4: Screen capture crashes — service not in Manifest
- `ScreenCaptureService` is NOT declared in `AndroidManifest.xml` → `startForegroundService()` crashes
- Also missing `FOREGROUND_SERVICE_SPECIAL_USE` for MediaProjection type

### Bug 5: No proper app icon
- Current icon is a simple circle/adaptive icon
- Fix: Create Material Design vector drawable with "T" + translate motif

### Release: Signed APK
- Configure signing config in build.gradle.kts
- Generate debug keystore for release signing
- Build release APK + create GitHub release

## Execution Plan

1. **Fix Manifest** — add ScreenCaptureService + fix foreground service type
2. **Fix FloatingBallService** — add close button to ball, long-press stop
3. **Fix MainActivity** — mutableStateOf + SharedPreferences for floating state
4. **Fix GemmaTranslator** — load model from assets
5. **Fix LanguagePackScreen** — show single model status
6. **Create icon** — Material Design vector drawable
7. **Build release** — signed APK
8. **GitHub release** — v1.2.0 with signed APK
