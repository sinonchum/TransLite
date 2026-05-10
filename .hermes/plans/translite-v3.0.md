# TransLite v3.0 Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.
> Execute phases sequentially. Each phase: build → install → verify → spec review → quality review → next.

**Goal:** Upgrade TransLite to v3.0 with i18n, dual-LLM (Gemma 4 + Phi-4-mini), academic mode, and enhanced system-level features.

**Architecture:** Keep existing single-module structure. Add i18n resource layers, extend model management for multi-model, add academic translation mode with structured output.

**Tech Stack:** Kotlin 2.2+, Compose Material 3, LiteRT-LM 0.11.0, Room 2.7.1, ML Kit, MediaProjection

**Device:** Xiaomi 12 Pro (48d093fd), ADB available, Java 17 at ~/java/jdk-17.0.13+11/Contents/Home

**Build:** `cd ~/Documents/HermesAnywhere/TransLite && JAVA_HOME=~/java/jdk-17.0.13+11/Contents/Home ./gradlew assembleRelease --no-daemon`

---

## Phase 1: i18n — Global Multi-Language Support

### Task 1.1: Extract all hardcoded strings to resources

**Objective:** Move every hardcoded Chinese/English string in Compose UI to `strings.xml`

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (default = English)
- Create: `app/src/main/res/values-zh/strings.xml` (Chinese)
- Scan & modify: all files in `app/src/main/java/com/translite/app/ui/`

**Steps:**
1. Grep all `Text("...")` and `label = { Text("...") }` in ui/ directory
2. For each hardcoded string, add `<string name="key">English text</string>` to values/strings.xml
3. Add corresponding Chinese translation to values-zh/strings.xml
4. Replace all `Text("硬编码")` with `Text(stringResource(R.string.key))`
5. Add `import androidx.compose.ui.res.stringResource` to each file that uses it

**Key strings to extract (from current codebase):**
```
"设置" → settings_title
"离线翻译模型" → offline_model_title
"Gemma 4 E2B (约 2.5GB)" → model_info_gemma
"下载离线模型 (~2.5GB)" → download_model_button
"首次使用需下载模型，建议在 Wi-Fi 下进行" → download_hint
"下载中..." → downloading
"正在加载模型..." → loading_model
"模型已就绪" → model_ready
"使用离线模型" → use_offline_model
"当前使用离线翻译" → using_offline
"当前使用在线翻译" → using_online
"悬浮球翻译" → floating_ball
"悬浮窗权限" → overlay_permission
"语言包管理" → language_packs
"关于" → about
"翻译" → translate
"对话" → conversation
"历史" → history
"输入要翻译的文本..." → input_hint
"拍照翻译" → photo_translate
"屏幕翻译" → screen_translate
"切换在线" → switch_online
"切换离线" → switch_offline
"下载语言包" → download_lang_pack
"删除" → delete
"取消" → cancel
"确认" → confirm
"已下载" → downloaded
"未下载" → not_downloaded
"复制" → copy
"收藏" → favorite
"清除历史" → clear_history
"速度:" → speed_label
"模型下载失败" → download_failed
"请检查网络连接后重试" → check_network
```

**Verification:** `grep -r 'Text("' app/src/main/java/com/translite/app/ui/ | wc -l` should return 0

---

### Task 1.2: Configure Android per-app language settings

**Objective:** Enable Android 13+ per-app language picker

**Files:**
- Create: `app/src/main/res/xml/locales_config.xml`
- Modify: `app/src/main/AndroidManifest.xml` (add `android:localeConfig`)

**Step 1: Create locales_config.xml**
```xml
<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en" />
    <locale android:name="zh" />
</locale-config>
```

**Step 2: Add to AndroidManifest.xml** (inside `<application>` tag)
```xml
android:localeConfig="@xml/locales_config"
```

**Verification:** Build succeeds, app appears in system language settings for TransLite

---

### Task 1.3: Add in-app language picker to Settings

**Objective:** Allow users to switch language without leaving the app

**Files:**
- Modify: `app/src/main/java/com/translite/app/ui/screens/SettingsScreen.kt`
- Modify: `app/src/main/java/com/translite/app/MainActivity.kt`

**Step 1: Add language picker section to SettingsScreen**
Add a new Card after the model section with:
- Title: stringResource(R.string.language_setting)
- Three options in a Column with RadioButton: "跟随系统" / "English" / "中文"
- On selection: call `AppCompatDelegate.setApplicationLocales()` with `LocaleListCompat.forLanguageTags()`

**Step 2: Wire up in MainActivity**
Pass `onLanguageChange: (String?) -> Unit` to SettingsScreen. The callback calls:
```kotlin
val locale = if (tag == null) LocaleListCompat.getEmptyLocales() else LocaleListCompat.forLanguageTags(tag)
AppCompatDelegate.setApplicationLocales(locale)
```

