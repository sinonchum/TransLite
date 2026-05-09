# TransLite Phase 1: Foundation — Project Scaffold + Core Translation

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.
> Each task is a fresh subagent. Two-stage review (spec compliance → code quality) after each.

**Goal:** Build a runnable Android app with offline ML Kit translation, text input, and Compose UI.

**Architecture:** Single-module Android app. Clean-ish architecture (data/domain/ui layers). ML Kit for offline translation + OCR. Room for history cache. Jetpack Compose + Material 3 for UI.

**Tech Stack:** Kotlin 1.9, Jetpack Compose (BOM 2024.02), Material 3, ML Kit Translation + Text Recognition, Room, Kotlin Coroutines + Flow, Gradle Kotlin DSL.

**Device:** Xiaomi 13 Pro (nuwa), arm64-v8a. Test on real device via ADB.

**Package:** `com.translite.app`
**Min SDK:** 26 | **Target SDK:** 34 | **Compile SDK:** 34

---

## Task 1: Create Gradle Build Files

**Objective:** Set up project-level and app-level Gradle build files with all dependencies.

**Files:**
- Create: `TransLite/settings.gradle.kts`
- Create: `TransLite/build.gradle.kts` (project level)
- Create: `TransLite/app/build.gradle.kts`
- Create: `TransLite/gradle.properties`
- Create: `TransLite/local.properties` (auto-generated placeholder)
- Create: `TransLite/gradle/wrapper/gradle-wrapper.properties`

**Step 1: settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "TransLite"
include(":app")
```

**Step 2: build.gradle.kts (project level)**

```kotlin
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
}
```

**Step 3: app/build.gradle.kts**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.translite.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.translite.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ML Kit Translation (offline)
    implementation("com.google.mlkit:translate:17.0.3")

    // ML Kit Text Recognition
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
```

**Step 4: gradle.properties**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

**Step 5: gradle-wrapper.properties**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

**Verification:**
```bash
cd ~/Documents/HermesAnywhere/TransLite && find . -name "*.kts" -o -name "*.properties" | sort
# Expected: 5 files listed
```

---

## Task 2: Android Manifest + Application Class

**Objective:** Create AndroidManifest.xml with required permissions and Application class.

**Files:**
- Create: `TransLite/app/src/main/AndroidManifest.xml`
- Create: `TransLite/app/src/main/java/com/translite/app/TransLiteApp.kt`

**Step 1: AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Translation + OCR permissions -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <uses-feature android:name="android.hardware.camera" android:required="false" />
    <uses-feature android:name="android.hardware.microphone" android:required="false" />

    <application
        android:name=".TransLiteApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.TransLite"
        tools:targetApi="34">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.TransLite">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.FloatingBallService"
            android:exported="false"
            android:foregroundServiceType="mediaProjection" />
    </application>
</manifest>
```

**Step 2: TransLiteApp.kt**

```kotlin
package com.translite.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class TransLiteApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val floatingChannel = NotificationChannel(
            CHANNEL_FLOATING,
            "悬浮翻译",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "悬浮球翻译服务通知"
        }

        val ocrChannel = NotificationChannel(
            CHANNEL_OCR,
            "OCR识别",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "屏幕文字识别通知"
        }

        manager.createNotificationChannel(floatingChannel)
        manager.createNotificationChannel(ocrChannel)
    }

    companion object {
        const val CHANNEL_FLOATING = "floating_ball"
        const val CHANNEL_OCR = "screen_ocr"
    }
}
```

**Verification:**
```bash
# Check manifest has all permissions
grep -c "uses-permission" ~/Documents/HermesAnywhere/TransLite/app/src/main/AndroidManifest.xml
# Expected: 7
```

---

## Task 3: Theme + Color System

**Objective:** Create Material 3 theme with dynamic color support and dark mode.

**Files:**
- Create: `TransLite/app/src/main/java/com/translite/app/ui/theme/Color.kt`
- Create: `TransLite/app/src/main/java/com/translite/app/ui/theme/Type.kt`
- Create: `TransLite/app/src/main/java/com/translite/app/ui/theme/Theme.kt`
- Create: `TransLite/app/src/main/res/values/strings.xml`
- Create: `TransLite/app/src/main/res/values/themes.xml`

**Step 1: Color.kt**

```kotlin
package com.translite.app.ui.theme

