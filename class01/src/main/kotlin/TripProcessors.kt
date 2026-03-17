package org.jetbrains.edu.sed2026.class01

class TripMarkdownProcessor: PlannedTripProcessor {
    private val sb = StringBuilder()

    fun getMarkdown(): String = sb.toString()

    override fun process(flightSegment: FlightSearchResult) {
        sb.append("### Flight: ${flightSegment.flightNumber}\n")
        sb.append("- From: ${flightSegment.origin}\n")
        sb.append("- To: ${flightSegment.destination}\n")
        sb.append("- Price: ${flightSegment.price} ${flightSegment.currency}\n\n")
    }

    override fun process(hotel: HotelSearchResult) {
        sb.append("### Hotel: ${hotel.hotelName}\n")
        sb.append("- Location: ${hotel.location}\n")
        sb.append("- Daily Price: ${hotel.dailyPrice} ${hotel.currency}\n")
        sb.append("- Score: ${hotel.score10}/10\n\n")
    }
}

class TripCostProcessor: PlannedTripProcessor {
    var totalCost: Double = 0.0
        private set

    override fun process(flightSegment: FlightSearchResult) {
        totalCost += flightSegment.price
    }

    override fun process(hotel: HotelSearchResult) {
        totalCost += hotel.dailyPrice
    }
}
