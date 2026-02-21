package com.jimandreas.state

data class OcrSettings(
    val enabled: Boolean = false,
    val language: String = "eng"
) {
    companion object {
        val SUPPORTED_LANGUAGES = listOf(
            "eng" to "English",
            "fra" to "French",
            "deu" to "German",
            "spa" to "Spanish",
            "ita" to "Italian",
            "por" to "Portuguese",
            "nld" to "Dutch",
            "pol" to "Polish",
            "rus" to "Russian",
            "jpn" to "Japanese",
            "chi_sim" to "Chinese (Simplified)",
            "chi_tra" to "Chinese (Traditional)",
            "kor" to "Korean",
            "ara" to "Arabic"
        )
    }
}
