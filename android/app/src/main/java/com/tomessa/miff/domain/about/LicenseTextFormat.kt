package com.tomessa.miff.domain.about

/**
 * The full texts of the licences are written for a page 78 characters wide: shown as they are on a phone (and at the largest font size) every sentence would break
 * in the middle, twice. This puts them back in PARAGRAPHS: a blank line separates two paragraphs, and the line breaks inside a paragraph become spaces. The words
 * are never changed, added or removed (a test checks it on every licence of the app).
 */
object LicenseTextFormat {
    fun paragraphs(text: String): List<String> =
        text
            .replace("\r\n", "\n")
            .trim()
            .split(Regex("\n[ \t]*\n"))
            .map { paragraph ->
                paragraph.trim().replace(Regex("[ \t]*\n[ \t]*"), " ").replace(Regex("[ \t]{2,}"), " ")
            }.filter { it.isNotEmpty() }
}
