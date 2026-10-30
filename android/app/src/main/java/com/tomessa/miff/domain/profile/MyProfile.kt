package com.tomessa.miff.domain.profile

/**
 * The profile of the person signed in, as `users/{uid}` holds it (names, phone number in E.164, photo). Personal data:
 * [toString] never shows it, so that an accidental log line or crash report cannot carry a name or a number.
 */
class MyProfile(
    val firstName: String,
    val lastName: String,
    /** E.164 ("+33612345678"), null for an account created before the phone number became mandatory. */
    val phone: String?,
    /** Square JPEG of at most 20 000 bytes, without any metadata. */
    val avatarJpeg: ByteArray?,
) {
    val displayName: String get() = "$firstName $lastName".trim()

    override fun equals(other: Any?): Boolean =
        other is MyProfile &&
            firstName == other.firstName &&
            lastName == other.lastName &&
            phone == other.phone &&
            avatarJpeg.contentEqualsNullable(other.avatarJpeg)

    override fun hashCode(): Int {
        var result = firstName.hashCode()
        result = 31 * result + lastName.hashCode()
        result = 31 * result + (phone?.hashCode() ?: 0)
        result = 31 * result + (avatarJpeg?.contentHashCode() ?: 0)
        return result
    }

    override fun toString(): String = "MyProfile(redacted)"
}

/** Same bytes (or both absent). */
fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean =
    if (this == null || other == null) this == null && other == null else this.contentEquals(other)

/**
 * The letters shown in place of the photo (Profile tab): the first letter of the first name and of the last name, in
 * capitals. Works on whole characters (an emoji or a letter outside the basic plane is never cut in two); a name that
 * starts with something that is not a letter or a digit (an emoji, a quote) is used as typed.
 */
object Initials {
    fun of(
        firstName: String,
        lastName: String,
    ): String = (firstSymbol(firstName) + firstSymbol(lastName)).ifEmpty { "?" }

    private fun firstSymbol(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return ""
        val first = String(Character.toChars(trimmed.codePointAt(0)))
        return first.uppercase(java.util.Locale.ROOT)
    }
}
