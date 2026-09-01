package com.example.meezan.domain.parser

import org.junit.Assert.*
import org.junit.Test

class RoadmapParserTest {

    @Test
    fun `parse valid roadmap success`() {
        val input = """
            Goal: Learn Kotlin
            Day 1
            Task: Intro to Syntax
            Duration: 45 min
            Day 2
            Task: Coroutines
            Duration: 1.5 hours
        """.trimIndent()

        val parsed = RoadmapParser.parse(input)
        assertEquals("Learn Kotlin", parsed.goalTitle)
        assertEquals(2, parsed.entries.size)
        assertEquals(1, parsed.entries[0].dayNumber)
        assertEquals(45, parsed.entries[0].durationMinutes)
        assertEquals(2, parsed.entries[1].dayNumber)
        assertEquals(90, parsed.entries[1].durationMinutes)
    }

    @Test(expected = RoadmapParseException::class)
    fun `parse empty input throws`() {
        RoadmapParser.parse("")
    }

    @Test
    fun `parse missing goal throws with line number`() {
        val input = """
            
            Day 1
            Task: Test
            Duration: 10m
        """.trimIndent()
        try {
            RoadmapParser.parse(input)
            fail("Should throw")
        } catch (e: RoadmapParseException) {
            assertTrue(e.message!!.contains("Line 2"))
        }
    }

    @Test
    fun `parse duplicate days throws with line number`() {
        val input = """
            Goal: Test
            Day 1
            Task: T1
            Duration: 10m
            Day 1
            Task: T2
            Duration: 20m
        """.trimIndent()
        try {
            RoadmapParser.parse(input)
            fail("Should have thrown RoadmapParseException for duplicate days")
        } catch (e: RoadmapParseException) {
            assertTrue(e.message!!.contains("Line 5"))
        }
    }

    @Test
    fun `parse duration units handles various formats`() {
        assertEquals(60, RoadmapParser.parseDurationToMinutes("1 hour"))
        assertEquals(120, RoadmapParser.parseDurationToMinutes("2.0 hours"))
        assertEquals(90, RoadmapParser.parseDurationToMinutes("1.5 hr"))
        assertEquals(30, RoadmapParser.parseDurationToMinutes("30 min"))
        assertEquals(10, RoadmapParser.parseDurationToMinutes("10m"))
    }

    @Test(expected = RoadmapParseException::class)
    fun `parse invalid duration unit throws`() {
        RoadmapParser.parseDurationToMinutes("10 lightyears")
    }
}
