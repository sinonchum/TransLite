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

- **Offline Translation** — ML Kit translation for 10 languages, works without internet
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
| Language | Kotlin 1.9 |
| UI | Jetpack Compose + Material 3 |
| Translation | Google ML Kit (offline) |
| OCR | Google ML Kit Text Recognition |
| Database | Room (SQLite) |
| Async | Kotlin Coroutines + Flow |
| Build | Gradle 8.5 + AGP 8.2.2 + KSP |
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
```

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
│   ├── engine/                  # TranslationEngine + ML Kit + Fallback
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

- **离线翻译** — ML Kit支持10种语言离线翻译
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
| 语言 | Kotlin 1.9 |
| UI | Jetpack Compose + Material 3 |
| 翻译 | Google ML Kit（离线） |
| OCR | Google ML Kit 文字识别 |
| 数据库 | Room (SQLite) |
| 异步 | Kotlin Coroutines + Flow |
| 构建 | Gradle 8.5 + AGP 8.2.2 + KSP |
| 最低SDK | 26 (Android 8.0) |
| 目标SDK | 34 (Android 14) |

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
