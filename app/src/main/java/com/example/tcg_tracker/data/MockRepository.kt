package com.example.tcg_tracker.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class ExpansionStats(
    val name: String,
    val ownedCount: Int,
    val totalCount: Int,
    val totalEurValue: Double
) {
    val completionPercentage: Int
        get() = if (totalCount == 0) 0 else ((ownedCount.toDouble() / totalCount.toDouble()) * 100).toInt()
}

object MockRepository {

    private var appContext: Context? = null
    private var isInitialized = false

    private val cards = mutableListOf<PokemonCard>()
    private val folders = mutableListOf<CardFolder>()
    private val folderMembers = mutableMapOf<String, MutableSet<String>>()

    fun init(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        isInitialized = true

        loadFromDisk(context.applicationContext)
        if (cards.isEmpty()) {
            seedInitialCards()
            saveToDisk()
        }
    }

    private fun seedInitialCards() {
        cards.clear()
    }

    // ---------------------------------------------------------------- Persistencia JSON

    private fun saveToDisk() {
        val ctx = appContext ?: return
        try {
            // Save cards
            val cardsArray = JSONArray()
            cards.forEach { card ->
                cardsArray.put(JSONObject().apply {
                    put("id", card.id)
                    put("name", card.name)
                    put("expansion", card.expansion)
                    put("cardNumber", card.cardNumber)
                    put("rarity", card.rarity)
                    put("type", card.type)
                    put("condition", card.condition)
                    put("isOwned", card.isOwned)
                    put("isWished", card.isWished)
                    put("quantity", card.quantity)
                    put("folderId", card.folderId ?: JSONObject.NULL)
                    put("imageUri", card.imageUri ?: JSONObject.NULL)
                    put("priceUsd", card.priceUsd ?: JSONObject.NULL)
                    put("priceEur", card.priceEur ?: JSONObject.NULL)
                    put("storeUrl", card.storeUrl ?: JSONObject.NULL)
                })
            }
            File(ctx.filesDir, "cards.json").writeText(cardsArray.toString())

            // Save folders
            val foldersArray = JSONArray()
            folders.forEach { folder ->
                foldersArray.put(JSONObject().apply {
                    put("id", folder.id)
                    put("title", folder.title)
                    put("cardCount", folder.cardCount)
                    put("description", folder.description)
                })
            }
            File(ctx.filesDir, "folders.json").writeText(foldersArray.toString())

            // Save folder members
            val membersObj = JSONObject()
            folderMembers.forEach { (fId, set) ->
                val arr = JSONArray()
                set.forEach { arr.put(it) }
                membersObj.put(fId, arr)
            }
            File(ctx.filesDir, "folder_members.json").writeText(membersObj.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadFromDisk(ctx: Context) {
        try {
            val cardsFile = File(ctx.filesDir, "cards.json")
            if (cardsFile.exists()) {
                val array = JSONArray(cardsFile.readText())
                cards.clear()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    cards.add(
                        PokemonCard(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            expansion = obj.getString("expansion"),
                            cardNumber = obj.optString("cardNumber", "N/A"),
                            rarity = obj.optString("rarity", "Común"),
                            type = obj.optString("type", "Incoloro"),
                            condition = obj.optString("condition", "Near Mint"),
                            isOwned = obj.optBoolean("isOwned", false),
                            isWished = obj.optBoolean("isWished", false),
                            quantity = obj.optInt("quantity", 1),
                            folderId = if (obj.isNull("folderId")) null else obj.optString("folderId"),
                            imageUri = if (obj.isNull("imageUri")) null else obj.optString("imageUri"),
                            priceUsd = if (obj.isNull("priceUsd")) null else obj.optDouble("priceUsd"),
                            priceEur = if (obj.isNull("priceEur")) null else obj.optDouble("priceEur"),
                            storeUrl = if (obj.isNull("storeUrl")) null else obj.optString("storeUrl")
                        )
                    )
                }
            }

            val foldersFile = File(ctx.filesDir, "folders.json")
            if (foldersFile.exists()) {
                val array = JSONArray(foldersFile.readText())
                folders.clear()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    folders.add(
                        CardFolder(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            cardCount = obj.optInt("cardCount", 0),
                            description = obj.optString("description", "")
                        )
                    )
                }
            }

            val membersFile = File(ctx.filesDir, "folder_members.json")
            if (membersFile.exists()) {
                val obj = JSONObject(membersFile.readText())
                folderMembers.clear()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val arr = obj.getJSONArray(key)
                    val set = mutableSetOf<String>()
                    for (i in 0 until arr.length()) {
                        set.add(arr.getString(i))
                    }
                    folderMembers[key] = set
                }
            }
            val sampleIds = setOf(
                "base1-4", "base1-58", "base1-2", "base1-15", "base1-6",
                "dp1-9", "dp1-10", "dp1-2", "swsh12-198", "swsh7-215",
                "swsh8-214", "swsh7-215u", "swsh9-154", "xy1-14", "xy2-35",
                "jp-151-025", "jp-vmax-001", "jp-eevee-091"
            )
            cards.removeAll { sampleIds.contains(it.id) }
            saveToDisk()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ---------------------------------------------------------------- Operaciones de Cartas

    fun addCard(card: PokemonCard) {
        cards.add(0, card)
        saveToDisk()
    }

    fun upsertCard(card: PokemonCard) {
        val existing = cards.find { it.id == card.id }
        if (existing == null) {
            cards.add(card)
        } else {
            if (existing.imageUri.isNullOrEmpty() || existing.imageUri!!.startsWith("http")) {
                existing.imageUri = card.imageUri
            }
            existing.priceUsd = card.priceUsd ?: existing.priceUsd
            existing.priceEur = card.priceEur ?: existing.priceEur
            existing.storeUrl = card.storeUrl ?: existing.storeUrl
        }
        saveToDisk()
    }

    fun getOwnedCards(): List<PokemonCard> {
        return cards.filter { it.isOwned }
    }

    fun getWishedCards(): List<PokemonCard> {
        return cards.filter { it.isWished }
    }

    fun getAllCards(): List<PokemonCard> = cards.toList()

    fun searchLocal(query: String, filters: CardFilters): List<PokemonCard> {
        val q = query.trim()
        return cards.filter { card ->
            val matchesQuery = q.isBlank() ||
                    card.name.contains(q, ignoreCase = true) ||
                    card.expansion.contains(q, ignoreCase = true)
            matchesQuery && filters.matches(card)
        }
    }

    fun getCardById(id: String): PokemonCard? {
        return cards.find { it.id == id }
    }

    fun toggleOwned(id: String): Boolean {
        val card = cards.find { it.id == id } ?: return false
        card.isOwned = !card.isOwned
        saveToDisk()
        return card.isOwned
    }

    fun toggleWished(id: String): Boolean {
        val card = cards.find { it.id == id } ?: return false
        card.isWished = !card.isWished
        saveToDisk()
        return card.isWished
    }

    fun updateCardCondition(id: String, condition: String) {
        val card = cards.find { it.id == id } ?: return
        card.condition = condition
        saveToDisk()
    }

    fun updateCardQuantity(id: String, quantity: Int) {
        val card = cards.find { it.id == id } ?: return
        card.quantity = quantity.coerceAtLeast(1)
        saveToDisk()
    }

    fun updateCardPrices(id: String, priceUsd: Double?, priceEur: Double?, storeUrl: String? = null) {
        val card = cards.find { it.id == id } ?: return
        priceUsd?.let { card.priceUsd = it }
        priceEur?.let { card.priceEur = it }
        storeUrl?.let { card.storeUrl = it }
        saveToDisk()
    }

    fun deleteCard(cardId: String): Boolean {
        val removed = cards.removeAll { it.id == cardId }
        folderMembers.values.forEach { set -> set.remove(cardId) }
        if (removed) saveToDisk()
        return removed
    }

    // ---------------------------------------------------------------- Estads. de Expansiones

    fun getExpansionsWithStats(): List<ExpansionStats> {
        // Obtenemos todas las expansiones únicas del catálogo y de las cartas
        val catalogSets = CardCatalog.collections
            .filter { !it.label.equals(CardCatalog.ALL_F, true) }
            .map { it.label }

        val cardSets = cards.map { it.expansion }.distinct()
        val allSets = (catalogSets + cardSets).distinct().sorted()

        return allSets.mapNotNull { setLabel ->
            val setCards = cards.filter { it.expansion.equals(setLabel, ignoreCase = true) }
            if (setCards.isEmpty()) return@mapNotNull null

            val ownedCount = setCards.count { it.isOwned }

            // Intentar estimar el total de la colección desde el número de carta (ej. "4/102")
            var estimatedTotal = setCards.size
            setCards.forEach { card ->
                val parts = card.cardNumber.split("/")
                if (parts.size == 2) {
                    val maxNum = parts[1].trim().toIntOrNull()
                    if (maxNum != null && maxNum > estimatedTotal) {
                        estimatedTotal = maxNum
                    }
                }
            }

            val totalValue = setCards.filter { it.isOwned }
                .sumOf { it.priceEur ?: 0.0 }

            ExpansionStats(
                name = setLabel,
                ownedCount = ownedCount,
                totalCount = estimatedTotal,
                totalEurValue = totalValue
            )
        }
    }

    fun getCardsForExpansion(expansionName: String): List<PokemonCard> {
        return cards.filter { it.expansion.equals(expansionName, ignoreCase = true) }
    }

    // ---------------------------------------------------------------- Listas (carpetas)

    fun getFolders(): List<CardFolder> {
        return folders.map { folder ->
            val count = folderMembers[folder.id]?.count { id -> cards.any { it.id == id } } ?: 0
            folder.copy(cardCount = count)
        }
    }

    fun addFolder(title: String, description: String): CardFolder {
        val newFolder = CardFolder(
            id = "f_${System.currentTimeMillis()}",
            title = title,
            cardCount = 0,
            description = description
        )
        folders.add(newFolder)
        saveToDisk()
        return newFolder
    }

    fun deleteFolder(folderId: String): Boolean {
        val removed = folders.removeAll { it.id == folderId }
        folderMembers.remove(folderId)
        if (removed) saveToDisk()
        return removed
    }

    fun isCardInFolder(folderId: String, cardId: String): Boolean =
        folderMembers[folderId]?.contains(cardId) == true

    fun addCardToFolder(folderId: String, cardId: String): Boolean {
        if (folders.none { it.id == folderId } || cards.none { it.id == cardId }) return false
        val added = folderMembers.getOrPut(folderId) { linkedSetOf() }.add(cardId)
        if (added) saveToDisk()
        return added
    }

    fun removeCardFromFolder(folderId: String, cardId: String): Boolean {
        val removed = folderMembers[folderId]?.remove(cardId) == true
        if (removed) saveToDisk()
        return removed
    }

    fun getCardsInFolder(folderId: String): List<PokemonCard> {
        val ids = folderMembers[folderId] ?: return emptyList()
        return ids.mapNotNull { id -> cards.find { it.id == id } }
    }

    fun exportToJson(): String {
        val root = JSONObject()
        val cardsArray = JSONArray()
        cards.forEach { card ->
            cardsArray.put(JSONObject().apply {
                put("id", card.id)
                put("name", card.name)
                put("expansion", card.expansion)
                put("cardNumber", card.cardNumber)
                put("rarity", card.rarity)
                put("type", card.type)
                put("condition", card.condition)
                put("isOwned", card.isOwned)
                put("isWished", card.isWished)
                put("quantity", card.quantity)
                put("folderId", card.folderId ?: JSONObject.NULL)
                put("imageUri", card.imageUri ?: JSONObject.NULL)
                put("priceUsd", card.priceUsd ?: JSONObject.NULL)
                put("priceEur", card.priceEur ?: JSONObject.NULL)
                put("storeUrl", card.storeUrl ?: JSONObject.NULL)
            })
        }
        root.put("cards", cardsArray)

        val foldersArray = JSONArray()
        folders.forEach { folder ->
            foldersArray.put(JSONObject().apply {
                put("id", folder.id)
                put("title", folder.title)
                put("cardCount", folder.cardCount)
                put("description", folder.description)
            })
        }
        root.put("folders", foldersArray)

        val membersObj = JSONObject()
        folderMembers.forEach { (fId, set) ->
            val arr = JSONArray()
            set.forEach { arr.put(it) }
            membersObj.put(fId, arr)
        }
        root.put("folderMembers", membersObj)

        return root.toString(2)
    }

    fun importFromJson(jsonStr: String): Boolean {
        try {
            val root = JSONObject(jsonStr)
            val cardsArray = root.optJSONArray("cards")
            if (cardsArray != null) {
                for (i in 0 until cardsArray.length()) {
                    val obj = cardsArray.getJSONObject(i)
                    val card = PokemonCard(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        expansion = obj.getString("expansion"),
                        cardNumber = obj.optString("cardNumber", "N/A"),
                        rarity = obj.optString("rarity", "Común"),
                        type = obj.optString("type", "Incoloro"),
                        condition = obj.optString("condition", "Near Mint"),
                        isOwned = obj.optBoolean("isOwned", false),
                        isWished = obj.optBoolean("isWished", false),
                        quantity = obj.optInt("quantity", 1),
                        folderId = if (obj.isNull("folderId")) null else obj.optString("folderId"),
                        imageUri = if (obj.isNull("imageUri")) null else obj.optString("imageUri"),
                        priceUsd = if (obj.isNull("priceUsd")) null else obj.optDouble("priceUsd"),
                        priceEur = if (obj.isNull("priceEur")) null else obj.optDouble("priceEur"),
                        storeUrl = if (obj.isNull("storeUrl")) null else obj.optString("storeUrl")
                    )
                    upsertCard(card)
                }
            }

            val foldersArray = root.optJSONArray("folders")
            if (foldersArray != null) {
                for (i in 0 until foldersArray.length()) {
                    val obj = foldersArray.getJSONObject(i)
                    val fId = obj.getString("id")
                    val title = obj.getString("title")
                    val desc = obj.optString("description", "")
                    if (folders.none { it.id == fId }) {
                        folders.add(CardFolder(fId, title, 0, desc))
                    }
                }
            }

            val membersObj = root.optJSONObject("folderMembers")
            if (membersObj != null) {
                val keys = membersObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val arr = membersObj.getJSONArray(key)
                    val set = folderMembers.getOrPut(key) { mutableSetOf() }
                    for (i in 0 until arr.length()) {
                        set.add(arr.getString(i))
                    }
                }
            }

            saveToDisk()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap): String {
        val filename = "card_${System.currentTimeMillis()}.jpg"
        val file = File(context.filesDir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file.absolutePath
    }

    fun saveUriToInternalStorage(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val filename = "card_${System.currentTimeMillis()}.jpg"
            val file = File(context.filesDir, filename)
            FileOutputStream(file).use { output ->
                inputStream.copyTo(output)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
