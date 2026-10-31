package com.tomessa.miff.testsupport

import com.tomessa.miff.data.miff.ActiveMiffStore
import com.tomessa.miff.data.miff.CreatePreparation
import com.tomessa.miff.data.miff.CreateResult
import com.tomessa.miff.data.miff.EntitlementRead
import com.tomessa.miff.data.miff.JoinPreparation
import com.tomessa.miff.data.miff.JoinResult
import com.tomessa.miff.data.miff.LocationAccessProbe
import com.tomessa.miff.data.miff.LostMiff
import com.tomessa.miff.data.miff.MiffObserver
import com.tomessa.miff.data.miff.MiffRead
import com.tomessa.miff.data.miff.MiffRepository
import com.tomessa.miff.data.miff.MiffStore
import com.tomessa.miff.data.miff.MiffWriteResult
import com.tomessa.miff.data.miff.MissingProfileException
import com.tomessa.miff.data.miff.MyMiffsLoad
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.LocationAccess
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.miff.MiffDetails
import com.tomessa.miff.domain.miff.MiffObservation
import com.tomessa.miff.domain.miff.ProfileCopy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

const val MIFF_ID = "AbCdEfGhIjKlMnOpQrSt"
const val OTHER_MIFF_ID = "ZyXwVuTsRqPoNmLkJiHg"
const val CODE = "XTZRSP367988"

/** What Firestore holds and what was asked of it, to prove the order of the reads and what was written. */
class FakeMiffStore : MiffStore {
    /** `joinCodes`: code -> {miffId, miffName}. */
    val codes = mutableMapOf<String, Map<String, Any?>>()
    var memberships: List<MembershipRef> = emptyList()
    var membershipsFailure: Exception? = null
    var cachedMembershipsFailure: Exception? = null

    /** What `get miffs/{id}` answers by id: a value, or an exception (a refusal of the rules, a network error). */
    val miffs = mutableMapOf<String, Any>()
    var entitlement: EntitlementRead? = null
    var reachable = true
    var profile: ProfileCopy? = ProfileCopy("Léa", "Martin", "+33612345678", null)

    /** Every lot, in order, as it was handed to the transaction. */
    var commitFailures = ArrayDeque<Exception?>()

    /** Every call, in order ("code", "memberships", "miff:ID", "commit"...). */
    val calls = mutableListOf<String>()

    /** Runs when a lot is committed, before it is recorded as done (to simulate a lot that leaves but is not confirmed). */
    var onCommit: () -> Unit = {}

    override suspend fun readJoinCode(code: String): Map<String, Any?>? {
        calls += "code"
        return codes[code]
    }

    override suspend fun readMemberships(
        uid: String,
        fromServer: Boolean,
    ): List<MembershipRef> {
        calls += if (fromServer) "memberships:server" else "memberships:cache"
        if (fromServer) membershipsFailure?.let { throw it }
        if (!fromServer) cachedMembershipsFailure?.let { throw it }
        return memberships
    }

    override suspend fun readMiff(
        miffId: String,
        fromServer: Boolean,
    ): MiffRead {
        calls += "miff:$miffId"
        return when (val answer = miffs[miffId]) {
            null -> MiffRead.Missing
            is Exception -> throw answer
            is MiffDetails -> MiffRead.Exists(answer)
            else -> error("unexpected")
        }
    }

    override suspend fun readEntitlement(uid: String): EntitlementRead? {
        calls += "entitlement"
        return entitlement
    }

    var sharing: Boolean? = null

    override suspend fun readSharing(
        miffId: String,
        uid: String,
    ): Boolean? {
        calls += "sharing"
        return sharing
    }

    override suspend fun serverReachable(uid: String): Boolean {
        calls += "reachable"
        return reachable
    }

    override suspend fun commitWithProfile(
        uid: String,
        build: (ProfileCopy) -> List<DocWrite>,
    ) {
        calls += "commit"
        val profile = profile ?: throw MissingProfileException()
        val writes = build(profile)
        record(writes)
    }

    override suspend fun commit(writes: List<DocWrite>) {
        calls += "commit"
        record(writes)
    }

