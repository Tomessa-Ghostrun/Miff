package com.tomessa.miff.domain.miff

/**
 * Who left the Miff since the last picture the creator's app had (ticket N3: "Un proche a quitté la Miff. Tu veux créer un nouveau
 * code ?"). Only what the app SAW disappear counts: with no earlier picture (first opening on a phone, cache purged) there is
 * nothing to compare and so no invitation for old departures (ticket 10, case 22). The creator's own card, and the people the
 * creator has just removed (the code was renewed by that very lot), never count.
 */
object MemberDepartures {
    fun between(
        previous: List<MiffMember>?,
        current: List<MiffMember>,
        myUid: String,
        removedByMe: Set<String>,
    ): Set<String> {
        if (previous == null) return emptySet()
        val still = current.map { it.uid }.toSet()
        return previous
            .map { it.uid }
            .filter { it != myUid && it !in still && it !in removedByMe }
            .toSet()
    }
}
