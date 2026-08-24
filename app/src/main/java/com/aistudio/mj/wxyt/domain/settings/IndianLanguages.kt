package com.aistudio.mj.wxyt.domain.settings

/**
 * Languages from India's Eighth Schedule, plus Indian English.
 * The selected language is persisted immediately and used for Maya's
 * response personality, speech recognition hints and local TTS fallback.
 */
data class IndianLanguage(
    val name: String,
    val nativeName: String,
    val localeTag: String
)

object IndianLanguages {
    val all: List<IndianLanguage> = listOf(
        IndianLanguage("Assamese", "অসমীয়া", "as-IN"),
        IndianLanguage("Bengali", "বাংলা", "bn-IN"),
        IndianLanguage("Bodo", "बड़ो", "brx-IN"),
        IndianLanguage("Dogri", "डोगरी", "doi-IN"),
        IndianLanguage("Gujarati", "ગુજરાતી", "gu-IN"),
        IndianLanguage("Hindi", "हिन्दी", "hi-IN"),
        IndianLanguage("Kannada", "ಕನ್ನಡ", "kn-IN"),
        IndianLanguage("Kashmiri", "कॉशुर / کٲشُر", "ks-IN"),
        IndianLanguage("Konkani", "कोंकणी", "kok-IN"),
        IndianLanguage("Maithili", "मैथिली", "mai-IN"),
        IndianLanguage("Malayalam", "മലയാളം", "ml-IN"),
        IndianLanguage("Manipuri", "মেইতেই / মণিপুরী", "mni-IN"),
        IndianLanguage("Marathi", "मराठी", "mr-IN"),
        IndianLanguage("Nepali", "नेपाली", "ne-IN"),
        IndianLanguage("Odia", "ଓଡ଼ିଆ", "or-IN"),
        IndianLanguage("Punjabi", "ਪੰਜਾਬੀ", "pa-IN"),
        IndianLanguage("Sanskrit", "संस्कृतम्", "sa-IN"),
        IndianLanguage("Santali", "ᱥᱟᱱᱛᱟᱲᱤ", "sat-IN"),
        IndianLanguage("Sindhi", "सिन्धी / سنڌي", "sd-IN"),
        IndianLanguage("Tamil", "தமிழ்", "ta-IN"),
        IndianLanguage("Telugu", "తెలుగు", "te-IN"),
        IndianLanguage("Urdu", "اردو", "ur-IN"),
        IndianLanguage("English (India)", "English (India)", "en-IN")
    )

    fun findByName(name: String): IndianLanguage =
        all.firstOrNull { it.name == name } ?: all.first { it.name == "Hindi" }
}
