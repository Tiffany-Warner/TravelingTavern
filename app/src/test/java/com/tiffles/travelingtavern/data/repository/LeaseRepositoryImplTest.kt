package com.tiffles.travelingtavern.data.repository

import android.util.Log
import com.tiffles.travelingtavern.data.bridge.BridgeClient
import com.tiffles.travelingtavern.data.bridge.BridgeRepository
import com.tiffles.travelingtavern.data.bridge.LeaseGate
import com.tiffles.travelingtavern.data.bridge.LeaseStateStore
import com.tiffles.travelingtavern.data.webview.WebViewHolder
import com.tiffles.travelingtavern.domain.model.ModelBanner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** The banner's Connect (A7): each answer the page can give, and the ones it can't. */
@OptIn(ExperimentalCoroutinesApi::class)
class LeaseRepositoryImplTest {

    private val store = LeaseStateStore(appLeaseVersion = 1)
    private val bridge = BridgeRepository(store)
    private val client: BridgeClient = mockk(relaxed = true)
    private val gate: LeaseGate = mockk {
        every { waiting } returns MutableStateFlow(false)
        every { stillLoading } returns MutableSharedFlow()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    private fun TestScope.repository() =
        LeaseRepositoryImpl(store, client, bridge, mockk<WebViewHolder>(relaxed = true), gate, backgroundScope)

    private fun leaseState(connected: Boolean) = bridge.onRawMessage(
        """{"v":1,"type":"leaseState","state":"holder","reason":null,"pageId":"t1.l1","epoch":3,""" +
            """"busy":false,"connected":$connected,"origin":"http://192.0.2.1:8000"}"""
    )

    /** The banner is up: a holder still not connected after a takeover. */
    private fun bannerShown() {
        leaseState(connected = false)
        bridge.onRawMessage("""{"v":1,"type":"leaseNotice","kind":"notConnected"}""")
    }

    /** The page answers connectModel with [outcome]; [connectedFirst] reports `connected: true` before it, as the lease does. */
    private fun pageAnswers(outcome: String, connected: Boolean, connectedFirst: Boolean = connected) {
        coEvery { client.connectModel(any()) } coAnswers {
            if (connectedFirst) leaseState(connected = true)
            bridge.onRawMessage("""{"v":1,"type":"connectResult","requestId":"other","outcome":"pressed","connected":true}""")
            bridge.onRawMessage(
                """{"v":1,"type":"connectResult","requestId":"${firstArg<String>()}","outcome":"$outcome","connected":$connected}"""
            )
            true
        }
    }

    @Test
    fun `pressed and connected clears the banner`() = runTest {
        bannerShown()
        pageAnswers("pressed", connected = true)

        repository().connectModel()

        assertNull(store.modelBanner.value)
    }

    @Test
    fun `pressed and still not connected points to API Connections`() = runTest {
        bannerShown()
        pageAnswers("pressed", connected = false)

        repository().connectModel()

        assertEquals(ModelBanner(stillNotConnected = true), store.modelBanner.value)
    }

    @Test
    fun `already connecting, then still not connected, points to API Connections`() = runTest {
        bannerShown()
        pageAnswers("alreadyConnecting", connected = false)

        repository().connectModel()

        assertEquals(ModelBanner(stillNotConnected = true), store.modelBanner.value)
    }

    @Test
    fun `already connecting, then connected, clears the banner`() = runTest {
        bannerShown()
        pageAnswers("alreadyConnecting", connected = true)

        repository().connectModel()

        assertNull(store.modelBanner.value)
    }

    @Test
    fun `no button for the selected API points to API Connections`() = runTest {
        bannerShown()
        pageAnswers("noButton", connected = false)

        repository().connectModel()

        assertEquals(ModelBanner(stillNotConnected = true), store.modelBanner.value)
    }

    @Test
    fun `an answer of connected without the lease's report yet keeps the first text`() = runTest {
        bannerShown()
        pageAnswers("pressed", connected = true, connectedFirst = false)

        repository().connectModel()

        assertEquals(ModelBanner(), store.modelBanner.value)
    }

    @Test
    fun `refused by the pre-check leaves the banner as it was`() = runTest {
        bannerShown()
        coEvery { client.connectModel(any()) } returns false

        repository().connectModel()

        assertEquals(ModelBanner(), store.modelBanner.value)
    }

    @Test
    fun `no answer in time counts as still not connected`() = runTest {
        bannerShown()
        coEvery { client.connectModel(any()) } returns true

        repository().connectModel()

        assertEquals(ModelBanner(stillNotConnected = true), store.modelBanner.value)
    }

    @Test
    fun `nothing is sent without a banner`() = runTest {
        leaseState(connected = false)

        repository().connectModel()

        coVerify(exactly = 0) { client.connectModel(any()) }
        assertNull(store.modelBanner.value)
    }
}
