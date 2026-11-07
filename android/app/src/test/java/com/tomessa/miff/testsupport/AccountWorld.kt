package com.tomessa.miff.testsupport

import com.google.firebase.firestore.FirebaseFirestoreException
import com.tomessa.miff.data.account.AccountDataStore
import com.tomessa.miff.data.miff.EntitlementRead
import com.tomessa.miff.data.miff.MiffGoneException
import com.tomessa.miff.data.miff.MiffRead
import com.tomessa.miff.data.miff.MiffStore
import com.tomessa.miff.data.miff.MiffTxView
import com.tomessa.miff.data.miff.MissingProfileException
import com.tomessa.miff.data.miff.SharingConsentRead
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.JoinCode
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.miff.MiffDetails
import com.tomessa.miff.domain.miff.ProfileCopy
import com.tomessa.miff.domain.miff.WriteKind

/**
 * The server as the deletion of an account sees it (AUTH-5b): ONE account ([me]) with its indexes, devices and profile, and any number of Miffs (created by
 * [me] or by somebody else). Every lot is a TRANSACTION that applies completely or not at all, and is checked against the constraints of the deployed
 * rules (backend/firestore.rules, section 6.2 of the ticket): a lot that breaks one is REFUSED (PERMISSION_DENIED) exactly as the server would, so a
 * procedure that passes against this double respects them. It also keeps what the lots did, to prove the budgets (writes per lot) and the idempotence.
 */
