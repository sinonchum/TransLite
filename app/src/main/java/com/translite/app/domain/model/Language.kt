package com.translite.app.domain.model

import android.content.Context
import android.os.Build
import java.util.Locale

enum class Language(val code: String, val displayNameZh: String, val displayNameEn: String) {
    AUTO("auto", "自动检测", "Auto Detect"),
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

    /** Returns display name based on app locale.
     *  Pass application context to get the locale set via updateConfiguration. */
    fun getDisplayName(context: Context? = null): String {
        val appLang = if (context != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.resources.configuration.locales[0]?.language
            } else {
                @Suppress("DEPRECATION")
                context.resources.configuration.locale?.language
            }
        } else null

        val lang = appLang ?: Locale.getDefault().language
        return if (lang == "zh") displayNameZh else displayNameEn
    }

    /** Legacy property — uses system locale (may not match app locale after updateConfiguration) */
    val displayName: String
        get() {
            val lang = Locale.getDefault().language
            return if (lang == "zh") displayNameZh else displayNameEn
        }

    companion object {
        fun fromCode(code: String): Language? =
            entries.find { it.code == code }

        val supportedPairs: List<Pair<Language, Language>> by lazy {
            entries.filter { it != AUTO }.flatMap { src ->
                entries.filter { it != AUTO && it != src }.map { src to it }
            }
        }
    }
}
