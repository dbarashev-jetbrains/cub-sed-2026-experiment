package org.jetbrains.edu.sed2026.class01

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

class TestTextResponse(
    chatId: String = "",
    message: String = "",
    buttons: List<List<String>> = emptyList(),
    messageId: Int? = null,
    private val responses: MutableList<TestTextResponse>
) : TextResponse(chatId, message, buttons, messageId) {
    override fun execute(): Int? {
        responses.add(this)
        this.messageId = responses.size
        return this.messageId
    }

    override fun update(newMessage: String): Int? {
        val updated = this.copy(message = newMessage) as TestTextResponse
        responses.add(updated)
        return messageId
    }

    override fun copy(
        chatId: String,
        message: String,
        buttons: List<List<String>>,
        messageId: Int?
    ): TextResponse {
        return TestTextResponse(chatId, message, buttons, messageId, responses)
    }
}

class TestResponseFactory : ResponseFactory {
    val responses = mutableListOf<TestTextResponse>()

    override fun createTextResponse(): TextResponse {
        return TestTextResponse(responses = responses)
    }
}

class AddTripDialogTest {

    private fun createTripPlanner(flightSearch: FlightSearch): TripPlanner {
        val hotelSearch = DummyHotelSearch()
        val coroutineScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        return TripPlanner(flightSearch, hotelSearch, coroutineScope)
    }

    @Test
    fun `test full trip addition process`() {
        val responseFactory = TestResponseFactory()
        val storage = InMemoryStorage()
        val tripService = TripService(storage)
        val tripPlanner = createTripPlanner(MockFlightSearch())
        val bot = AddTripDialog(storage, tripService, tripPlanner)
        bot.sendMainMenu = { userId ->
            bot.reset(userId)
            doSendMainMenu(userId, responseFactory)
        }

        val userId = 123L

        // 2. Click "Add TripPlanRequest"
        bot.processRequest(TelegramRequest(userId, "Add TripPlanRequest"), responseFactory)
        assertEquals(1, responseFactory.responses.size)
        assertTrue(responseFactory.responses.last().message.contains("Enter the cities"))
        responseFactory.responses.clear()

        // 3. Enter cities
        bot.processRequest(TelegramRequest(userId, "Paris, London"), responseFactory)
        assertEquals(1, responseFactory.responses.size)
        assertTrue(responseFactory.responses.last().message.contains("Is this a round-trip?"))
        assertEquals(listOf(listOf("Yes", "No")), responseFactory.responses.last().buttons)
        responseFactory.responses.clear()

        // 4. Say No to round-trip
        bot.processRequest(TelegramRequest(userId, "No"), responseFactory)
        assertEquals(1, responseFactory.responses.size)
        assertTrue(responseFactory.responses.last().message.contains("When do you travel from Paris to London?"))
        responseFactory.responses.clear()

        // 5. Enter date
        val travelDate = "2026-05-20"
        bot.processRequest(TelegramRequest(userId, travelDate), responseFactory)
        
        // Should show "Planning your trip...", then the success message AND main menu
        assertEquals(1, responseFactory.responses.size)
        assertTrue(responseFactory.responses[0].message.contains("Planning your trip"))

        // Verify storage
        val trips = storage.getTrips(userId)
        assertEquals(1, trips.size)
        assertEquals(listOf("Paris", "London"), trips[0].cities)
        assertEquals(1, trips[0].segments.size)
        assertEquals("Paris", trips[0].segments[0].origin)
        assertEquals("London", trips[0].segments[0].destination)
        assertEquals(LocalDate.parse(travelDate), trips[0].segments[0].arrivalDate)
    }