**Verification:** Switch to Chinese → all UI text changes. Switch to English → all UI text changes. "跟随系统" → follows device locale.

---

## Phase 2: Dual-LLM Engine & Phi-4 Integration

### Task 2.1: Multi-model manager with hardware detection

**Objective:** Extend model management to support downloading/switching between Gemma 4 and Phi-4-mini

**Files:**
- Create: `app/src/main/java/com/translite/app/domain/engine/ModelManager.kt`
- Modify: `app/src/main/java/com/translite/app/domain/engine/GemmaTranslator.kt` (extract model logic)
- Modify: `app/src/main/java/com/translite/app/TransLiteApp.kt`

**Step 1: Create ModelManager**
```kotlin
class ModelManager(private val context: Context) {
    enum class ModelType(val filename: String, val displayName: String, val sizeGB: Float, val url: String) {
        GEMMA_4_E2B("gemma-4-E2B-it.litertlm", "Gemma 4 E2B", 2.5f,
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"),
        PHI_4_MINI("Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm", "Phi-4 Mini", 3.7f,
            "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm")
    }

    fun isModelDownloaded(type: ModelType): Boolean
    fun getModelFile(type: ModelType): File
    suspend fun downloadModel(type: ModelType, onProgress: (Int, Long, Long, Float) -> Unit)
    fun deleteModel(type: ModelType)
    fun getDeviceRAMGB(): Int  // For Phi-4 warning
}
```

**Step 2: Refactor GemmaTranslator to use ModelManager**
- Remove model file management from GemmaTranslator
- GemmaTranslator takes `modelFile: File` in constructor
- ModelManager handles all download/copy logic

**Step 3: Register in TransLiteApp**
```kotlin
lateinit var modelManager: ModelManager
    private set
```

**Verification:** Both models can be downloaded independently. RAM check shows warning on low-RAM devices.

---

### Task 2.2: Phi-4 translator engine

**Objective:** Create a translation engine using Phi-4-mini for academic mode

**Files:**
- Create: `app/src/main/java/com/translite/app/domain/engine/Phi4Translator.kt`
- Modify: `app/src/main/java/com/translite/app/domain/engine/TranslationEngine.kt` (add mode enum)

**Step 1: Add TranslationMode to TranslationEngine**
```kotlin
enum class TranslationMode { STANDARD, ACADEMIC }
```

**Step 2: Create Phi4Translator**
- Same interface as GemmaTranslator but uses Phi-4 model file
- Academic prompt template that requests structured output (JSON with translation, grammar breakdown, key vocabulary)
- Returns `TranslationResult` with extended `analysisData` field

**Step 3: Academic prompt template**
```
<start_of_turn>user
You are an academic translation specialist. Translate the following text from {src} to {tgt}.
Output a JSON object with these fields:
- "translation": the translation text
- "grammar": array of grammar breakdowns (structure, tense, voice)
- "vocabulary": array of key terms with definitions
- "notes": translation notes and alternatives

Text: {text}
<start_of_turn>model
```

**Verification:** Phi-4 model downloads and translates sample academic text with structured JSON output.

---

### Task 2.3: Dual-mode routing & memory management

**Objective:** Route translations to the correct engine based on mode, with safe model hot-swapping

**Files:**
- Create: `app/src/main/java/com/translite/app/domain/engine/EngineRouter.kt`
- Modify: `app/src/main/java/com/translite/app/ui/viewmodel/TranslationViewModel.kt`
- Modify: `app/src/main/java/com/translite/app/ui/screens/SettingsScreen.kt`

**Step 1: Create EngineRouter**
```kotlin
class EngineRouter(
    private val gemmaEngine: GemmaTranslator,
    private val phi4Engine: Phi4Translator
) {
    private val activeEngine = MutableStateFlow<TranslationMode>(TranslationMode.STANDARD)

    suspend fun translate(text: String, src: Language, tgt: Language, mode: TranslationMode): Result<TranslationResult> {
        val engine = when (mode) {
            TranslationMode.STANDARD -> gemmaEngine
            TranslationMode.ACADEMIC -> phi4Engine
        }
        return engine.translate(text, src, tgt)
    }

    suspend fun switchModel(mode: TranslationMode) {
        // Unload current, force GC, load new
        when (mode) {
            TranslationMode.STANDARD -> { phi4Engine.close(); System.gc(); gemmaEngine.ensureModelAvailable() }
            TranslationMode.ACADEMIC -> { gemmaEngine.close(); System.gc(); phi4Engine.ensureModelAvailable() }
        }
        activeEngine.value = mode
    }
}
```

**Step 2: Add mode toggle to Settings UI**
- Radio buttons: "Standard (Gemma 4)" / "Academic (Phi-4)"
- When switching, show loading indicator, call `switchModel()`
- If Phi-4 not downloaded, prompt to download first

**Verification:** Switching between modes loads/unloads models correctly. No OOM on 8GB device.

---

## Phase 3: Enhanced System Features