class AccountWorld(
    val me: String = "uid-1",
    private val timeline: Timeline = Timeline(),
) : MiffStore,
    AccountDataStore {
    class Room(
        val id: String,
        var name: String,
        var code: String,
        val owner: String,
        val members: MutableSet<String> = mutableSetOf(),
        val status: MutableSet<String> = mutableSetOf(),
        val photos: MutableSet<String> = mutableSetOf(),
        val positions: MutableSet<String> = mutableSetOf(),
        val places: MutableSet<String> = mutableSetOf(),
    ) {
        fun copyOf() =
            Room(
                id,
                name,
                code,
                owner,
                members.toMutableSet(),
                status.toMutableSet(),
                photos.toMutableSet(),
                positions.toMutableSet(),
                places.toMutableSet(),
            )
    }

    val rooms = linkedMapOf<String, Room>()

    /** `joinCodes`: code -> Miff. */
    val codes = linkedMapOf<String, String>()

    /** The tombstones `miffsDeleted/{id}`. */
    val tombstones = linkedSetOf<String>()

    /** My indexes `users/{me}/memberships/{key}`: key -> the Miff (null = a document that has not the shape of an index). */
    val indexes = linkedMapOf<String, String?>()
    val devices = mutableListOf<String>()
    var profileExists = true
    var reachable = true

    /** What the server lists as a Miff that does not exist: "missing" (true) or a refusal of the rules (false, what a non-member gets). */
    var missingReadsAsNull = false

    /** Every lot that was APPLIED, in order. */
    val lots = mutableListOf<List<DocWrite>>()

    /** Every call that reached the server, in order ("read:memberships", "lot"...), for ordering checks. */
    val calls = mutableListOf<String>()

    /** The number of lots ATTEMPTED so far (applied or not). */
    var attempts = 0
        private set

    /** Fails the lot with this number (from 0) before anything is applied: null = it goes through. */
    var failLot: (Int) -> Exception? = { null }

    /** The lot with this number is applied, then the answer is lost (the call fails as a network cut would). */
    var loseAnswerAt: (Int) -> Boolean = { false }

    /** Fails a read or a deletion by name ("memberships", "miff", "members", "places", "devices", "count", "profile", "reachable"). */
    var failCall: (String) -> Exception? = { null }

    /** Runs when a named call starts (to make something happen in the middle: a Miff that appears...). */
    var onCall: (String) -> Unit = {}

    private var codeCounter = 0

    // --- Building the world ------------------------------------------------------------------------------------------------------

    private fun nextCode(): String {
        val n = ++codeCounter
        val alphabet = JoinCode.ALPHABET
        return "QQQQQQQQQQ" + alphabet[n / alphabet.length % alphabet.length] + alphabet[n % alphabet.length]
    }

    /** A Miff created by [owner] with [others] as members (each with a card, a status, a photo and a last position). */
    fun addRoom(
        id: String,
        owner: String,
        others: List<String> = emptyList(),
        places: Int = 0,
        name: String = "Miff $id",
        indexKey: String? = null,
    ): Room {
        val room = Room(id, name, nextCode(), owner)
        for (uid in listOf(owner) + others) {
            room.members += uid
            room.status += uid
            room.photos += uid
            room.positions += uid
        }
        repeat(places) { room.places += "place$it" }
        rooms[id] = room
        codes[room.code] = id
        if (me in room.members) indexes[indexKey ?: if (indexes.containsKey("free")) id else "free"] = id
        return room
    }

    /** My index of a Miff that does not exist (or that removed me): the Miff is gone, the index is left. */
    fun addStaleIndex(
        miffId: String,
        key: String = miffId,
    ) {
        indexes[key] = miffId
    }

    /** Somebody arrives in a Miff meanwhile (card, status). */
    fun join(
        roomId: String,
        uid: String,
    ) {
        rooms.getValue(roomId).apply {
            members += uid
            status += uid
            positions += uid
        }
    }

    fun ids(vararg miff: String): List<String> = miff.toList()

    // --- The size of what was written, to check the budgets ------------------------------------------------------------

    val lotSizes get() = lots.map { it.size }

    /** The lots that remove somebody else's card (7 writes, the code renewed). */
    fun removalLots(): List<List<DocWrite>> =
        lots.filter { lot -> lot.any { it.path.contains("/members/") && !it.path.endsWith("/members/$me") } }

    fun finalLots(): List<List<DocWrite>> = lots.filter { lot -> lot.any { it.path.startsWith("miffsDeleted/") } }

    /** The lots "Quitter" and "index seul": no write on a Miff or a code, only my own documents. */
    fun leaveLots(): List<List<DocWrite>> =
        lots.filter { lot -> lot.all { it.path.endsWith("/$me") || it.path.startsWith("users/$me/memberships/") } }

    // --- MiffStore ---------------------------------------------------------------------------------------------------------------

    private fun named(name: String) {
        calls += "read:$name"
        timeline += "read:$name"
        onCall(name)
        failCall(name)?.let { throw it }
    }

    override suspend fun readJoinCode(code: String): Map<String, Any?>? {
        calls += "read:code"
        val miff = codes[code] ?: return null
        return mapOf("miffId" to miff, "miffName" to (rooms[miff]?.name ?: ""))
    }

    override suspend fun readMemberships(
        uid: String,
        fromServer: Boolean,
    ): List<MembershipRef> {
        named("memberships")
        return indexes.mapNotNull { (key, miff) -> miff?.let { MembershipRef(key, it) } }
    }

    override suspend fun readMiff(
        miffId: String,
        fromServer: Boolean,
    ): MiffRead {
        named("miff")
        val room = rooms[miffId]
        if (room == null) {
            if (missingReadsAsNull) return MiffRead.Missing
            throw firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }
        if (me !in room.members) throw firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED)
        return MiffRead.Exists(MiffDetails(room.id, room.name, room.code, room.owner))
    }

    override suspend fun readEntitlement(uid: String): EntitlementRead? = null

    override suspend fun readSharing(
        miffId: String,
        uid: String,
    ): Boolean? = error("not used by the deletion of an account")

    override suspend fun readSharingConsent(
        miffId: String,
        uid: String,
    ): SharingConsentRead? = error("not used by the deletion of an account")

    override suspend fun commitWhereSharing(
        uid: String,
        miffIds: List<String>,
        build: (List<String>) -> List<DocWrite>,
    ): List<String> = error("not used by the deletion of an account")

    override suspend fun serverReachable(uid: String): Boolean {
        named("reachable")
        return reachable
    }

    override suspend fun readMemberIds(miffId: String): List<String> {
        named("members")
        val room = rooms[miffId] ?: throw firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED)
        return room.members.toList()
    }

    override suspend fun readPlaceIds(miffId: String): List<String> {
        named("places")
        val room = rooms[miffId] ?: throw firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED)
        return room.places.toList()
    }

    override suspend fun commitWithMiff(
        miffId: String,
        build: (MiffTxView) -> List<DocWrite>,
    ) {
        val room = rooms[miffId] ?: throw MiffGoneException()
        if (me !in room.members) throw firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED)
        val details = MiffDetails(room.id, room.name, room.code, room.owner)
        val writes = build(MiffTxView(details, codeDocumentExists = room.code in codes))
        commit(writes)
    }

    override suspend fun commitWithProfile(
        uid: String,
        build: (ProfileCopy) -> List<DocWrite>,
    ) {
        if (!profileExists) throw MissingProfileException()
        commit(build(ProfileCopy("Léa", "Martin", "+33612345678", null)))
    }

    override suspend fun commit(writes: List<DocWrite>) {
        val number = attempts++
        calls += "lot"
        timeline += "lot"
        failLot(number)?.let { throw it }
        val lose = loseAnswerAt(number)
        apply(writes)
        lots += listOf(writes)
        if (lose) throw firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE)
    }

    // --- The rules, as far as the deletion needs them -------------------------------------------------------------------------------

    private fun refuse(): Nothing = throw firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED)

    /** Applies the lot on a COPY, checks the constraints of the rules on the result, and only then makes it the state of the world. */
    private fun apply(writes: List<DocWrite>) {
        val rooms2 = rooms.mapValuesTo(linkedMapOf()) { it.value.copyOf() }
        val codes2 = LinkedHashMap(codes)
        val tombstones2 = LinkedHashSet(tombstones)
        val indexes2 = LinkedHashMap(indexes)
        // (room, uid) of a card of somebody else deleted by this lot
        val removedOthers = mutableListOf<Pair<String, String>>()
        val deletedMyCard = mutableSetOf<String>()
        val deletedRooms = mutableSetOf<String>()
        val deletedIndexes = mutableListOf<Pair<String, String?>>() // key, miff
        val deletedCodes = mutableSetOf<String>()
        val newCodes = mutableMapOf<String, String>()
        val renewedRooms = mutableSetOf<String>()
        val deletedPlaces = mutableListOf<String>() // room ids

        for (w in writes) {
            val p = w.path.split("/")
            when {
                p.size == 4 && p[0] == "miffs" && p[2] == "members" && w.kind == WriteKind.DELETE -> {
                    val room = rooms2[p[1]] ?: refuse()
                    // A card that does not exist cannot be deleted: the rule reads `resource.data.role`.
                    if (!room.members.remove(p[3])) refuse()
                    if (p[3] == me) deletedMyCard += p[1] else removedOthers += p[1] to p[3]
                }

                p.size == 4 && p[0] == "miffs" && p[2] == "status" && w.kind == WriteKind.DELETE -> {
                    rooms2[p[1]]?.status?.remove(p[3])
                }

                p.size == 4 && p[0] == "miffs" && p[2] == "photos" && w.kind == WriteKind.DELETE -> {
                    rooms2[p[1]]?.photos?.remove(p[3])
                }

                p.size == 4 && p[0] == "miffs" && p[2] == "positions" && w.kind == WriteKind.DELETE -> {
                    rooms2[p[1]]?.positions?.remove(p[3])
                }

                p.size == 4 && p[0] == "miffs" && p[2] == "places" && w.kind == WriteKind.DELETE -> {
                    val room = rooms2[p[1]] ?: refuse()
                    if (room.owner != me) refuse()
                    if (!room.places.remove(p[3])) refuse()
                    deletedPlaces += p[1]
                }

                p.size == 2 && p[0] == "miffs" && w.kind == WriteKind.UPDATE -> {
                    val room = rooms2[p[1]] ?: refuse()
                    if (room.owner != me) refuse()
                    room.code = w.data["joinCode"] as String
                    renewedRooms += p[1]
                }

                p.size == 2 && p[0] == "miffs" && w.kind == WriteKind.DELETE -> {
                    val room = rooms2[p[1]] ?: refuse()
                    if (room.owner != me) refuse()
                    deletedRooms += p[1]
                }

                p.size == 2 && p[0] == "miffsDeleted" && w.kind == WriteKind.SET -> {
                    tombstones2 += p[1]
                }

                p.size == 2 && p[0] == "joinCodes" && w.kind == WriteKind.SET -> {
                    newCodes[p[1]] = w.data["miffId"] as String
                    codes2[p[1]] = w.data["miffId"] as String
                }

                p.size == 2 && p[0] == "joinCodes" && w.kind == WriteKind.DELETE -> {
                    if (codes2.remove(p[1]) == null) refuse()
                    deletedCodes += p[1]
                }

                p.size == 4 && p[0] == "users" && p[1] == me && p[2] == "memberships" && w.kind == WriteKind.DELETE -> {
                    if (!indexes2.containsKey(p[3])) refuse()
                    deletedIndexes += p[3] to indexes2.remove(p[3])
                }

                else -> {
                    error("a write the deletion of an account must never make: ${w.kind}")
                }
            }
        }
        for (roomId in deletedRooms) rooms2.remove(roomId)

        // --- The constraints (backend/firestore.rules, ticket section 6.2) ---
        for ((roomId, uid) in removedOthers) {
            val room = rooms2[roomId] ?: refuse()
            // Removing another member: the code is renewed in the same lot (the rules refuse it otherwise), and everything of that person goes with the card.
            if (room.owner != me) refuse()
            if (roomId !in renewedRooms || room.code !in newCodes) refuse()
            if (uid in room.status || uid in room.photos || uid in room.positions) refuse()
        }
        for (roomId in deletedMyCard) {
            val room = rooms[roomId] ?: refuse()
            // My card: with my status, photo and last position; and a creator's card only in the lot that deletes the Miff.
            val after = rooms2[roomId]
            if (after != null && (me in after.status || me in after.photos || me in after.positions)) refuse()
            if (room.owner == me && roomId !in deletedRooms) refuse()
        }
        for (roomId in deletedRooms) {
            val room = rooms.getValue(roomId)
            // The Miff: tombstone and current code in the same lot; no removal of somebody else's card in it (the rules read the Miff after the lot).
            if (roomId !in tombstones2 || room.code !in deletedCodes) refuse()
            if (removedOthers.any { it.first == roomId }) refuse()
        }
        for ((_, miff) in deletedIndexes) {
            // An index goes only when I am no longer a member of that Miff after the lot.
            val after = rooms2[miff ?: refuse()]
            if (after != null && me in after.members) refuse()
        }

        rooms.clear()
        rooms.putAll(rooms2)
        codes.clear()
        codes.putAll(codes2)
        tombstones.clear()
        tombstones.addAll(tombstones2)
        indexes.clear()
        indexes.putAll(indexes2)
    }

    // --- AccountDataStore --------------------------------------------------------------------------------------------------------

    override suspend fun membershipCount(uid: String): Int {
        named("count")
        return indexes.size
    }

    override suspend fun deviceIds(uid: String): List<String> {
        named("devices")
        return devices.toList()
    }

    override suspend fun deleteDevice(
        uid: String,
        deviceId: String,
    ) {
        named("device.delete")
        devices.remove(deviceId)
    }

    override suspend fun deleteProfile(uid: String) {
        named("profile")
        profileExists = false
    }
}
