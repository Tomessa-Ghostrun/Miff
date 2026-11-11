package com.tomessa.miff.ui.profile

import com.tomessa.miff.data.profile.CurrentProfile
import com.tomessa.miff.data.profile.MyProfileLoad
import com.tomessa.miff.testsupport.FakeMyProfileRepository
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.ui.components.ConnectionGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Bug of 2026-11-10 on the Profile tab: no profile and no network = the same calm pop-in, the same attempt every 10 s that reads only with a network. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileTabConnectionTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = FakeMyProfileRepository()
    private val current = CurrentProfile(repo) { "uid-1" }
    private val network = FakeNetwork(online = false)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun noProfileAndNoNetwork_thePopInAppears_theTimerReadsNothing_thenTheNetworkComesBack() =
        runTest(dispatcher) {
            repo.readResult = MyProfileLoad.Unavailable
            val vm = ProfileTabViewModel(current, ConnectionGate(network))
            runCurrent()
            val job = launch { vm.observe() }
            advanceTimeBy(60_000)
            assertTrue(vm.connection.value.popInVisible)
            assertEquals(1, repo.reads)
            // The network is back: one reading at once, the profile arrives, the pop-in leaves by itself.
            repo.readResult = null
            network.online = true
            runCurrent()
            assertEquals(2, repo.reads)
            assertEquals(ProfileTabStatus.READY, vm.uiState.value.status)
            assertFalse(vm.connection.value.active)
            job.cancel()
        }

    @Test
    fun leavingTheTab_stopsThePopIn() =
        runTest(dispatcher) {
            repo.readResult = MyProfileLoad.Unavailable
            val vm = ProfileTabViewModel(current, ConnectionGate(network))
            runCurrent()
            val job = launch { vm.observe() }
            advanceTimeBy(5_000)
            assertTrue(vm.connection.value.popInVisible)
            job.cancel()
            runCurrent()
            assertFalse(vm.connection.value.active)
        }
}