import androidx.compose.ui.graphics.Color

// Light
val PrimaryLight = Color(0xFF1A73E8)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFD3E3FD)
val SecondaryLight = Color(0xFF5F6368)
val BackgroundLight = Color(0xFFF8FAFA)
val SurfaceLight = Color(0xFFFFFFFF)

// Dark
val PrimaryDark = Color(0xFF8AB4F8)
val OnPrimaryDark = Color(0xFF062E6F)
val PrimaryContainerDark = Color(0xFF004A93)
val SecondaryDark = Color(0xFFBDC1C6)
val BackgroundDark = Color(0xFF1F1F1F)
val SurfaceDark = Color(0xFF2D2D2D)
```

**Step 2: Type.kt**

```kotlin
package com.translite.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp)
)
```

**Step 3: Theme.kt**

```kotlin
package com.translite.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    secondary = SecondaryLight,
    background = BackgroundLight,
    surface = SurfaceLight
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    secondary = SecondaryDark,
    background = BackgroundDark,
    surface = SurfaceDark
)

@Composable
fun TransLiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

**Step 4: strings.xml**

```xml
<resources>
    <string name="app_name">TransLite</string>
    <string name="input_hint">输入要翻译的文本...</string>
    <string name="translate">翻译</string>
    <string name="source_lang">源语言</string>
    <string name="target_lang">目标语言</string>
    <string name="history">历史记录</string>
    <string name="settings">设置</string>
    <string name="download_lang">下载语言包</string>
    <string name="downloading">下载中...</string>
    <string name="download_complete">下载完成</string>
    <string name="copy_result">复制结果</string>
    <string name="clear">清除</string>
</resources>
```

**Step 5: themes.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.TransLite" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

**Verification:**
```bash
# Check all theme files exist
ls ~/Documents/HermesAnywhere/TransLite/app/src/main/java/com/translite/app/ui/theme/
# Expected: Color.kt  Theme.kt  Type.kt
```

---

## Task 4: Domain Models

**Objective:** Create data models for translation, language pairs, and cache.

**Files:**
- Create: `TransLite/app/src/main/java/com/translite/app/domain/model/TranslationModels.kt`
- Create: `TransLite/app/src/main/java/com/translite/app/domain/model/Language.kt`

**Step 1: Language.kt**

```kotlin
package com.translite.app.domain.model

enum class Language(val code: String, val displayName: String, val displayNameEn: String) {
    CHINESE("zh", "中文", "Chinese"),
    ENGLISH("en", "英语", "English"),
    FRENCH("fr", "法语", "French"),
    SPANISH("es", "西班牙语", "Spanish"),
    GERMAN("de", "德语", "German"),
    JAPANESE("ja", "日语", "Japanese"),
    KOREAN("ko", "韩语", "Korean"),
    RUSSIAN("ru", "俄语", "Russian"),
    PORTUGUESE("pt", "葡萄牙语", "Portuguese"),
    ARABIC("ar", "阿拉伯语", "Arabic");

    companion object {
        fun fromCode(code: String): Language? =
            entries.find { it.code == code }

        val supportedPairs: List<Pair<Language, Language>> by lazy {
            entries.flatMap { src ->
                entries.filter { it != src }.map { src to it }
            }
        }
    }
}
```

**Step 2: TranslationModels.kt**

```kotlin
package com.translite.app.domain.model

data class TranslationRequest(
    val text: String,
    val sourceLang: Language,
    val targetLang: Language
)

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLang: Language,
    val targetLang: Language,
    val timestamp: Long = System.currentTimeMillis(),
    val isOffline: Boolean = true
)
```

**Verification:**
```bash
grep "enum class Language" ~/Documents/HermesAnywhere/TransLite/app/src/main/java/com/translite/app/domain/model/Language.kt
# Expected: 1 match
```

