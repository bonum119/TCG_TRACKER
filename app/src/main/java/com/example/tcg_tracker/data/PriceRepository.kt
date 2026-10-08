package com.example.tcg_tracker.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class PriceResult(
    val priceUsd: Double?,
    val priceEur: Double?,
    val storeUrl: String? = null
)

data class OnlineCardResult(
    val id: String,
    val name: String,
    val expansion: String,
    val cardNumber: String,
    val rarity: String,
    val type: String,
    val imageUrl: String?,
    val priceUsd: Double?,
    val priceEur: Double?,
    val storeUrl: String?
)

object PriceRepository {

    /** Búsqueda continua sin tiempo límite corto. */
    const val SEARCH_TIMEOUT_MS = 300_000L
    private const val PRICE_TIMEOUT_MS = 15_000L
    private const val REQUEST_TIMEOUT_MS = 10_000L

    /** Opcional: con API key pokemontcg.io da muchos más límites de peticiones. */
    private const val API_KEY = ""

    private const val API_URL = "https://api.pokemontcg.io/v2/cards"
    private const val SELECT = "id,name,number,rarity,types,images,set,tcgplayer,cardmarket"

    // ------------------------------------------------------------------ Red

    private fun isNetworkAvailable(context: Context?): Boolean {
        if (context == null) return true
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            true
        }
    }

    /** Devuelve null si hubo un fallo (red, HTTP != 200, JSON inválido). Lista vacía = respuesta válida sin cartas. */
    private suspend fun fetchPage(q: String, pageSize: Int, timeoutMs: Int): List<OnlineCardResult>? =
        runInterruptible(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val params = StringBuilder("pageSize=$pageSize&select=$SELECT&orderBy=-set.releaseDate")
                if (q.isNotBlank()) params.append("&q=").append(URLEncoder.encode(q, "UTF-8"))

                connection = (URL("$API_URL?$params").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    setRequestProperty("User-Agent", "TCGTrackerAndroidApp/1.0")
                    setRequestProperty("Accept", "application/json")
                    if (API_KEY.isNotBlank()) setRequestProperty("X-Api-Key", API_KEY)
                }
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    null
                } else {
                    val json = connection.inputStream.bufferedReader().use { it.readText() }
                    val data = JSONObject(json).optJSONArray("data")
                    if (data == null) null
                    else List(data.length()) { parseCardResult(data.getJSONObject(it)) }
                }
            } catch (e: Exception) {
                null
            } finally {
                connection?.disconnect()
            }
        }

    /**
     * Repite [block] (con pausas crecientes) hasta que devuelva algo distinto de null
     * o hasta que se agote [timeoutMs]. [block] recibe el instante límite (elapsedRealtime).
     */
    private suspend fun <T : Any> retryUntil(
        timeoutMs: Long,
        block: suspend (deadline: Long) -> T?
    ): T? {
        val start = SystemClock.elapsedRealtime()
        val deadline = start + timeoutMs
        var attempt = 0
        while (SystemClock.elapsedRealtime() < deadline) {
            currentCoroutineContext().ensureActive()
            attempt++
            block(deadline)?.let { return it }
            val left = deadline - SystemClock.elapsedRealtime()
            if (left <= 0) break
            delay(minOf(500L * attempt, 3_000L, left))
        }
        return null
    }

    private fun requestTimeout(deadline: Long): Int =
        minOf(REQUEST_TIMEOUT_MS, deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1_000L).toInt()

    // ------------------------------------------------------------------ Búsqueda

    /**
     * Busca cartas sin rendirse: si la red falla, la API no responde o no hay coincidencias,
     * vuelve a intentarlo (relajando progresivamente la consulta) hasta [timeoutMs].
     * El resultado SIEMPRE se filtra en local con [filters], así los filtros nunca se "pierden".
     * Devuelve lista vacía solo si se agota el tiempo.
     */
    suspend fun searchCardsWithRetry(
        query: String,
        filters: CardFilters = CardFilters(),
        context: Context? = null,
        timeoutMs: Long = SEARCH_TIMEOUT_MS
    ): List<OnlineCardResult> {
        if (filters.isJapanese) return emptyList() // la API pública no tiene cartas japonesas
        val ladder = buildQueryLadder(query, filters)

        return retryUntil(timeoutMs) { deadline ->
            if (!isNetworkAvailable(context)) return@retryUntil null
            for (q in ladder) {
                if (deadline - SystemClock.elapsedRealtime() <= 0) return@retryUntil null
                val page = fetchPage(q, 100, requestTimeout(deadline))
                if (page != null) {
                    val matches = page
                        .map(::withStoreUrl)
                        .filter { filters.matches(it.name, it.expansion, it.rarity, it.type) }
                    if (matches.isNotEmpty()) return@retryUntil matches
                }
                delay(250) // respeta el límite de peticiones/minuto de la API
            }
            null
        } ?: emptyList()
    }

    /** Consultas de más estricta a más laxa. El filtro local descarta lo que no cumpla. */
    private fun buildQueryLadder(query: String, filters: CardFilters): List<String> {
        val name = nameClause(query).ifBlank { null }
        val set = filters.collectionOption?.apiSetName?.let { "set.name:\"$it\"" }
        val attr = filters.attributeOption?.serverClause
        val rarity = filters.rarityOption?.serverValues?.takeIf { it.isNotEmpty() }?.let { v ->
            if (v.size == 1) "rarity:\"${v[0]}\""
            else v.joinToString(" OR ", "(", ")") { "rarity:\"$it\"" }
        }

        val ladder = LinkedHashSet<String>()
        ladder += listOfNotNull(name, set, attr, rarity).joinToString(" ")
        ladder += listOfNotNull(name, set, attr).joinToString(" ")
        ladder += listOfNotNull(name, set).joinToString(" ")
        ladder += listOfNotNull(name).joinToString(" ")
        return ladder.toList() // una cadena vacía = "cartas más recientes"
    }

    /** "mega charizard" -> name:mega* name:charizard* */
    private fun nameClause(query: String): String =
        query.trim().split(Regex("\\s+"))
            .map { it.replace(Regex("[^\\p{L}\\p{N}'.\\-]"), "").trimStart('-', '.') }
            .filter { it.isNotBlank() }
            .joinToString(" ") { "name:$it*" }

    /** Solo completa el enlace de tienda. Los precios NUNCA se inventan: si no hay, quedan en null. */
    private fun withStoreUrl(res: OnlineCardResult): OnlineCardResult =
        res.copy(storeUrl = res.storeUrl?.takeIf { it.isNotBlank() } ?: tcgPlayerSearchUrl("${res.name} ${res.expansion}"))

    // ------------------------------------------------------------------ Escáner

    /**
     * Candidatos para comparar visualmente con la foto. Combina:
     *  - la carta exacta por número impreso (ej. 4/102) si el OCR lo leyó
     *  - cartas cuyo nombre empieza por cada texto candidato del OCR
     * También reintenta hasta [timeoutMs].
     */
    suspend fun fetchScanCandidates(
        nameQueries: List<String>,
        number: String?,
        printedTotal: String?,
        context: Context? = null,
        timeoutMs: Long = SEARCH_TIMEOUT_MS
    ): List<OnlineCardResult> {
        val queries = buildList {
            if (number != null && !printedTotal.isNullOrBlank()) add("number:$number set.printedTotal:$printedTotal")
            nameQueries.take(4).map { nameClause(it) }.filter { it.isNotBlank() }.forEach { add(it) }
        }
        if (queries.isEmpty()) return emptyList()

        return retryUntil(timeoutMs) { deadline ->
            if (!isNetworkAvailable(context)) return@retryUntil null
            val pages = coroutineScope {
                queries.map { q -> async { fetchPage(q, 60, requestTimeout(deadline)) } }.awaitAll()
            }
            pages.filterNotNull().flatten().distinctBy { it.id }.map(::withStoreUrl).takeIf { it.isNotEmpty() }
        } ?: emptyList()
    }

    // ------------------------------------------------------------------ Precios

    /**
     * Precio real de una carta. Si se conoce el id de la API (p. ej. "base1-4") se pide esa carta exacta;
     * si no, se busca por nombre y se elige la que coincida en colección y número.
     * Si la web no publica precio, devuelve null en ese campo (nunca un valor inventado).
     */
    suspend fun fetchCardPrices(
        cardName: String,
        expansion: String? = null,
        context: Context? = null,
        cardId: String? = null,
        cardNumber: String? = null
    ): PriceResult {
        val noPrice = PriceResult(null, null, tcgPlayerSearchUrl(cardName))
        if (cardName.isBlank()) return noPrice

        val idQuery = cardId?.takeIf { Regex("^[a-z0-9]+-[A-Za-z0-9]+$").matches(it) }?.let { "id:$it" }
        val nameQuery = nameClause(cardName)

        val result = retryUntil(PRICE_TIMEOUT_MS) { deadline ->
            if (!isNetworkAvailable(context)) return@retryUntil null

            var page: List<OnlineCardResult>? = null
            if (idQuery != null) {
                page = fetchPage(idQuery, 1, requestTimeout(deadline))?.takeIf { it.isNotEmpty() }
            }
            if (page == null) {
                page = fetchPage(nameQuery, 100, requestTimeout(deadline)) ?: return@retryUntil null
            }
            if (page.isEmpty()) return@retryUntil noPrice

            val exact = page.firstOrNull {
                it.name.equals(cardName, true) && expansionMatches(it.expansion, expansion) && numberMatches(it.cardNumber, cardNumber)
            } ?: page.firstOrNull {
                it.name.equals(cardName, true) && expansionMatches(it.expansion, expansion)
            } ?: page.firstOrNull { expansionMatches(it.expansion, expansion) && numberMatches(it.cardNumber, cardNumber) }

            if (exact == null) noPrice
            else PriceResult(exact.priceUsd, exact.priceEur, exact.storeUrl)
        }
        return result ?: noPrice
    }

    private fun expansionMatches(apiExpansion: String, wanted: String?): Boolean {
        if (wanted.isNullOrBlank() || wanted.equals("Desconocida", true)) return true
        if (apiExpansion.equals(wanted, true)) return true
        val option = CardCatalog.collections.firstOrNull { o ->
            o.label.equals(wanted, true) || o.aliases.any { it.equals(wanted, true) }
        }
        if (option != null && option.aliases.isNotEmpty()) return option.aliases.any { it.equals(apiExpansion, true) }
        return apiExpansion.contains(wanted, true) || wanted.contains(apiExpansion, true)
    }

    private fun numberMatches(apiNumber: String, wanted: String?): Boolean {
        if (wanted.isNullOrBlank() || wanted.equals("N/A", true)) return true
        fun norm(n: String) = n.substringBefore('/').trim().trimStart('0')
        return norm(apiNumber).equals(norm(wanted), true)
    }

    // ------------------------------------------------------------------ Parsing

    private fun parseCardResult(card: JSONObject): OnlineCardResult {
        val id = card.optString("id", "")
        val name = card.optString("name", "")
        val setObj = card.optJSONObject("set")
        val expansion = setObj?.optString("name")?.takeIf { it.isNotBlank() } ?: "Desconocida"
        val cardNumber = card.optString("number").takeIf { it.isNotBlank() } ?: "N/A"
        val rarity = card.optString("rarity").takeIf { it.isNotBlank() } ?: "Común"

        val typesArray = card.optJSONArray("types")
        val type = if (typesArray != null && typesArray.length() > 0) typesArray.optString(0) else "Colorless"

        val imageUrl = card.optJSONObject("images")?.optString("small")?.takeIf { it.isNotBlank() }

        var priceUsd: Double? = null
        var priceEur: Double? = null
        var storeUrl: String? = null

        // TCGPlayer -> USD (precio de mercado de la variante principal; si falta, medio o mínimo)
        val tcgPlayer = card.optJSONObject("tcgplayer")
        if (tcgPlayer != null) {
            if (tcgPlayer.has("url") && !tcgPlayer.isNull("url")) storeUrl = tcgPlayer.optString("url")
            val prices = tcgPlayer.optJSONObject("prices")
            if (prices != null) {
                val variants = listOf(
                    "holofoil", "normal", "reverseHolofoil", "1stEditionHolofoil",
                    "unlimitedHolofoil", "1stEditionNormal", "unlimitedNormal"
                )
                variantLoop@ for (variant in variants) {
                    val obj = prices.optJSONObject(variant) ?: continue
                    for (field in listOf("market", "mid", "low")) {
                        val v = obj.optPositive(field)
                        if (v != null) {
                            priceUsd = v
                            break@variantLoop
                        }
                    }
                }
            }
        }

        // Cardmarket -> EUR (precio de tendencia; si falta, venta media o mínimo)
        val cardmarket = card.optJSONObject("cardmarket")
        if (cardmarket != null) {
            if (storeUrl == null && cardmarket.has("url") && !cardmarket.isNull("url")) {
                storeUrl = cardmarket.optString("url")
            }
            val prices = cardmarket.optJSONObject("prices")
            if (prices != null) {
                for (key in listOf("trendPrice", "averageSellPrice", "avg30", "lowPrice")) {
                    val v = prices.optPositive(key)
                    if (v != null) {
                        priceEur = v
                        break
                    }
                }
            }
        }

        return OnlineCardResult(
            id = id,
            name = name,
            expansion = expansion,
            cardNumber = cardNumber,
            rarity = rarity,
            type = type,
            imageUrl = imageUrl,
            priceUsd = priceUsd,
            priceEur = priceEur,
            storeUrl = storeUrl?.takeIf { it.isNotBlank() } ?: tcgPlayerSearchUrl("$name $expansion")
        )
    }

    private fun tcgPlayerSearchUrl(text: String) =
        "https://www.tcgplayer.com/search/pokemon/product?q=${URLEncoder.encode(text, "UTF-8")}"

    private fun JSONObject.optPositive(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { it > 0.0 } else null
}
