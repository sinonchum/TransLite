package com.translite.app.domain.model

enum class Language(val code: String, val displayName: String, val displayNameEn: String) {
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
