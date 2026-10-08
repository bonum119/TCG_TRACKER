package com.example.tcg_tracker.data

data class PokemonCard(
    val id: String,
    val name: String,
    val expansion: String,
    val cardNumber: String,
    val rarity: String,
    val type: String,
    var condition: String = "Near Mint",
    var isOwned: Boolean = false,
    var isWished: Boolean = false,
    var quantity: Int = 1,
    var cardLanguage: String = "Inglés",
    val folderId: String? = null,
    var imageUri: String? = null,
    var priceUsd: Double? = null,
    var priceEur: Double? = null,
    var storeUrl: String? = null
)