### Task 3.1: Improve floating ball overlay

**Objective:** Make the floating ball fully functional with drag, tap, and translation popup

**Files:**
- Modify: `app/src/main/java/com/translite/app/service/FloatingBallService.kt`

**Current state:** Service exists but likely incomplete. Needs:
1. WindowManager-based draggable circle (50dp)
2. Single tap → expand to show last translation
3. Double tap → trigger screen capture
4. Long press → open main app
5. Proper FOREGROUND_SERVICE notification for Android 12+

**Verification:** Floating ball appears, is draggable, responds to gestures.

---

### Task 3.2: Screen capture translation pipeline

**Objective:** Capture screen region → OCR → translate → overlay result

**Files:**
- Modify: `app/src/main/java/com/translite/app/service/ScreenCaptureService.kt`

**Pipeline:**
1. MediaProjection captures screen bitmap
2. User draws rectangle to select region
3. Crop bitmap to selection
4. ML Kit OCR extracts text
5. Send text to active engine (Gemma or Phi-4)
6. Show translation result in a floating overlay window

**Verification:** Select region on screen → OCR text appears → translation overlay shows.

---

### Task 3.3: Clipboard monitoring service

**Objective:** Optional clipboard listener that detects foreign text and offers quick translate

**Files:**
- Create: `app/src/main/java/com/translite/app/service/ClipboardMonitorService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Implementation:**
- Foreground service with low-importance notification
- On clipboard change: detect language via simple heuristic (CJK characters, Latin ratio)
- If foreign language detected: show notification with translated text
- User taps notification → opens app with pre-filled translation

**Verification:** Copy foreign text in another app → notification appears with translation.

---

## Phase 4: Academic Analysis UI

### Task 4.1: Markdown rendering in translation results

**Objective:** Render Phi-4's structured JSON output as formatted UI

**Files:**
- Create: `app/src/main/java/com/translite/app/ui/components/AcademicResultView.kt`
- Modify: `app/src/main/java/com/translite/app/ui/screens/HomeScreen.kt`

**Components:**
- Main translation text (large, prominent)
- Expandable "Grammar Breakdown" card with bullet list
- Expandable "Key Vocabulary" card with term/definition pairs
- Expandable "Notes" card

Use Compose `AnimatedVisibility` for expand/collapse.

---

### Task 4.2: History with mode & analysis data

**Objective:** Save academic analysis results to database

**Files:**
- Modify: `app/src/main/java/com/translite/app/data/db/entity/TranslationEntity.kt`
- Modify: `app/src/main/java/com/translite/app/data/db/AppDatabase.kt` (version 2, migration)
- Modify: `app/src/main/java/com/translite/app/data/db/TranslationDao.kt`

**Step 1: Add fields to TranslationEntity**
```kotlin
@ColumnInfo(name = "mode") val mode: String = "standard",  // "standard" or "academic"
@ColumnInfo(name = "analysis_data") val analysisData: String? = null  // JSON string
```

**Step 2: Room migration**
```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE translations ADD COLUMN mode TEXT NOT NULL DEFAULT 'standard'")
        db.execSQL("ALTER TABLE translations ADD COLUMN analysis_data TEXT")
    }
}
```

**Step 3: Update database version to 2**

**Verification:** Academic translations save with analysis_data. Old translations still accessible.

---

## Phase 5: Quality Assurance

### Task 5.1: i18n completeness check

**Objective:** Verify all UI strings are resource-ized

**Command:**
```bash
grep -r 'Text("' app/src/main/java/com/translite/app/ui/ | wc -l
# Expected: 0
grep -rn 'stringResource' app/src/main/java/com/translite/app/ui/ | wc -l
# Expected: > 50
```

---

### Task 5.2: Model switching stress test

**Objective:** Verify no OOM on repeated model switches

**Manual test script (via ADB):**
```bash
for i in $(seq 1 10); do
    echo "Switch $i"
    adb shell input tap 540 800  # Toggle offline mode
    sleep 3
done
# Monitor: adb shell dumpsys meminfo com.translite.app
```

---

### Task 5.3: Translation quality benchmark

**Objective:** Compare Gemma vs Phi-4 on academic text

**Test samples:**
1. Legal clause: "The party of the first part shall indemnify and hold harmless..."
2. Scientific abstract: "We present a novel approach to quantum error correction..."
3. Literary: "It was the best of times, it was the worst of times..."

**Expected:** Phi-4 provides grammar breakdown + vocabulary. Gemma provides direct translation only.

---

## Version & Release

After all phases pass:
1. Update `build.gradle.kts`: versionCode = 8, versionName = "3.0.0"
2. Update SettingsScreen "关于" text
3. `git commit -m "v3.0.0: i18n, Phi-4 academic mode, enhanced system features"`
4. `git tag -a v3.0.0`
5. `git push origin main --tags`
6. `gh release create v3.0.0` with APK
