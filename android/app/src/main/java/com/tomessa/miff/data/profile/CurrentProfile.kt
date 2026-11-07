package com.tomessa.miff.data.profile

import com.tomessa.miff.data.session.SessionTeardown
import com.tomessa.miff.domain.profile.MyProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The profile of the person signed in, kept IN MEMORY for the session: the Profile tab shows it, "Mes informations" edits
 * it and puts the new version back here, so that the header changes as soon as the server has confirmed the save, with
 * no second read.
 *
 * Personal data: never written to the disk, never logged. It goes away with the session ([stop], registered in the
 * teardown list of the [com.tomessa.miff.data.session.SessionManager]) and is also dropped when it was read for another
 * account than the one signed in now.
 */
class CurrentProfile(
    private val repository: MyProfileRepository,
    private val currentUid: () -> String?,
) : SessionTeardown {
    private val mutableProfile = MutableStateFlow<MyProfile?>(null)

    /** Null until the first read succeeded (and after a sign-out). */
    val profile: StateFlow<MyProfile?> = mutableProfile.asStateFlow()

    /** Goes up at each end of a session: an answer that was awaited across it belongs to the previous person. */
    private var epoch = 0

    /** Reads the profile from the server (or the cache of the phone, offline). False = it could not be read. */
    suspend fun refresh(): Boolean {
        val uid = currentUid() ?: return false
        val startedIn = epoch
        return when (val load = repository.read(uid)) {
            is MyProfileLoad.Loaded -> {
                // The session may have ended, or changed, while we were waiting for the answer.
                if (epoch != startedIn || currentUid() != uid) return false
                mutableProfile.value = load.profile
                true
            }

            MyProfileLoad.Unavailable -> {
                false
            }
        }
    }

    /** A save was confirmed for [uid]: this is the profile now. Ignored if that person is not signed in any more. */
    fun onSaved(
        uid: String,
        profile: MyProfile,
    ) {
        if (currentUid() == uid) mutableProfile.value = profile
    }

    override suspend fun stop() {
        epoch++
        mutableProfile.value = null
    }
}
