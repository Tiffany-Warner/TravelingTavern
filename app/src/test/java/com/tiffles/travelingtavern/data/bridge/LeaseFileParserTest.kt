package com.tiffles.travelingtavern.data.bridge

import org.junit.Assert.assertEquals
import org.junit.Test

class LeaseFileParserTest {

    private fun lease(
        format: String = "1",
        holder: String = "\"t1.l1\"",
        epoch: String = "3",
        claimedAt: String = "\"2026-01-01T00:00:00.000Z\"",
        busy: String = "false",
        active: String = "null",
        extra: String = ""
    ) = """{"format":$format,"holder":$holder,"epoch":$epoch,"claimedAt":$claimedAt,"busy":$busy,"active":$active$extra}"""

    @Test
    fun `a valid lease gives its pair, unknown fields ignored`() {
        assertEquals(
            LeaseFileRead.Found("t1.l1", 3),
            LeaseFileParser.parse(200, lease(extra = ""","newField":{"x":1}""", active = """{"avatar":"a.png","chatFile":"c"}"""))
        )
    }

    @Test
    fun `404 is missing, any other failure status is unreadable`() {
        assertEquals(LeaseFileRead.Missing, LeaseFileParser.parse(404, null))
        assertEquals(LeaseFileRead.Unreadable, LeaseFileParser.parse(403, lease()))
        assertEquals(LeaseFileRead.Unreadable, LeaseFileParser.parse(500, lease()))
        assertEquals(LeaseFileRead.Unreadable, LeaseFileParser.parse(200, null))
    }

    @Test
    fun `a body that isn't a JSON object is unreadable`() {
        assertEquals(LeaseFileRead.Unreadable, LeaseFileParser.parse(200, "not json"))
        assertEquals(LeaseFileRead.Unreadable, LeaseFileParser.parse(200, "[1,2]"))
        assertEquals(LeaseFileRead.Unreadable, LeaseFileParser.parse(200, ""))
    }

    @Test
    fun `a missing or unknown format is an unknown format`() {
        assertEquals(LeaseFileRead.UnknownFormat, LeaseFileParser.parse(200, lease(format = "2")))
        assertEquals(LeaseFileRead.UnknownFormat, LeaseFileParser.parse(200, lease(format = "\"1\"")))
        assertEquals(LeaseFileRead.UnknownFormat, LeaseFileParser.parse(200, lease(format = "null")))
        assertEquals(LeaseFileRead.UnknownFormat, LeaseFileParser.parse(200, """{"holder":"t1.l1","epoch":3}"""))
    }

    @Test
    fun `missing or mistyped required fields are unreadable`() {
        val bad = listOf(
            lease(holder = "\"\""),
            lease(holder = "7"),
            lease(epoch = "0"),
            lease(epoch = "1.5"),
            lease(epoch = "\"3\""),
            lease(claimedAt = "null"),
            lease(busy = "\"false\""),
            lease(active = "\"chat\""),
            """{"format":1,"holder":"t1.l1","epoch":3,"claimedAt":"x","busy":false}"""
        )
        bad.forEach { assertEquals(it, LeaseFileRead.Unreadable, LeaseFileParser.parse(200, it)) }
    }

    @Test
    fun `a whole number written with a fraction counts, as in JavaScript`() {
        assertEquals(LeaseFileRead.Found("t1.l1", 3), LeaseFileParser.parse(200, lease(epoch = "3.0")))
    }
}
