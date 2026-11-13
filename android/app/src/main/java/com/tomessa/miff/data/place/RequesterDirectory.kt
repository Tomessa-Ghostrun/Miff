package com.tomessa.miff.data.place

import com.tomessa.miff.data.session.SessionTeardown
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * PRIVACY, for this file (ticket LIEU-REQ, section 7.1): the centre of notifications is local and stores NO name of a person. The words of its lines about a
 * request ("Léa propose un lieu pour « Famille »") are made when the line is DRAWN, from the first names kept HERE, in memory only, for the session:
 * never in a file, a log or an event, and gone at the sign-out (this class is part of the teardown of the session).
 */

/**
 * The first names of the people who asked for a place, by Miff and by person: what the centre needs to write its lines. Since NOTIF-P1A it also holds the first names of the
 * members the Feed showed (the lines of "Depuis ta dernière visite" are written from them at draw time); same rule: memory only, gone with the session.
 */
class RequesterDirectory : SessionTeardown {
    private val mutable = MutableStateFlow<Map<String, String>>(emptyMap())

    /** The first name by [key] (a Miff and a person). A person whose name is not known yet is not in the map: the line says it without a name. */
    val firstNames: StateFlow<Map<String, String>> = mutable.asStateFlow()

    fun put(
        miffId: String,
        uid: String,
        firstName: String,
    ) {
        if (firstName.isEmpty()) return
        val key = key(miffId, uid)
        if (mutable.value[key] != firstName) mutable.value = mutable.value + (key to firstName)
    }

    override suspend fun stop() {
        mutable.value = emptyMap()
    }

    companion object {
        fun key(
            miffId: String,
            uid: String,
        ): String = "$miffId|$uid"
    }
}