---

## Task 5: Room Database (Translation History)

**Objective:** Set up Room database for caching translation history and favorites.

**Files:**
- Create: `TransLite/app/src/main/java/com/translite/app/data/db/entity/TranslationEntity.kt`
- Create: `TransLite/app/src/main/java/com/translite/app/data/db/TranslationDao.kt`
- Create: `TransLite/app/src/main/java/com/translite/app/data/db/AppDatabase.kt`

**Step 1: TranslationEntity.kt**

```kotlin
package com.translite.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "translations")
data class TranslationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalText: String,
    val translatedText: String,
    val sourceLangCode: String,
    val targetLangCode: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isOffline: Boolean = true
)
```

**Step 2: TranslationDao.kt**

```kotlin
package com.translite.app.data.db

import androidx.room.*
import com.translite.app.data.db.entity.TranslationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(translation: TranslationEntity): Long

    @Query("SELECT * FROM translations ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TranslationEntity>>

    @Query("SELECT * FROM translations WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavorites(): Flow<List<TranslationEntity>>

    @Query("UPDATE translations SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("DELETE FROM translations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM translations")
    suspend fun deleteAll()

    @Query("SELECT * FROM translations WHERE originalText LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun search(query: String): Flow<List<TranslationEntity>>
}
```

**Step 3: AppDatabase.kt**

```kotlin
package com.translite.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.translite.app.data.db.entity.TranslationEntity

@Database(
    entities = [TranslationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun translationDao(): TranslationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "translite.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
```

**Verification:**
```bash
grep "@Dao" ~/Documents/HermesAnywhere/TransLite/app/src/main/java/com/translite/app/data/db/TranslationDao.kt
# Expected: 1 match
```

---

## Task 6: Translation Engine Interface + ML Kit Implementation

**Objective:** Define translation engine abstraction and implement ML Kit offline translator.

**Files:**
- Create: `TransLite/app/src/main/java/com/translite/app/domain/engine/TranslationEngine.kt`
- Create: `TransLite/app/src/main/java/com/translite/app/domain/engine/MlKitTranslator.kt`
- Create: `TransLite/app/src/main/java/com/translite/app/domain/engine/FallbackTranslator.kt`
- Create: `TransLite/app/src/main/java/com/translite/app/domain/languagemanager/LanguagePackageManager.kt`

**Step 1: TranslationEngine.kt**

```kotlin
package com.translite.app.domain.engine

import com.translite.app.domain.model.Language
import com.translite.app.domain.model.TranslationResult
import kotlinx.coroutines.flow.Flow

interface TranslationEngine {
    suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): Result<TranslationResult>

    fun observeDownloadingLanguages(): Flow<Set<String>>

    suspend fun isLanguageDownloaded(lang: Language): Boolean

    suspend fun downloadLanguage(lang: Language): Flow<Float>

    suspend fun deleteLanguageModel(lang: Language)
}
```

**Step 2: MlKitTranslator.kt**

