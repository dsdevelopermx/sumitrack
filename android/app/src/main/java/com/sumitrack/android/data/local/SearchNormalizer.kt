package com.sumitrack.android.data.local

object SearchNormalizer {

    private val ACCENTED_TO_PLAIN = mapOf(
        'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n',
    )

    fun normalize(text: String): String =
        text.lowercase().map { ACCENTED_TO_PLAIN[it] ?: it }.joinToString("")

    fun toLikePattern(query: String): String =
        normalize(query)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
}
