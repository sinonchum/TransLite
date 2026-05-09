<div align="center">

# TransLite

### Offline Translation Android App

**Built entirely by AI agents — 26 tasks, 5 phases, zero manual intervention.**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg)](https://developer.android.com/compose)

**[English](#features)** | **[中文](#中文)**

</div>

---

## Features

- **On-Device LLM Translation** — Gemma 4 (E2B) powered by LiteRT-LM, fully offline neural translation
- **Online Fallback** — MyMemory API for languages/models not yet downloaded
- **OCR Recognition** — ML Kit text recognition (Chinese + Latin scripts)
- **Floating Ball** — Global overlay for quick translation anywhere on screen
- **Screen Capture** — Capture screen region, OCR → translate automatically
- **Conversation Mode** — Voice input with real-time translation
- **Language Pack Manager** — Download and manage offline language models
- **History & Favorites** — Room database persistence with search
- **Material 3** — Dynamic color, dark mode, modern UI

## Tech Stack

| Component | Technology |
|:----------|:-----------|
| Language | Kotlin 2.2 |
| UI | Jetpack Compose + Material 3 |
| Translation | LiteRT-LM SDK 0.11.0 (Gemma 4 E2B, 2.5GB `.litertlm`) |
| Online Backup | MyMemory Translation API |
| OCR | Google ML Kit Text Recognition |
| Database | Room (SQLite) |
| Async | Kotlin Coroutines + Flow |
| Build | Gradle 8.12 + AGP 8.9.3 + KSP 2.2.21 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |

## Build

```bash
# Set environment
export JAVA_HOME=~/java/jdk-17.0.14+7/Contents/Home  # macOS
export ANDROID_HOME=~/Library/Android/sdk

# Debug build
./gradlew assembleDebug

# Release build (R8 optimized)
./gradlew assembleRelease

# Install on device
adb install -t -r app/build/outputs/apk/debug/app-debug.apk

# Push offline model (2.5GB, one-time)
adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.translite.app/files/models/
```

## Offline Model Setup

TransLite uses [LiteRT-LM](https://ai.google.dev/edge/litertlm) to run Gemma 4 E2B on-device:

1. Download `gemma-4-E2B-it.litertlm` from [litert-community/gemma-4-E2B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)
2. Push to device: `adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.translite.app/files/models/`
3. Open TransLite → Settings → Enable "Use Offline Model"
4. First translation loads the model (~0.6s), subsequent translations take ~1-2s

**Model specs:**
- Format: `.litertlm` (LiteRT-LM native format)
- Size: 2.5GB (int4 quantized)
- Backend: CPU (XNNPack optimized)
- RAM: ~4GB peak during inference

## Project Structure

```
TransLite/app/src/main/java/com/translite/app/
├── TransLiteApp.kt              # Application + notification channels
├── MainActivity.kt              # Entry point + 4-tab navigation
├── data/
│   ├── db/                      # Room database (Entity, Dao, Database)
│   └── repository/              # TranslationRepository
├── domain/
│   ├── model/                   # Language enum + TranslationModels
│   ├── engine/                  # TranslationEngine + LiteRT-LM + Online
│   ├── ocr/                     # OcrEngine + ML Kit OCR
│   └── languagemanager/         # Language pack download manager
├── service/
│   ├── FloatingBallService.kt   # WindowManager floating overlay
│   └── ScreenCaptureService.kt  # MediaProjection screen capture
└── ui/
    ├── theme/                   # Material 3 theme (dynamic color)
    ├── screens/                 # Home, History, Settings, Conversation, LanguagePack
    ├── components/              # Reusable UI components
    └── viewmodel/               # TranslationViewModel
```

## Autonomous Development

This project was built by AI agents using the [Autonomous AI Development Framework](https://github.com/sinonchum/autonomous-ai-framework).

| Metric | Value |
|:-------|:------|
| Tasks | 26 across 5 phases |
| Manual Interventions | 0 after initial plan |
| Kotlin Files | 27 |
| Lines of Code | 2,357 |
| Build Time | ~25 minutes |
| Device | Xiaomi 12 Pro, zero crashes |

---

## 中文

### 功能特性

- **端侧大模型翻译** — Gemma 4 (E2B) 驱动，LiteRT-LM 推理，完全离线神经网络翻译
- **在线回退** — MyMemory API 补充未下载模型的语言
- **OCR识别** — ML Kit文字识别（中英文）
- **悬浮球** — 全局悬浮窗，随时快速翻译
- **屏幕抓取** — 截取屏幕区域，OCR→自动翻译
- **对话模式** — 语音输入实时翻译
- **语言包管理** — 下载管理离线语言模型
- **历史与收藏** — Room数据库持久化，支持搜索
- **Material 3** — 动态配色、深色模式、现代UI

### 技术栈

| 组件 | 技术 |
|:-----|:-----|
| 语言 | Kotlin 2.2 |
| UI | Jetpack Compose + Material 3 |
| 翻译 | LiteRT-LM SDK 0.11.0（Gemma 4 E2B，2.5GB `.litertlm`） |
| 在线备用 | MyMemory Translation API |
| OCR | Google ML Kit 文字识别 |
| 数据库 | Room (SQLite) |
| 异步 | Kotlin Coroutines + Flow |
| 构建 | Gradle 8.12 + AGP 8.9.3 + KSP 2.2.21 |
| 最低SDK | 26 (Android 8.0) |
| 目标SDK | 34 (Android 14) |

### 离线模型配置

1. 从 [litert-community/gemma-4-E2B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) 下载 `gemma-4-E2B-it.litertlm`
2. 推送到设备：`adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.translite.app/files/models/`
3. 打开 TransLite → 设置 → 开启"使用离线模型"
4. 首次翻译加载模型（~0.6s），后续翻译 ~1-2s

**模型规格：**
- 格式：`.litertlm`（LiteRT-LM 原生格式）
- 大小：2.5GB（int4 量化）
- 后端：CPU（XNNPack 优化）
- 内存：推理时峰值 ~4GB

### 自主开发

本项目由AI代理使用[自主AI开发框架](https://github.com/sinonchum/autonomous-ai-framework)构建。

| 指标 | 数值 |
|:-----|:-----|
| 任务数 | 26个，跨5个阶段 |
| 人工干预 | 初始计划后0次 |
| Kotlin文件 | 27个 |
| 代码行数 | 2,357行 |
| 构建时间 | ~25分钟 |
| 设备 | Xiaomi 12 Pro，零崩溃 |

---

## License

MIT