```kotlin
package com.translite.app.domain.engine

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.translite.app.domain.model.Language
import com.translite.app.domain.model.TranslationRequest
import com.translite.app.domain.model.TranslationResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class MlKitTranslator : TranslationEngine {

    private val downloadingLanguages = mutableSetOf<String>()

    override fun observeDownloadingLanguages(): Flow<Set<String>> = callbackFlow {
        trySend(downloadingLanguages.toSet())
        awaitClose {}
    }

    override suspend fun isLanguageDownloaded(lang: Language): Boolean {
        return try {
            val mlLang = toMlLanguage(lang) ?: return false
            val model = Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(mlLang)
                    .setTargetLanguage(mlLang)
                    .build()
            )
            // Model downloaded if we can get the conditions
            val conditions = com.google.mlkit.common.model.RemoteModelManager.getInstance()
                .getDownloadConditionsFor(model)
            model.close()
            true // If no exception, model exists
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun downloadLanguage(lang: Language): Flow<Float> = callbackFlow {
        downloadingLanguages.add(lang.code)
        trySend(0f)

        val mlLang = toMlLanguage(lang)
        if (mlLang == null) {
            close(Exception("Unsupported language: ${lang.code}"))
            return@callbackFlow
        }

        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(mlLang)
                .setTargetLanguage(mlLang)
                .build()
        )

        try {
            val conditions = com.google.mlkit.common.model.RemoteModelManager.getInstance()
                .getDownloadConditionsFor(translator)
            translator.downloadModelIfNeeded(conditions).await()
            downloadingLanguages.remove(lang.code)
            trySend(1f)
            close()
        } catch (e: Exception) {
            downloadingLanguages.remove(lang.code)
            close(e)
        }

        awaitClose { translator.close() }
    }

    override suspend fun deleteLanguageModel(lang: Language) {
        val mlLang = toMlLanguage(lang) ?: return
        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(mlLang)
                .setTargetLanguage(mlLang)
                .build()
        )
        try {
            val modelManager = com.google.mlkit.common.model.RemoteModelManager.getInstance()
            modelManager.deleteDownloadedModel(translator.model).await()
        } catch (_: Exception) { }
        translator.close()
    }

    override suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): Result<TranslationResult> {
        return try {
            val srcMl = toMlLanguage(sourceLang)
                ?: return Result.failure(Exception("Unsupported source: ${sourceLang.code}"))
            val tgtMl = toMlLanguage(targetLang)
                ?: return Result.failure(Exception("Unsupported target: ${targetLang.code}"))

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(srcMl)
                .setTargetLanguage(tgtMl)
                .build()

            val translator = Translation.getClient(options)
            val translated = translator.translate(text).await()
            translator.close()

            Result.success(
                TranslationResult(
                    originalText = text,
                    translatedText = translated,
                    sourceLang = sourceLang,
                    targetLang = targetLang,
                    isOffline = true
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun toMlLanguage(lang: Language): com.google.mlkit.nl.translate.TranslateLanguage? {
        return when (lang) {
            Language.CHINESE -> TranslateLanguage.CHINESE
            Language.ENGLISH -> TranslateLanguage.ENGLISH
            Language.FRENCH -> TranslateLanguage.FRENCH
            Language.SPANISH -> TranslateLanguage.SPANISH
            Language.GERMAN -> TranslateLanguage.GERMAN
            Language.JAPANESE -> TranslateLanguage.JAPANESE
            Language.KOREAN -> TranslateLanguage.KOREAN
            Language.RUSSIAN -> TranslateLanguage.RUSSIAN
            Language.PORTUGUESE -> TranslateLanguage.PORTUGUESE
            Language.ARABIC -> TranslateLanguage.ARABIC
        }
    }
}
```

**Step 3: FallbackTranslator.kt (API fallback)**

```kotlin
package com.translite.app.domain.engine

import com.translite.app.domain.model.Language
import com.translite.app.domain.model.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fallback translator using LibreTranslate API.
 * Used only when local ML Kit model is not downloaded.
 */
class FallbackTranslator(
    private val baseUrl: String = "https://libretranslate.com"
) : TranslationEngine {

    override fun observeDownloadingLanguages(): Flow<Set<String>> = flow { emit(emptySet()) }

    override suspend fun isLanguageDownloaded(lang: Language): Boolean = false

    override suspend fun downloadLanguage(lang: Language): Flow<Float> = flow {
        // No-op for API translator
        emit(1f)
    }

    override suspend fun deleteLanguageModel(lang: Language) { /* no-op */ }

    override suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): Result<TranslationResult> {
        return withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(text, "UTF-8")
                val url = URL("$baseUrl/translate?q=$encoded&source=${sourceLang.code}&target=${targetLang.code}&format=text")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 10000

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    // Simple JSON parse — avoid adding Gson dependency
                    val translatedText = body.substringAfter("\"translatedText\":\"").substringBefore("\"")
                    Result.success(
                        TranslationResult(
                            originalText = text,
                            translatedText = translatedText,
                            sourceLang = sourceLang,
                            targetLang = targetLang,
                            isOffline = false
                        )
                    )
                } else {
                    Result.failure(Exception("API error: ${conn.responseCode}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                // cleanup
            }
        }
    }
}
```

