package com.tomessa.miff.testsupport

import com.google.firebase.firestore.FirebaseFirestoreException
import com.tomessa.miff.data.profile.MyProfileLoad
import com.tomessa.miff.data.profile.MyProfileRepository
import com.tomessa.miff.data.profile.MyProfileStore
import com.tomessa.miff.data.profile.ProfileSaveResult
import com.tomessa.miff.data.settings.LanguageStore
import com.tomessa.miff.domain.profile.MyProfile
import com.tomessa.miff.domain.settings.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The profile of the person signed in, as `users/{uid}` would hold it, and what a save does. */
class FakeMyProfileRepository(
    var stored: MyProfile? = MyProfile("Léa", "Martin", "+33612345678", null),
) : MyProfileRepository {
    var readResult: MyProfileLoad? = null
    var reads = 0
    val saved = mutableListOf<MyProfile>()

    /** Answers of the next saves, one per call; the last one is repeated. */
    var results = ArrayDeque<ProfileSaveResult>()
    var onSave: () -> Unit = {}

    override suspend fun read(uid: String): MyProfileLoad {
        reads++
        return readResult ?: stored?.let { MyProfileLoad.Loaded(it) } ?: MyProfileLoad.Unavailable
    }

    override suspend fun save(
        uid: String,
        profile: MyProfile,
    ): ProfileSaveResult {
        onSave()
        saved += profile
        val result = if (results.size > 1) results.removeFirst() else results.firstOrNull() ?: ProfileSaveResult.SAVED
        if (result == ProfileSaveResult.SAVED || result == ProfileSaveResult.SAVED_PARTIAL) stored = profile
        return result
    }
}

/** Firestore behind [com.tomessa.miff.data.profile.FirestoreMyProfileRepository]: records what would be written. */
class FakeMyProfileStore : MyProfileStore {
    class Commit(
        val writeProfile: Boolean,
        val miffIds: List<String>,
        val data: Map<String, Any?>,
    )

    var fields: Map<String, Any?>? = null
    var memberships: List<String> = emptyList()
    var membershipsFailure: Exception? = null
    val commits = mutableListOf<Commit>()

    /** What the next commits do: null = succeeds, an exception = fails with it. The last one is repeated. */
    var commitFailures = ArrayDeque<Exception?>()
    var commitBehaviour: (Commit) -> Exception? = { null }

    override suspend fun readProfile(uid: String): Map<String, Any?>? = fields

    override suspend fun membershipMiffIds(uid: String): List<String> {
        membershipsFailure?.let { throw it }
        return memberships
    }

    override suspend fun commit(
        uid: String,
        data: Map<String, Any?>,
        writeProfile: Boolean,
        miffIds: List<String>,
    ) {
        val commit = Commit(writeProfile, miffIds, data)
        commits += commit
        val failure =
            if (commitFailures.isNotEmpty()) {
                if (commitFailures.size > 1) commitFailures.removeFirst() else commitFailures.first()
            } else {
                commitBehaviour(commit)
            }
        if (failure != null) throw failure
    }
}

fun firestoreError(code: FirebaseFirestoreException.Code): FirebaseFirestoreException =
    FirebaseFirestoreException("test", code)

class FakeLanguageStore(
    initial: AppLanguage = AppLanguage.SYSTEM,
) : LanguageStore {
    private val mutable = MutableStateFlow(initial)
    override val selected: StateFlow<AppLanguage> = mutable.asStateFlow()
    val selections = mutableListOf<AppLanguage>()
    var refreshes = 0

    override fun select(language: AppLanguage) {
        selections += language
        mutable.value = language
    }

    override fun refresh() {
        refreshes++
    }
}
