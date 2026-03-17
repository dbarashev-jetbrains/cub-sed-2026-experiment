package org.jetbrains.edu.sed2026.class01

import com.google.gson.Gson
import okhttp3.OkHttpClient


import com.google.gson.JsonObject
import okhttp3.Request

interface CurrencyConverter {
    fun convert(amount: Money, fromCurrency: String, toCurrency: String): Money?
}

class ExpensiveFrankfurterConverter: CurrencyConverter {
    private val client = OkHttpClient()
    private val gson = Gson()

    init {
        Thread.sleep(2000)
    }
    override fun convert(amount: Money, fromCurrency: String, toCurrency: String): Money? {
        // Call Frankfurter API: https://frankfurter.dev/
        val url = "https://api.frankfurter.app/latest?from=$fromCurrency&to=$toCurrency"
        val request = Request.Builder().url(url).build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val rates = json.getAsJsonObject("rates")
                    val rate = rates.get(toCurrency).asDouble
                    rate
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            println("Error getting rate for $fromCurrency: ${e.message}")
            null
        }
    }
}

// THIS IS A FACADE TO THE EXPENSIVE FRANKFURTER CONVERTER.
class CachingCurrencyConverter: CurrencyConverter {
    private val realConverter: CurrencyConverter by lazy { ExpensiveFrankfurterConverter() }
    private val cache = mutableMapOf<String, Double>()
    override fun convert(
        amount: Money,
        fromCurrency: String,
        toCurrency: String
    ): Money? {
        val cacheKey = "$fromCurrency-$toCurrency"
        if (cache.containsKey(cacheKey)) {
            return cache[cacheKey]
        }
        return realConverter.convert(amount, fromCurrency, toCurrency)?.also {
            cache[cacheKey] = it
        }
    }
}