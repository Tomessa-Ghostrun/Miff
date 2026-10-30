package com.tomessa.miff.testsupport

import com.tomessa.miff.data.auth.ProfileWriteResult
import com.tomessa.miff.data.auth.UserProfileRepository
import com.tomessa.miff.data.onboarding.ProfileDraft
import com.tomessa.miff.data.onboarding.ProfileDraftStore
import com.tomessa.miff.domain.auth.PendingConsent
import com.tomessa.miff.domain.profile.AvatarCodec
import com.tomessa.miff.domain.profile.DecodedPhoto
import com.tomessa.miff.domain.profile.SquareCrop

/** In-memory doubles for the profile steps (Onboarding ticket 6.2 to 6.4). */
class MemoryProfileDraftStore : ProfileDraftStore {
    var stored: ProfileDraft? = null
    var saves = 0
    var clears = 0

    override fun read(uid: String): ProfileDraft? {
        val draft = stored ?: return null
        if (draft.uid != uid) {
            clear()
            return null
        }
        return draft
    }

    override fun save(draft: ProfileDraft) {
        saves++
        stored = draft
    }

    override fun clear() {
        clears++
        stored = null
    }
}

class FakeUserProfileRepository : UserProfileRepository {
    class Created(
        val uid: String,
        val firstName: String,
        val lastName: String,
        val avatarJpeg: ByteArray?,
        val phone: String,
        val consent: PendingConsent,
    )

    val created = mutableListOf<Created>()
    val phonesAdded = mutableListOf<Pair<String, String>>()
    val deleted = mutableListOf<String>()

    /** Answers of the next calls, one per call; the last one is repeated. */
    var results = ArrayDeque<ProfileWriteResult>()
    var onWrite: () -> Unit = {}

    private fun next(): ProfileWriteResult {
        onWrite()
        return if (results.size > 1) results.removeFirst() else results.firstOrNull() ?: ProfileWriteResult.DONE
    }

    val writes get() = created.size + phonesAdded.size + deleted.size

    override suspend fun create(
        uid: String,
        firstName: String,
        lastName: String,
        avatarJpeg: ByteArray?,
        phone: String,
        consent: PendingConsent,
    ): ProfileWriteResult {
        created += Created(uid, firstName, lastName, avatarJpeg, phone, consent)
        return next()
    }

    override suspend fun addPhone(
        uid: String,
        phone: String,
    ): ProfileWriteResult {
        phonesAdded += uid to phone
        return next()
    }

    override suspend fun delete(uid: String): ProfileWriteResult {
        deleted += uid
        return next()
    }
}

class FakePhoto(
    override val width: Int = 2000,
    override val height: Int = 1000,
) : DecodedPhoto

class FakeAvatarCodec : AvatarCodec {
    /** null = "this image cannot be used". */
    var decoded: DecodedPhoto? = FakePhoto()
    var encoded: ByteArray? = ByteArray(9_000) { 1 }
    val decodedSources = mutableListOf<String>()
    val crops = mutableListOf<SquareCrop>()

    override suspend fun decode(source: String): DecodedPhoto? {
        decodedSources += source
        return decoded
    }

    override suspend fun encode(
        photo: DecodedPhoto,
        crop: SquareCrop,
    ): ByteArray? {
        crops += crop
        return encoded
    }
}
