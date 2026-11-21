package com.tiffles.travelingtavern.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleServerPolicyTest {

    private val policy = SingleServerPolicy("http://100.64.0.2:8000/")

    @Test
    fun `allows the test install after normalization`() {
        assertTrue(policy.allows("http://100.64.0.2:8000"))
    }

    @Test
    fun `refuses another host`() {
        assertFalse(policy.allows("http://100.64.0.3:8000"))
    }

    @Test
    fun `refuses another port or scheme on the same host`() {
        assertFalse(policy.allows("http://100.64.0.2:8001"))
        assertFalse(policy.allows("https://100.64.0.2:8000"))
    }

    @Test
    fun `an unset address allows nothing`() {
        assertFalse(SingleServerPolicy("").allows("http://100.64.0.2:8000"))
    }
}
