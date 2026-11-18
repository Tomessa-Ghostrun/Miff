package com.tomessa.miff.ui.alert

import com.tomessa.miff.data.alert.AlertController
import com.tomessa.miff.data.alert.AlertStore
import com.tomessa.miff.data.alert.AlertsHeard
import com.tomessa.miff.data.alert.EndResult
import com.tomessa.miff.data.alert.MemoryAlertLocalStore
import com.tomessa.miff.data.alert.MineRead
import com.tomessa.miff.data.alert.StartResult
import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.miff.FacesRead
import com.tomessa.miff.data.miff.MemberFacesReader
import com.tomessa.miff.domain.alert.AlertDialTarget
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.testsupport.FakeActiveDevice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The list of the screen "avertis" is NEVER empty (review of #275, M2): the emergency services are there at once, the people of the Miff join them when they are read. */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class NoStore : AlertStore {
        override suspend fun start(
            uid: String,
            miffId: String,
            deviceId: String,
            nowMillis: Long,
        ): StartResult = StartResult.Started(nowMillis, nowMillis)

        override suspend fun end(
            uid: String,
            miffId: String,
            deviceId: String,
            nowMillis: Long,
        ): EndResult = EndResult.Ended

        override suspend fun readMine(
            uid: String,
            miffId: String,
        ): MineRead = MineRead.Value(null)

        override fun observe(miffId: String): Flow<AlertsHeard> = emptyFlow()
    }

    private class SlowFaces : MemberFacesReader {
        val gate = CompletableDeferred<FacesRead>()

        override suspend fun read(miffId: String): FacesRead = gate.await()
    }

    private fun member(
        uid: String,
        first: String,
        owner: Boolean = false,
    ) = MiffMember(uid, first, "X", null, if (owner) MiffRole.OWNER else MiffRole.MEMBER, true, "+33612345678")

    private fun vm(faces: MemberFacesReader): AlertViewModel {
        val controller =
            AlertController(
                store = NoStore(),
                device = FakeActiveDevice(),
                network =
                    object : NetworkMonitor {
                        override fun isOnline() = true
                    },
                local = MemoryAlertLocalStore(),
                currentUid = { "me" },
                scope = kotlinx.coroutines.CoroutineScope(dispatcher),
            )
        return AlertViewModel(controller, MemoryAlertLocalStore(), faces, { "me" }, { "FR" })
    }

    @Test
    fun beforeAnyReading_theEmergencyServicesAreThere_112Chosen_17And15And18Too() {
        val model = vm(SlowFaces())
        val state = model.call.value as CallState.Ready
        assertEquals(listOf("17", "15", "18", "112"), state.list.emergency.map { it.number })
        assertEquals("112", state.selected.number)
        assertTrue(state.list.people.isEmpty())
    }

    @Test
    fun whileTheMembersLoad_theListIsAlreadyUsable_thenThePeopleJoin_andTheCreatorIsChosen() =
        runTest(dispatcher) {
            val faces = SlowFaces()
            val model = vm(faces)
            model.loadCallList("m1")
            dispatcher.scheduler.runCurrent()
            val loading = model.call.value as CallState.Ready
            assertTrue(loading.loadingPeople)
            assertEquals("112", loading.selected.number)
            faces.gate.complete(FacesRead.Loaded(listOf(member("c", "Emmanuelle", owner = true), member("me", "Moi"))))
            dispatcher.scheduler.runCurrent()
            val ready = model.call.value as CallState.Ready
            assertFalse(ready.loadingPeople)
            assertEquals("p:c", ready.selected.id)
            assertEquals(listOf("Emmanuelle"), ready.list.people.map { it.name })
        }

    @Test
    fun aChoiceMadeWhileLoading_isNeverReplaced() =
        runTest(dispatcher) {
            val faces = SlowFaces()
            val model = vm(faces)
            model.loadCallList("m1")
            dispatcher.scheduler.runCurrent()
            val police = (model.call.value as CallState.Ready).list.targets.first { it.number == "17" }
            model.select(police)
            faces.gate.complete(FacesRead.Loaded(listOf(member("c", "Emmanuelle", owner = true))))
            dispatcher.scheduler.runCurrent()
            assertEquals("17", (model.call.value as CallState.Ready).selected.number)
        }

    @Test
    fun membersThatCannotBeRead_keepTheServices_andSayItCalmly() =
        runTest(dispatcher) {
            val faces = SlowFaces()
            val model = vm(faces)
            model.loadCallList("m1")
            faces.gate.complete(FacesRead.Failed)
            dispatcher.scheduler.runCurrent()
            val state = model.call.value as CallState.Ready
            assertTrue(state.contactsUnavailable)
            assertEquals("112", state.selected.number)
        }

    @Test
    fun noMiffKnown_onlyTheServices_noReading() =
        runTest(dispatcher) {
            val faces = SlowFaces()
            val model = vm(faces)
            model.loadCallList("")
            dispatcher.scheduler.runCurrent()
            val state = model.call.value as CallState.Ready
            assertFalse(state.loadingPeople)
            assertEquals(AlertDialTarget.Emergency::class, state.selected::class)
        }
}
