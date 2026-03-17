package org.jetbrains.edu.sed2026.class01

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class TripProcessorsTest {

    @Test
    fun `test TripCostProcessor`() {
        val processor = TripCostProcessor()
        val flight = FlightSearchResult("AF123", 100.0, "USD", "PRG", "CDG")
        val hotel = HotelSearchResult("Grand Hotel", 200.0, "USD", "Paris", 8.5)

        processor.process(flight)
        processor.process(hotel)

        assertEquals(300.0, processor.totalCost)
    }

    @Test
    fun `test TripMarkdownProcessor`() {
        val processor = TripMarkdownProcessor()
        val flight = FlightSearchResult("AF123", 100.0, "USD", "PRG", "CDG")
        val hotel = HotelSearchResult("Grand Hotel", 200.0, "USD", "Paris", 8.5)

        processor.process(flight)
        processor.process(hotel)

        val markdown = processor.getMarkdown()
        assertTrue(markdown.contains("### Flight: AF123"))
        assertTrue(markdown.contains("- From: PRG"))
        assertTrue(markdown.contains("- To: CDG"))
        assertTrue(markdown.contains("- Price: 100.0 USD"))
        assertTrue(markdown.contains("### Hotel: Grand Hotel"))
        assertTrue(markdown.contains("- Location: Paris"))
        assertTrue(markdown.contains("- Daily Price: 200.0 USD"))
        assertTrue(markdown.contains("- Score: 8.5/10"))
    }

    @Test
    fun `test PlannedTrip accept`() {
        val flight = FlightSearchResult("AF123", 100.0, "USD", "PRG", "CDG")
        val hotel = HotelSearchResult("Grand Hotel", 200.0, "USD", "Paris", 8.5)
        val plannedTrip = PlannedTrip(listOf(flight), listOf(hotel))

        val costProcessor = TripCostProcessor()
        plannedTrip.accept(costProcessor)
        assertEquals(300.0, costProcessor.totalCost)

        val markdownProcessor = TripMarkdownProcessor()
        plannedTrip.accept(markdownProcessor)
        val markdown = markdownProcessor.getMarkdown()
        assertTrue(markdown.contains("AF123"))
        assertTrue(markdown.contains("Grand Hotel"))
    }
}