**Step 4: LanguagePackageManager.kt**

```kotlin
package com.translite.app.domain.languagemanager

import com.translite.app.domain.engine.TranslationEngine
import com.translite.app.domain.model.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LanguagePackageManager(
    private val engine: TranslationEngine
) {
    private val _downloadStatus = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadStatus: StateFlow<Map<String, DownloadStatus>> = _downloadStatus

    data class DownloadStatus(
        val language: Language,
        val progress: Float,
        val isDownloading: Boolean,
        val isDownloaded: Boolean
    )

    suspend fun checkAllLanguages(): Map<Language, Boolean> {
        return Language.entries.associateWith { lang ->
            engine.isLanguageDownloaded(lang)
        }
    }

    suspend fun downloadLanguage(lang: Language) {
        val current = _downloadStatus.value.toMutableMap()
        current[lang.code] = DownloadStatus(lang, 0f, true, false)
        _downloadStatus.value = current

        engine.downloadLanguage(lang).collect { progress ->
            val updated = _downloadStatus.value.toMutableMap()
            updated[lang.code] = DownloadStatus(lang, progress, progress < 1f, progress >= 1f)
            _downloadStatus.value = updated
        }
    }

    suspend fun deleteLanguage(lang: Language) {
        engine.deleteLanguageModel(lang)
        val current = _downloadStatus.value.toMutableMap()
        current.remove(lang.code)
        _downloadStatus.value = current
    }
}

sealed class DownloadEvent {
    data class Progress(val language: Language, val percent: Float) : DownloadEvent()
    data class Complete(val language: Language) : DownloadEvent()
    data class Error(val language: Language, val error: Throwable) : DownloadEvent()
}
```

**Verification:**
```bash
grep "interface TranslationEngine" ~/Documents/HermesAnywhere/TransLite/app/src/main/java/com/translite/app/domain/engine/TranslationEngine.kt
# Expected: 1 match
```

---

## Task 7: OCR Engine

**Objective:** Implement ML Kit text recognition for camera and screen capture.

**Files:**
- Create: `TransLite/app/src/main/java/com/translite/app/domain/ocr/OcrEngine.kt`
- Create: `TransLite/app/src/main/java/com/translite/app/domain/ocr/MlKitOcr.kt`

**Step 1: OcrEngine.kt**

```kotlin
package com.translite.app.domain.ocr

import android.net.Uri

interface OcrEngine {
    suspend fun recognizeFromUri(uri: Uri): Result<String>
    suspend fun recognizeFromByteArray(imageData: ByteArray, width: Int, height: Int): Result<String>
}
```

**Step 2: MlKitOcr.kt**

```kotlin
package com.translite.app.domain.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class MlKitOcr(context: Context) : OcrEngine {

    private val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val chineseRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    override suspend fun recognizeFromUri(uri: Uri): Result<String> {
        return try {
            val context = /* need app context - pass in constructor */
                throw NotImplementedError("Use recognizeFromBitmap for direct input")
            Result.success("")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recognizeFromByteArray(
        imageData: ByteArray,
        width: Int,
        height: Int
    ): Result<String> {
        return try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(imageData))
            recognizeFromBitmap(bitmap)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recognizeFromBitmap(bitmap: Bitmap): Result<String> {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)

            // Try Chinese recognizer first (handles both Chinese + Latin)
            try {
                val result = chineseRecognizer.process(image).await()
                if (result.text.isNotBlank()) {
                    return Result.success(result.text)
                }
            } catch (_: Exception) { }

            // Fallback to Latin recognizer
            val result = latinRecognizer.process(image).await()
            Result.success(result.text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun close() {
        latinRecognizer.close()
        chineseRecognizer.close()
    }
}
```

**Verification:**
```bash
grep "interface OcrEngine" ~/Documents/HermesAnywhere/TransLite/app/src/main/java/com/translite/app/domain/ocr/OcrEngine.kt
```

---

## Task 8: Translation Repository

**Objective:** Create repository layer bridging engine + database.