    @Test
    fun `test round trip addition process`() {
        val responseFactory = TestResponseFactory()
        val storage = InMemoryStorage()
        val tripService = TripService(storage)
        val tripPlanner = createTripPlanner(MockFlightSearch())
        val bot = AddTripDialog(storage, tripService, tripPlanner)
        bot.sendMainMenu = { userId ->
            bot.reset(userId)
            doSendMainMenu(userId, responseFactory)
        }

        val userId = 124L

        // 1. Start add trip
        bot.processRequest(TelegramRequest(userId, "Add TripPlanRequest"), responseFactory)
        responseFactory.responses.clear()

        // 2. Enter cities
        bot.processRequest(TelegramRequest(userId, "Paris, London"), responseFactory)
        responseFactory.responses.clear()

        // 3. Say Yes to round-trip
        bot.processRequest(TelegramRequest(userId, "Yes"), responseFactory)
        assertEquals(1, responseFactory.responses.size)
        assertTrue(responseFactory.responses.last().message.contains("When do you travel from Paris to London?"))
        responseFactory.responses.clear()

        // 4. Enter first date
        bot.processRequest(TelegramRequest(userId, "2026-06-01"), responseFactory)
        assertEquals(1, responseFactory.responses.size)
        assertTrue(responseFactory.responses.last().message.contains("When do you travel from London to Paris?"))
        responseFactory.responses.clear()

        // 5. Enter second date
        bot.processRequest(TelegramRequest(userId, "2026-06-10"), responseFactory)
        
        // Should show "Planning your trip...", then success message AND main menu
        assertEquals(1, responseFactory.responses.size)
        assertTrue(responseFactory.responses[0].message.contains("Planning your trip"))
        
        // Verify storage
        val trips = storage.getTrips(userId)
        assertEquals(1, trips.size)
        assertEquals(listOf("Paris", "London", "Paris"), trips[0].cities)
        assertEquals(2, trips[0].segments.size)
        assertEquals("Paris", trips[0].segments[0].origin)
        assertEquals("London", trips[0].segments[0].destination)
        assertEquals(LocalDate.of(2026, 6, 1), trips[0].segments[0].arrivalDate)
        assertEquals("London", trips[0].segments[1].origin)
        assertEquals("Paris", trips[0].segments[1].destination)
        assertEquals(LocalDate.of(2026, 6, 10), trips[0].segments[1].arrivalDate)
    }

    @Test
    fun `test invalid date format`() {
        val responseFactory = TestResponseFactory()
        val storage = InMemoryStorage()
        val tripService = TripService(storage)
        val tripPlanner = createTripPlanner(MockFlightSearch())
        val bot = AddTripDialog(storage, tripService, tripPlanner)
        bot.sendMainMenu = { userId ->
            bot.reset(userId)
            doSendMainMenu(userId, responseFactory)
        }

        val userId = 125L

        bot.processRequest(TelegramRequest(userId, "Add TripPlanRequest"), responseFactory)
        bot.processRequest(TelegramRequest(userId, "Paris, London"), responseFactory)
        bot.processRequest(TelegramRequest(userId, "No"), responseFactory)
        responseFactory.responses.clear()

        bot.processRequest(TelegramRequest(userId, "invalid-date"), responseFactory)
        assertEquals(1, responseFactory.responses.size)
        assertTrue(responseFactory.responses.last().message.contains("Invalid date format"))
    }

    @Test
    fun `test date before previous segment`() {
        val responseFactory = TestResponseFactory()
        val storage = InMemoryStorage()
        val tripService = TripService(storage)
        val tripPlanner = createTripPlanner(MockFlightSearch())
        val bot = AddTripDialog(storage, tripService, tripPlanner)
        bot.sendMainMenu = { userId ->
            bot.reset(userId)
            doSendMainMenu(userId, responseFactory)
        }

        val userId = 126L

        bot.processRequest(TelegramRequest(userId, "Add TripPlanRequest"), responseFactory)
        bot.processRequest(TelegramRequest(userId, "Paris, London, Berlin"), responseFactory)
        bot.processRequest(TelegramRequest(userId, "No"), responseFactory)
        bot.processRequest(TelegramRequest(userId, "2026-07-10"), responseFactory) // Paris -> London
        responseFactory.responses.clear()

        bot.processRequest(TelegramRequest(userId, "2026-07-09"), responseFactory) // London -> Berlin (Before)
        assertEquals(1, responseFactory.responses.size)
        assertTrue(responseFactory.responses.last().message.contains("Travel date cannot be before previous travel date"))
    }
}
