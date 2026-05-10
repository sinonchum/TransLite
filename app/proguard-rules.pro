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

# LiteRT-LM (Gemma 4) — JNI needs all Java methods intact for native calls
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**

# MediaPipe LLM - ignore missing javax.lang.model classes
-dontwarn javax.lang.model.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.mediapipe.proto.**
-dontwarn com.google.mediapipe.framework.**
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mediapipe.tasks.genai.** { *; }
-keep class com.google.mediapipe.tasks.text.** { *; }
