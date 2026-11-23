package com.tiffles.travelingtavern.data.repository

import android.util.Log
import com.tiffles.travelingtavern.data.bridge.BridgeClient
import com.tiffles.travelingtavern.data.bridge.BridgeRepository
import com.tiffles.travelingtavern.data.bridge.LeaseGate
import com.tiffles.travelingtavern.data.bridge.LeaseStateStore
import com.tiffles.travelingtavern.data.webview.WebViewHolder
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import com.tiffles.travelingtavern.domain.model.ParkReason
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The scrim's and the off banner's actions reach the right `__ttLease` member (A3). */
@OptIn(ExperimentalCoroutinesApi::class)
class LeaseRepositoryActionsTest {

    private val store = LeaseStateStore(appLeaseVersion = 1)
    private val bridge = BridgeRepository(store)
    private val client: BridgeClient = mockk(relaxed = true)
    private val holder: WebViewHolder = mockk(relaxed = true)
    private val gate: LeaseGate = mockk {
        every { waiting } returns MutableStateFlow(false)
        every { stillLoading } returns MutableSharedFlow()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    private fun TestScope.repository() =
        LeaseRepositoryImpl(store, client, bridge, holder, gate, FakePromptFlag(), backgroundScope, appLeaseVersion = 1)

    private fun parked(reason: String) = bridge.onRawMessage(
        """{"v":1,"type":"leaseState","state":"parked","reason":"$reason","pageId":"t1.l1","epoch":3,""" +
            """"busy":false,"connected":true,"origin":"http://192.0.2.1:8000",""" +
            """"actions":["refresh","takeOverAnyway","turnOffProtection"]}"""
    )

    @Test
    fun `Refresh taken by the page doesn't reload a second time`() = runTest {
        parked("error")
        coEvery { client.leaseAction("refresh") } returns true

        repository().refresh()

        verify(exactly = 0) { holder.reload() }
        assertTrue(store.isTakeoverReload)
    }

    @Test
    fun `Refresh falls back to a page reload when the page declines or doesn't answer`() = runTest {
        parked("error")
        coEvery { client.leaseAction("refresh") } returnsMany listOf(false, null)
        val repository = repository()

        repository.refresh()
        repository.refresh()

        verify(exactly = 2) { holder.reload() }
    }

    @Test
    fun `Take over anyway calls the page and shows claiming at once`() = runTest {
        parked("damaged")

        repository().takeOverAnyway()

        coVerify { client.leaseAction("takeOverAnyway") }
        assertEquals(LeaseStatus.Parked(ParkReason.Claiming), store.status.value)
        assertTrue(store.isTakeoverReload)
    }

    @Test
    fun `Turn off protection and Turn protection back on call the page, never a plain reload`() = runTest {
        parked("error")
        coEvery { client.leaseAction(any()) } returns false
        val repository = repository()

        repository.turnOffProtection()
        repository.turnProtectionOn()

        coVerify { client.leaseAction("turnOffProtection") }
        coVerify { client.leaseAction("turnProtectionOn") }
        verify(exactly = 0) { holder.reload() }
    }
}