    private fun record(writes: List<DocWrite>) {
        val failure = if (commitFailures.isEmpty()) null else commitFailures.removeFirst()
        onCommit()
        // A failed lot writes NOTHING: it is not recorded as done (atomicity).
        if (failure != null) throw failure
        commitsAsLots += listOf(writes)
    }

    /** One entry per lot that went through. */
    val commitsAsLots = mutableListOf<List<DocWrite>>()
}

class FakeLocationProbe(
    var access: LocationAccess = LocationAccess.UNKNOWN,
) : LocationAccessProbe {
    override fun current() = access
}

fun miffDetails(
    id: String = MIFF_ID,
    name: String = "Chez nous",
    code: String = CODE,
    owner: String = "uid-owner",
) = MiffDetails(id, name, code, owner)

class MemoryActiveMiffStore : ActiveMiffStore {
    var value: String? = null

    override fun get() = value

    override fun set(miffId: String) {
        value = miffId
    }

    override fun clear() {
        value = null
    }
}

class FakeMiffObserver : MiffObserver {
    val flows = mutableMapOf<String, MutableSharedFlow<MiffObservation>>()

    fun flowOf(id: String): MutableSharedFlow<MiffObservation> =
        flows.getOrPut(id) { MutableSharedFlow(replay = 1, extraBufferCapacity = 8) }

    override fun observe(miffId: String): Flow<MiffObservation> = flowOf(miffId)
}

/** The repository as the view models see it. */
class FakeMiffRepository : MiffRepository {
    var createPreparation: CreatePreparation = CreatePreparation.Ready(premium = false, firstMiff = true)
    var createResult: CreateResult = CreateResult.Created(MIFF_ID, CODE, "Chez nous", firstMiff = true)
    var joinPreparation: JoinPreparation =
        JoinPreparation.Ready(MIFF_ID, "Chez nous", CODE, premium = false, firstMiff = true)
    var joinResult: JoinResult = JoinResult.Joined(MIFF_ID, firstMiff = true)
    var renameResult: MiffWriteResult = MiffWriteResult.Done
    var sharingResult: MiffWriteResult = MiffWriteResult.Done
    var myMiffsResult: MyMiffsLoad = MyMiffsLoad.Loaded(emptyList(), emptyList(), premium = false, fromCache = false)
    val observer = FakeMiffObserver()

    val calls = mutableListOf<String>()
    val created = mutableListOf<String>()
    val prepareJoinCodes = mutableListOf<String>()
    val renamed = mutableListOf<Triple<String, String, String>>()
    val sharingChanges = mutableListOf<Boolean>()
    val cleaned = mutableListOf<LostMiff>()
    var onCreate: () -> Unit = {}
    var onJoin: () -> Unit = {}

    override suspend fun prepareCreate(uid: String): CreatePreparation {
        calls += "prepareCreate"
        return createPreparation
    }

    override suspend fun create(
        uid: String,
        name: String,
        preparation: CreatePreparation.Ready,
    ): CreateResult {
        calls += "create"
        created += name
        onCreate()
        return createResult
    }

    override suspend fun prepareJoin(
        uid: String,
        typedCode: String,
    ): JoinPreparation {
        calls += "prepareJoin"
        prepareJoinCodes += typedCode
        return joinPreparation
    }

    override suspend fun join(
        uid: String,
        preparation: JoinPreparation.Ready,
    ): JoinResult {
        calls += "join"
        onJoin()
        return joinResult
    }

    override suspend fun rename(
        miffId: String,
        code: String,
        name: String,
    ): MiffWriteResult {
        calls += "rename"
        renamed += Triple(miffId, code, name)
        return renameResult
    }

    override suspend fun setSharing(
        miffId: String,
        uid: String,
        enabled: Boolean,
    ): MiffWriteResult {
        calls += "setSharing"
        sharingChanges += enabled
        return sharingResult
    }

    override suspend fun myMiffs(uid: String): MyMiffsLoad {
        calls += "myMiffs"
        return myMiffsResult
    }

    override suspend fun cleanUpLost(
        uid: String,
        lost: LostMiff,
    ): Boolean {
        calls += "cleanUp"
        cleaned += lost
        return true
    }

    override fun observe(miffId: String): Flow<MiffObservation> = observer.observe(miffId)
}