**Files:**
- Create: `TransLite/app/src/main/java/com/translite/app/data/repository/TranslationRepository.kt`

```kotlin
package com.translite.app.data.repository

import com.translite.app.data.db.TranslationDao
import com.translite.app.data.db.entity.TranslationEntity
import com.translite.app.domain.engine.TranslationEngine
import com.translite.app.domain.model.Language
import com.translite.app.domain.model.TranslationResult
import kotlinx.coroutines.flow.Flow

class TranslationRepository(
    private val engine: TranslationEngine,
    private val dao: TranslationDao
) {
    suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): Result<TranslationResult> {
        val result = engine.translate(text, sourceLang, targetLang)

        result.onSuccess { translation ->
            // Cache successful translation
            dao.insert(
                TranslationEntity(
                    originalText = translation.originalText,
                    translatedText = translation.translatedText,
                    sourceLangCode = translation.sourceLang.code,
                    targetLangCode = translation.targetLang.code,
                    isOffline = translation.isOffline
                )
            )
        }

        return result
    }

    fun getHistory(): Flow<List<TranslationEntity>> = dao.getAll()

    fun getFavorites(): Flow<List<TranslationEntity>> = dao.getFavorites()

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        dao.setFavorite(id, isFavorite)
    }

    suspend fun deleteTranslation(id: Long) = dao.deleteById(id)

    suspend fun clearHistory() = dao.deleteAll()

    fun searchHistory(query: String): Flow<List<TranslationEntity>> = dao.search(query)
}
```

**Verification:**
```bash
grep "class TranslationRepository" ~/Documents/HermesAnywhere/TransLite/app/src/main/java/com/translite/app/data/repository/TranslationRepository.kt
```

---

## Task 9: Main ViewModel

**Objective:** Create ViewModel coordinating UI state, translation, and history.

**Files:**
- Create: `TransLite/app/src/main/java/com/translite/app/ui/viewmodel/TranslationViewModel.kt`

```kotlin
package com.translite.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.translite.app.data.db.entity.TranslationEntity
import com.translite.app.data.repository.TranslationRepository
import com.translite.app.domain.model.Language
import com.translite.app.domain.model.TranslationResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TranslationUiState(
    val inputText: String = "",
    val resultText: String = "",
    val sourceLang: Language = Language.AUTO,
    val targetLang: Language = Language.CHINESE,
    val isTranslating: Boolean = false,
    val error: String? = null,
    val languages: List<Language> = Language.entries.toList()
)

class TranslationViewModel(
    private val repository: TranslationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TranslationUiState())
    val uiState: StateFlow<TranslationUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<TranslationEntity>> = repository.getHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<TranslationEntity>> = repository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun setSourceLang(lang: Language) {
        _uiState.update { it.copy(sourceLang = lang) }
    }

    fun setTargetLang(lang: Language) {
        _uiState.update { it.copy(targetLang = lang) }
    }

    fun swapLanguages() {
        _uiState.update {
            it.copy(
                sourceLang = it.targetLang,
                targetLang = it.sourceLang,
                inputText = it.resultText,
                resultText = it.inputText
            )
        }
    }

    fun translate() {
        val state = _uiState.value
        if (state.inputText.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isTranslating = true, error = null) }

            val sourceLang = if (state.sourceLang == Language.AUTO) Language.ENGLISH else state.sourceLang

            repository.translate(state.inputText, sourceLang, state.targetLang)
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            resultText = result.translatedText,
                            isTranslating = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            error = e.message ?: "翻译失败",
                            isTranslating = false
                        )
                    }
                }
        }
    }

    fun clearInput() {
        _uiState.update { it.copy(inputText = "", resultText = "", error = null) }
    }

    fun toggleFavorite(entity: TranslationEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(entity.id, !entity.isFavorite)
        }
    }

    fun deleteTranslation(id: Long) {
        viewModelScope.launch {
            repository.deleteTranslation(id)
        }
    }
}
```

Note: Add `AUTO` to Language enum:
```kotlin
// Add to Language.kt enum:
AUTO("auto", "自动检测", "Auto Detect"),
```

