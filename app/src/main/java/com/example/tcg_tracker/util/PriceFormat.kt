package com.example.tcg_tracker.util

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Formato de precios: USD estilo "$1,234.56" y EUR estilo español "1234,56 €". Usar solo en el hilo principal. */
object PriceFormat {
    private val usdFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)
    private val eurFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale("es", "ES")).apply {
        currency = Currency.getInstance("EUR")
    }

    fun usd(value: Double?, whenMissing: String = "N/A"): String =
        value?.let { usdFormat.format(it) } ?: whenMissing

    fun eur(value: Double?, whenMissing: String = "N/A"): String =
        value?.let { eurFormat.format(it) } ?: whenMissing
}
