package com.tomessa.miff.domain.profile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI
import java.net.URISyntaxException

/*
 * SECURITY, for this file: the address of the Google photo and the photo itself are personal data. They are never logged,
 * never put in an analytics event, never in an exception message; the photo only lives in memory until it is written with
 * the profile like any other profile photo (or thrown away).
 */

/**
 * Which address of a Google photo we accept to download, and at which size (Android, "la photo Google devient la photo de
 * profil", decision of the PM, 2026-11-08).
 *
 * Only `https` addresses of `googleusercontent.com` (and its sub-domains, e.g. `lh3.googleusercontent.com`) are accepted:
 * the address comes from the sign-in provider, but it is still data from the network, so nothing else is ever contacted
 * (no other host, no user info in the address, no other port). The picture that Google serves for an account WITHOUT a
 * photo (a generic silhouette) is recognised and refused: it would be a worse profile photo than none.
 */
object GooglePhotoUrl {
    /** Side asked to Google, in pixels: a bit more than the 160 px of the stored avatar, so the reduction stays sharp. */
    const val REQUEST_SIDE = 256

    private const val HOST = "googleusercontent.com"
    private const val MAX_URL_LENGTH = 2048

    /** `...=s96-c`, `...=s96`, `...=w96-h96-c`: the size suffix that Google reads at the end of the address. */
    private val SIZE_SUFFIX = Regex("=[swh][0-9]+(-[A-Za-z0-9]+)*$")

    /** Old style address: `.../s96-c/photo.jpg`. */
    private val SIZE_SEGMENT = Regex("/s[0-9]+(-c)?/([^/]+)$")

    /** Old style generic picture: the folder made only of "A" that Google used when the account had no photo. */
    private val LEGACY_PLACEHOLDER = Regex("/A{11}/")

    /**
     * The address to download for a photo given by the provider, or null if there is nothing acceptable (no address, not
     * Google, not https, generic picture). The size asked is [REQUEST_SIDE].
     */
    fun select(raw: String?): String? {
        val trimmed = raw?.trim()?.substringBefore('#') ?: return null
        if (trimmed.isEmpty() || trimmed.length > MAX_URL_LENGTH) return null
        if (!isAllowed(trimmed) || isGenericPicture(trimmed)) return null
        val sized = withRequestedSize(trimmed)
        return sized.takeIf { isAllowed(it) }
    }

    /** True for an address that may be contacted at all: https, a Google picture host, no user info, default port. */
    fun isAllowed(url: String): Boolean {
        if (url.length > MAX_URL_LENGTH) return false
        val uri =
            try {
                URI(url)
            } catch (e: URISyntaxException) {
                return false
            }
        val host = uri.host?.lowercase() ?: return false
        val hostIsGoogle = host == HOST || host.endsWith(".$HOST")
        return uri.scheme.equals("https", ignoreCase = true) &&
            hostIsGoogle &&
            uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443)
    }

    /** The generic silhouette Google serves to an account that has no photo of its own. */
    fun isGenericPicture(url: String): Boolean {
        val path = url.substringBefore('?')
        return path.contains("/default-user") || LEGACY_PLACEHOLDER.containsMatchIn(path)
    }

    private fun withRequestedSize(url: String): String {
        // An address that already carries a query is left as it is (its size, if any, is not ours to guess).
        if (url.contains('?')) return url
        val size = "s$REQUEST_SIDE-c"
        return when {
            SIZE_SUFFIX.containsMatchIn(url) -> url.replace(SIZE_SUFFIX, "=$size")

            SIZE_SEGMENT.containsMatchIn(url) -> url.replace(SIZE_SEGMENT, "/$size/$2")

            // No size at all: only when the last segment has no "=" parameter yet.
            !url.substringAfterLast('/').contains('=') -> "$url=$size"

            else -> url
        }
    }
}

/**
 * Downloads the bytes of the photo at an address already accepted by [GooglePhotoUrl]. Returns null on ANY failure (no
 * network, slow answer, refused, too big, not an image...): the sign-up never waits for it and never says why it failed.
 * The implementation checks the address again, and every redirect, with [GooglePhotoUrl.isAllowed].
 */
fun interface GooglePhotoSource {
    suspend fun fetch(url: String): ByteArray?
}

/**
 * The whole path from the address given by the provider to the avatar JPEG of the profile: select the address, download
 * ONCE, decode, square-crop in the centre, re-encode WITHOUT METADATA in at most 20 000 bytes (the very same tools as the
 * photo the person picks: [AvatarCodec], [AvatarCompression]). Null when anything fails, including after [timeoutMs]:
 * never an exception, never blocking beyond that delay.
 */
class GooglePhotoPrefill(
    private val source: GooglePhotoSource,
    private val codec: AvatarCodec,
    private val timeoutMs: Long = TIMEOUT_MS,
) {
    suspend fun load(rawUrl: String?): ByteArray? {
        val url = GooglePhotoUrl.select(rawUrl) ?: return null
        return try {
            withTimeoutOrNull(timeoutMs) {
                val bytes = source.fetch(url) ?: return@withTimeoutOrNull null
                val photo = codec.decodeBytes(bytes) ?: return@withTimeoutOrNull null
                val crop =
                    CropMath.toCrop(photo.width, photo.height, CropMath.MIN_ZOOM, photo.width / 2f, photo.height / 2f)
                codec.encode(photo, crop)?.takeIf { it.size in 1..AvatarCompression.MAX_BYTES }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        /** Short on purpose: the photo is a nicety, the person must not wait for it (the profile step is already usable). */
        const val TIMEOUT_MS = 8_000L
    }
}