**Verification:**
```bash
grep "class TranslationViewModel" ~/Documents/HermesAnywhere/TransLite/app/src/main/java/com/translite/app/ui/viewmodel/TranslationViewModel.kt
```

---

## Task 10: Home Screen Composable

**Objective:** Build the main translation input/output UI.

**Files:**
- Create: `TransLite/app/src/main/java/com/translite/app/ui/screens/HomeScreen.kt`
- Create: `TransLite/app/src/main/java/com/translite/app/ui/components/TranslationInput.kt`
- Create: `TransLite/app/src/main/java/com/translite/app/ui/components/TranslationResult.kt`

**Step 1: HomeScreen.kt**

```kotlin
package com.translite.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.translite.app.domain.model.Language
import com.translite.app.ui.components.TranslationInput
import com.translite.app.ui.components.TranslationResultCard
import com.translite.app.ui.viewmodel.TranslationUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: TranslationUiState,
    onInputChange: (String) -> Unit,
    onTranslate: () -> Unit,
    onSwapLanguages: () -> Unit,
    onSourceLangChange: (Language) -> Unit,
    onTargetLangChange: (Language) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSourceLangPicker by remember { mutableStateOf(false) }
    var showTargetLangPicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Language selector bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Source language chip
            FilterChip(
                selected = true,
                onClick = { showSourceLangPicker = true },
                label = { Text(uiState.sourceLang.displayName) },
                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) }
            )

            IconButton(onClick = onSwapLanguages) {
                Icon(Icons.Default.SwapHoriz, contentDescription = "交换语言")
            }

            // Target language chip
            FilterChip(
                selected = true,
                onClick = { showTargetLangPicker = true },
                label = { Text(uiState.targetLang.displayName) },
                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) }
            )
        }

        // Input area
        TranslationInput(
            text = uiState.inputText,
            onTextChange = onInputChange,
            hint = "输入要翻译的文本...",
            modifier = Modifier.weight(1f, fill = false)
        )

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Clear, contentDescription = "清除")
            }

            Button(
                onClick = onTranslate,
                enabled = uiState.inputText.isNotBlank() && !uiState.isTranslating
            ) {
                if (uiState.isTranslating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("翻译")
            }
        }

        // Error display
        uiState.error?.let { error ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Result
        if (uiState.resultText.isNotBlank()) {
            TranslationResultCard(text = uiState.resultText)
        }
    }

    // Language pickers
    if (showSourceLangPicker) {
        LanguagePickerDialog(
            languages = uiState.languages,
            onSelect = { lang ->
                onSourceLangChange(lang)
                showSourceLangPicker = false
            },
            onDismiss = { showSourceLangPicker = false }
        )
    }

    if (showTargetLangPicker) {
        LanguagePickerDialog(
            languages = uiState.languages.filter { it != Language.AUTO },
            onSelect = { lang ->
                onTargetLangChange(lang)
                showTargetLangPicker = false
            },
            onDismiss = { showTargetLangPicker = false }
        )
    }
}

@Composable
fun LanguagePickerDialog(
    languages: List<Language>,
    onSelect: (Language) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择语言") },
        text = {
            Column {
                languages.forEach { lang ->
                    TextButton(
                        onClick = { onSelect(lang) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${lang.displayName} (${lang.displayNameEn})")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
```

**Step 2: TranslationInput.kt**

```kotlin
package com.translite.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TranslationInput(
    text: String,
    onTextChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = modifier.fillMaxWidth().heightIn(min = 120.dp),
        placeholder = { Text(hint) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}
```

**Step 3: TranslationResult.kt**

```kotlin
package com.translite.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun TranslationResultCard(
    text: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("translation", text))
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                }
            }
        }
    }
}
```

**Verification:**
```bash
grep "@Composable" ~/Documents/HermesAnywhere/TransLite/app/src/main/java/com/translite/app/ui/screens/HomeScreen.kt | wc -l
# Expected: 3+ composables
```

---

## Task 11: MainActivity + Navigation

