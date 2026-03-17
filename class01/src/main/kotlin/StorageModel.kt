package org.jetbrains.edu.sed2026.class01

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

data class TripPlanRequest(val id: Int, val userId: Long, val cities: List<String>, val segments: List<TripSegment> = emptyList())

@Serializable
data class TripSegment(
    val id: Int = 0,
    val tripId: Int = 0,
    val origin: String,
    val destination: String,
    @Serializable(with = LocalDateSerializer::class)
    val arrivalDate: LocalDate,
    @Serializable(with = LocalDateSerializer::class)
    val departureDate: LocalDate,
    val flightId: Int? = null
)

data class Flight(
    val id: Int = 0,
    val flightNumber: String,
    val departureTime: String,
    val arrivalTime: String
)
