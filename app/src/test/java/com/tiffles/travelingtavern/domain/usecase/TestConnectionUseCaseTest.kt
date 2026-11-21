package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.model.ConnectionResult
import com.tiffles.travelingtavern.domain.repository.ConnectionTester
import com.tiffles.travelingtavern.domain.repository.ServerPolicy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TestConnectionUseCaseTest {

    private val tester: ConnectionTester = mockk()
    private var allowed = true
    private val useCase = TestConnectionUseCase(tester, ServerPolicy { allowed })

    @Test
    fun `invalid url short-circuits without hitting the network`() = runTest {
        val result = useCase(rawUrl = "not a url", username = null, password = null)

        assertEquals(ConnectionResult.InvalidUrl, result)
        coVerify(exactly = 0) { tester.test(any(), any(), any()) }
    }

    @Test
    fun `a server this build doesn't allow is refused without hitting the network`() = runTest {
        allowed = false

        val result = useCase(rawUrl = "100.64.0.3:8000", username = null, password = null)

        assertEquals(ConnectionResult.ServerNotAllowed, result)
        coVerify(exactly = 0) { tester.test(any(), any(), any()) }
    }

    @Test
    fun `normalized url and credentials are passed to the tester`() = runTest {
        coEvery { tester.test("http://100.64.0.2:8000", "user", "pass") } returns ConnectionResult.Success

        val result = useCase(rawUrl = "100.64.0.2:8000/", username = "user", password = "pass")

        assertEquals(ConnectionResult.Success, result)
    }

    @Test
    fun `blank credentials are treated as none`() = runTest {
        coEvery { tester.test("http://desktop", null, null) } returns ConnectionResult.AuthRequired

        val result = useCase(rawUrl = "desktop", username = "   ", password = "")

        assertEquals(ConnectionResult.AuthRequired, result)
    }
}
