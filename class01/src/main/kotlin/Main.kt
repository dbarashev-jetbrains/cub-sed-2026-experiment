package org.jetbrains.edu.sed2026.class01

import org.jetbrains.edu.sed2026.DuffelFlightSearchService
import org.jetbrains.edu.sed2026.SerpFlightSearchService
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob


class TeleTripBot(
    private val botToken: String,
    private val addTripDialog: AddTripDialog,
    private val storage: Storage
) : TelegramLongPollingBot() {


    override fun getBotUsername() =
        System.getenv("TELEGRAM_BOT_USERNAME") ?: error("TELEGRAM_BOT_USERNAME environment variable not set")

    override fun getBotToken() = botToken

    override fun onUpdateReceived(update: Update) {
        if (!update.hasMessage() || !update.message.hasText()) return

        val responseFactory = TelegramResponseFactory(this)
        processRequest(TelegramRequest(update.message.from.id, update.message.text), responseFactory)
    }

    fun processRequest(request: TelegramRequest, responseFactory: TelegramResponseFactory) {
        val userId = request.userId
        val text = request.message
        when {
            text == "/start" -> sendMainMenu(userId, responseFactory)
            text == "My Trips" -> showTrips(userId, responseFactory)
            else -> {
                addTripDialog.processRequest(request, responseFactory).onFailure {
                    sendMainMenu(userId, responseFactory)
                }
            }
        }
    }

    private fun sendMainMenu(userId: Long, responseFactory: TelegramResponseFactory) {
        addTripDialog.reset(userId)
        doSendMainMenu(userId, responseFactory)
    }

    private fun showTrips(userId: Long, responseFactory: TelegramResponseFactory) {
        val trips = storage.getTrips(userId)

        val text = if (trips.isEmpty()) {
            "You don't have any trips yet."
        } else {
            "Your trips:\n\n" + trips.joinToString("\n\n") { trip ->
                val dates = if (trip.segments.isNotEmpty()) {
                    "📅 ${trip.segments.first().arrivalDate} to ${trip.segments.last().departureDate}"
                } else ""
                "🌍 Cities: ${trip.cities.joinToString(" -> ")}\n$dates"
            }
        }

        responseFactory.createTextResponse().run {
            copy(chatId = userId.toString(), message = text)
        }.execute()
    }
}

fun doSendMainMenu(userId: Long, responseFactory: ResponseFactory) {
    responseFactory.createTextResponse().run {
        copy(chatId = userId.toString(), message = "Welcome to TeleTrip! Choose an option:", buttons = listOf(listOf("My Trips"), listOf("Add TripPlanRequest")))
    }.execute()
}

fun main() {
    val botToken = System.getenv("TELEGRAM_BOT_TOKEN") ?: error("TELEGRAM_BOT_TOKEN environment variable not set")
    val storage = SQLiteStorage("teletrip.db")
    val tripService = TripService(storage)

    val serpSearch = SerpFlightSearchImpl(SerpFlightSearchService())

    val duffelSearch = DuffelFlightSearchImpl(DuffelFlightSearchService())
    val resilientSearch = ResilientFlightSearch(serpSearch, duffelSearch)

    val currencyConverter = CachingCurrencyConverter()
    val mainFlightSearch = FlightSearchWithCurrency(resilientSearch, currencyConverter, "CZK")
    val hotelSearch = HotelSearchWithCurrency(DummyHotelSearch(), currencyConverter, "CZK")
    val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val tripPlanner = TripPlanner(mainFlightSearch, hotelSearch, coroutineScope)

    val botsApi = TelegramBotsApi(DefaultBotSession::class.java)
    val addTripDialog = AddTripDialog(storage, tripService, tripPlanner)
    val bot = TeleTripBot(botToken, addTripDialog, storage)
    botsApi.registerBot(bot)

    println("TeleTrip bot is running...")
}