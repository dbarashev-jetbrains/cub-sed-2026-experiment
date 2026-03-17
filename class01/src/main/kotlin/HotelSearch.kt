package org.jetbrains.edu.sed2026.class01
import io.github.serpro69.kfaker.Faker
import java.math.BigDecimal
import kotlin.random.Random

data class HotelSearchResult(
    val hotelName: String,
    val dailyPrice: Money,
    val currency: String,
    val location: String,
    val score10: Double
)

interface HotelSearch {
    fun searchHotels(location: String): List<HotelSearchResult>
}

class DummyHotelSearch: HotelSearch {
    private val faker = Faker()

    override fun searchHotels(location: String): List<HotelSearchResult> {
        return (1..10).map {
            HotelSearchResult(
                hotelName = faker.greekPhilosophers.names() + " " + faker.address.city() + " Hotel",
                dailyPrice = Random.nextDouble(1.0, 200.0).roundToTwoDecimals(),
                currency = "USD",
                location = location,
                score10 = Random.nextDouble(1.0, 10.0).roundToOneDecimal()
            )
        }
    }
}

private fun Double.roundToOneDecimal(): Double {
    return BigDecimal.valueOf(this).setScale(1, java.math.RoundingMode.HALF_UP).toDouble()
}

private fun Double.roundToTwoDecimals(): Money {
    return BigDecimal.valueOf(this).setScale(2, java.math.RoundingMode.HALF_UP).toDouble()
}

class HotelSearchWithCurrency(private val hotelSearch: HotelSearch, private val currencyConverter: CurrencyConverter, private val targetCurrency: String): HotelSearch {
    override fun searchHotels(location: String): List<HotelSearchResult> {
        return hotelSearch.searchHotels(location).map { result ->
            val convertedDailyPrice = currencyConverter.convert(result.dailyPrice, result.currency,targetCurrency)
            convertedDailyPrice?.let { result.copy(currency = targetCurrency, dailyPrice = it) } ?: result
        }
    }
}