**Objective:** Wire up MainActivity with navigation and DI setup.

**Files:**
- Create: `TransLite/app/src/main/java/com/translite/app/MainActivity.kt`

```kotlin
package com.translite.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.translite.app.data.db.AppDatabase
import com.translite.app.data.repository.TranslationRepository
import com.translite.app.domain.engine.FallbackTranslator
import com.translite.app.domain.engine.MlKitTranslator
import com.translite.app.ui.screens.HomeScreen
import com.translite.app.ui.theme.TransLiteTheme
import com.translite.app.ui.viewmodel.TranslationViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: TranslationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Simple DI (no Hilt for now)
        val db = AppDatabase.getInstance(this)
        val engine = MlKitTranslator()
        val repository = TranslationRepository(engine, db.translationDao())
        viewModel = TranslationViewModel(repository)

        setContent {
            TransLiteTheme {
                MainApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: TranslationViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TransLite") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Translate, contentDescription = null) },
                    label = { Text("翻译") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("历史") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("设置") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> HomeScreen(
                uiState = uiState,
                onInputChange = viewModel::updateInput,
                onTranslate = viewModel::translate,
                onSwapLanguages = viewModel::swapLanguages,
                onSourceLangChange = viewModel::setSourceLang,
                onTargetLangChange = viewModel::setTargetLang,
                onClear = viewModel::clearInput,
                modifier = Modifier.padding(padding)
            )
            1 -> HistoryScreen(
                modifier = Modifier.padding(padding)
            )
            2 -> SettingsScreen(
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text("历史记录 - Coming Soon")
    }
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text("设置 - Coming Soon")
    }
}
```

**Verification:**
```bash
grep "class MainActivity" ~/Documents/HermesAnywhere/TransLite/app/src/main/java/com/translite/app/MainActivity.kt
```

---

## Task 12: ProGuard Rules

**Objective:** Add ProGuard rules for ML Kit and Room.

**Files:**
- Create: `TransLite/app/proguard-rules.pro`

```proguard
# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
```

---

## Task 13: Build Verification (Full Assemble)

**Objective:** Build the entire project to verify compilation.

**Commands:**
```bash
export JAVA_HOME=~/java/jdk-17.0.14+7
export ANDROID_HOME=~/Library/Android/sdk
cd ~/Documents/HermesAnywhere/TransLite
chmod +x gradlew 2>/dev/null || true
./gradlew assembleDebug 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL or compilation errors to fix.

---

## Summary: Files Created

```
TransLite/
├── .hermes/plans/2026-05-09-translite-phase1-foundation.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── local.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/translite/app/
        │   ├── TransLiteApp.kt
        │   ├── MainActivity.kt
        │   ├── data/
        │   │   ├── db/
        │   │   │   ├── AppDatabase.kt
        │   │   │   ├── TranslationDao.kt
        │   │   │   └── entity/TranslationEntity.kt
        │   │   └── repository/
        │   │       └── TranslationRepository.kt
        │   ├── domain/
        │   │   ├── model/
        │   │   │   ├── Language.kt
        │   │   │   └── TranslationModels.kt
        │   │   ├── engine/
        │   │   │   ├── TranslationEngine.kt
        │   │   │   ├── MlKitTranslator.kt
        │   │   │   └── FallbackTranslator.kt
        │   │   ├── ocr/
        │   │   │   ├── OcrEngine.kt
        │   │   │   └── MlKitOcr.kt
        │   │   └── languagemanager/
        │   │       └── LanguagePackageManager.kt
        │   └── ui/
        │       ├── theme/
        │       │   ├── Color.kt
        │       │   ├── Type.kt
        │       │   └── Theme.kt
        │       ├── screens/
        │       │   └── HomeScreen.kt
        │       ├── components/
        │       │   ├── TranslationInput.kt
        │       │   └── TranslationResult.kt
        │       └── viewmodel/
        │           └── TranslationViewModel.kt
        └── res/
            ├── values/
            │   ├── strings.xml
            │   └── themes.xml
            └── mipmap-*/ (auto-generated icons)
```
