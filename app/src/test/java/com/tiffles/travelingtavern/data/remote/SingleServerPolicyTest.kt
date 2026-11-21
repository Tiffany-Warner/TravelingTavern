package com.tiffles.travelingtavern.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleServerPolicyTest {

    private val policy = SingleServerPolicy("http://192.0.2.1:8000/")

    @Test
    fun `allows the test install after normalization`() {
        assertTrue(policy.allows("http://192.0.2.1:8000"))
    }

    @Test
    fun `refuses another host`() {
        assertFalse(policy.allows("http://192.0.2.2:8000"))
    }

    @Test
    fun `refuses another port or scheme on the same host`() {
        assertFalse(policy.allows("http://192.0.2.1:8080"))
        assertFalse(policy.allows("https://192.0.2.1:8000"))
    }

    @Test
    fun `an unset address allows nothing`() {
        assertFalse(SingleServerPolicy("").allows("http://192.0.2.1:8000"))
    }
}
