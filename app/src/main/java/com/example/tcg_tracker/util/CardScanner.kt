package com.example.tcg_tracker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import com.example.tcg_tracker.data.MockRepository
import com.example.tcg_tracker.data.OnlineCardResult
import com.example.tcg_tracker.data.PokemonCard
import com.example.tcg_tracker.data.PriceRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.min

data class ScanHints(
    val nameQueries: List<String>,
    val number: String?,
    val printedTotal: String?,
    val rawText: String
)

data class ScanMatch(val card: OnlineCardResult, val score: Float)

/**
 * Identifica una carta a partir de una foto:
 *  1. OCR (ML Kit) para sacar nombre y número impreso.
 *  2. Descarga candidatos de la API.
 *  3. Compara la foto con la imagen oficial de cada candidato (color, estructura y hash perceptual)
 *     y combina esa similitud visual con la coincidencia de texto.
 */
object CardScanner {

    private const val MAX_IMAGES = 60
    private const val IMAGE_PHASE_TIMEOUT_MS = 20_000L
    private const val CARD_ASPECT = 63f / 88f

    private val POPULAR = listOf(
        "Charizard", "Pikachu", "Mewtwo", "Blastoise", "Venusaur", "Snorlax",
        "Rayquaza", "Lucario", "Gengar", "Eevee", "Lugia", "Mew", "Greninja",
        "Umbreon", "Sylveon", "Gardevoir", "Dragonite", "Arceus", "Giratina",
        "Bulbasaur", "Charmander", "Squirtle", "Psyduck", "Gyarados", "Scyther",
        "Zapdos", "Articuno", "Moltres", "Celebi", "Kyogre", "Groudon", "Infernape"
    )

    private val STOP_WORDS = setOf(
        "basic", "stage", "pokemon", "pokémon", "trainer", "energy", "evolves", "from", "put", "onto",
        "bench", "weakness", "resistance", "retreat", "illus", "ability", "attack", "damage", "the", "and"
    )

    // ------------------------------------------------------------------ OCR

    suspend fun recognizeText(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { if (cont.isActive) cont.resume(it.text) }
            .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
            .addOnCompleteListener { recognizer.close() }
    }

    fun parseHints(text: String): ScanHints {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        val numberMatch = Regex("(\\d{1,3})\\s*/\\s*(\\d{2,3})").find(text)
        val number = numberMatch?.groupValues?.get(1)?.trimStart('0')?.ifEmpty { "0" }
        val total = numberMatch?.groupValues?.get(2)?.trimStart('0')?.takeIf { it.isNotEmpty() }

        val candidates = LinkedHashSet<String>()
        POPULAR.filter { text.contains(it, ignoreCase = true) }.forEach { candidates += it }

        val hpRegex = Regex("\\bHP\\s*\\d+|\\d+\\s*HP", RegexOption.IGNORE_CASE)
        for (line in lines.take(10)) {
            if (line.contains("evolves", ignoreCase = true)) continue
            val cleaned = line.replace(hpRegex, " ")
                .replace(Regex("[^\\p{L}'\\- ]"), " ")
                .replace(Regex("\\s+"), " ").trim()
            val words = cleaned.split(" ").filter { it.length >= 3 && it.lowercase() !in STOP_WORDS }
            if (words.isEmpty()) continue
            if (words.size <= 3) candidates += words.joinToString(" ")
            words.filter { it.length >= 4 }.forEach { candidates += it }
            if (candidates.size >= 8) break
        }

        return ScanHints(candidates.take(4), number, total, text)
    }

    // ------------------------------------------------------------------ Búsqueda por parecido

    suspend fun findMatches(photo: Bitmap, hints: ScanHints, context: Context): List<ScanMatch> {
        val online = if (hints.nameQueries.isNotEmpty() || hints.number != null) {
            PriceRepository.fetchScanCandidates(hints.nameQueries, hints.number, hints.printedTotal, context)
        } else emptyList()

        // Sin texto legible (o sin red): se compara con las cartas ya conocidas en la app
        val candidates = online.ifEmpty { MockRepository.getAllCards().map { it.toOnline() } }
        if (candidates.isEmpty()) return emptyList()

        val hasText = hints.nameQueries.isNotEmpty() || hints.number != null
        val pre = candidates.sortedByDescending { textScore(it, hints) }.take(MAX_IMAGES)

        val photoSignatures = withContext(Dispatchers.Default) { photoVariants(photo).map { signature(it) } }

        val scored = java.util.Collections.synchronizedList(mutableListOf<ScanMatch>())
        withTimeoutOrNull(IMAGE_PHASE_TIMEOUT_MS) {
            coroutineScope {
                val semaphore = Semaphore(8)
                pre.map { card ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val bmp = card.imageUrl?.let { downloadBitmap(it) }
                            val text = textScore(card, hints)
                            val score = if (bmp != null) {
                                val sig = signature(bmp)
                                val visual = photoSignatures.maxOf { similarity(it, sig) }
                                if (hasText) 0.55f * visual + 0.45f * text else visual
                            } else {
                                0.45f * text // sin imagen no se puede comparar
                            }
                            scored += ScanMatch(card, score)
                        }
                    }
                }.awaitAll()
            }
        }

        val result = synchronized(scored) { scored.toMutableList() }
        val done = result.map { it.card.id }.toSet()
        pre.filter { it.id !in done }.forEach { result += ScanMatch(it, 0.45f * textScore(it, hints)) }

        return result.sortedByDescending { it.score }.take(15)
    }

    private fun textScore(card: OnlineCardResult, hints: ScanHints): Float {
        var s = 0f
        val baseName = card.name.lowercase()
            .replace(Regex("\\b(vmax|vstar|v|ex|gx)\\b"), "").trim()
        if (baseName.isNotBlank() && hints.rawText.contains(baseName, ignoreCase = true)) s += 0.6f
        else if (hints.nameQueries.any { card.name.contains(it, ignoreCase = true) }) s += 0.35f

        val cardNumber = card.cardNumber.substringBefore('/').trimStart('0')
        if (hints.number != null && cardNumber == hints.number) s += 0.4f
        return s.coerceAtMost(1f)
    }

    private fun PokemonCard.toOnline() = OnlineCardResult(
        id, name, expansion, cardNumber, rarity, type,
        imageUri?.takeIf { it.startsWith("http", ignoreCase = true) },
        priceUsd, priceEur, storeUrl
    )

    // ------------------------------------------------------------------ Similitud visual

    private class Signature(val layout: FloatArray, val dhash: Long, val hue: FloatArray)

    /** Recortes de la foto con proporción de carta (la carta suele estar centrada). */
    private fun photoVariants(src: Bitmap): List<Bitmap> {
        val list = mutableListOf(cropToCard(src, 1f), cropToCard(src, 0.8f))
        if (src.width > src.height) { // carta horizontal en foto apaisada
            val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(90f) }, true)
            list += cropToCard(rotated, 1f)
            list += cropToCard(rotated, 0.8f)
        }
        return list
    }

    private fun cropToCard(src: Bitmap, scale: Float): Bitmap {
        var w = src.width.toFloat()
        var h = src.height.toFloat()
        if (w / h > CARD_ASPECT) w = h * CARD_ASPECT else h = w / CARD_ASPECT
        w *= scale
        h *= scale
        val x = ((src.width - w) / 2f).toInt().coerceAtLeast(0)
        val y = ((src.height - h) / 2f).toInt().coerceAtLeast(0)
        return Bitmap.createBitmap(
            src, x, y,
            w.toInt().coerceIn(1, src.width - x),
            h.toInt().coerceIn(1, src.height - y)
        )
    }

    private fun signature(src: Bitmap): Signature {
        val base = Bitmap.createScaledBitmap(src, 64, 88, true)

        // 1) Disposición de color 16x22 (centrada por canal para tolerar cambios de luz)
        val small = Bitmap.createScaledBitmap(base, 16, 22, true)
        val px = IntArray(16 * 22)
        small.getPixels(px, 0, 16, 0, 0, 16, 22)
        val layout = FloatArray(px.size * 3)
        val mean = FloatArray(3)
        px.forEachIndexed { i, c ->
            layout[i * 3] = Color.red(c).toFloat()
            layout[i * 3 + 1] = Color.green(c).toFloat()
            layout[i * 3 + 2] = Color.blue(c).toFloat()
            mean[0] += layout[i * 3]; mean[1] += layout[i * 3 + 1]; mean[2] += layout[i * 3 + 2]
        }
        for (ch in 0..2) mean[ch] /= px.size
        for (i in px.indices) for (ch in 0..2) layout[i * 3 + ch] -= mean[ch]

        // 2) Histograma de tono (12 tonos + 1 neutro)
        val hue = FloatArray(13)
        val hsv = FloatArray(3)
        px.forEach { c ->
            Color.colorToHSV(c, hsv)
            val bin = if (hsv[1] < 0.2f) 12 else (hsv[0] / 360f * 12).toInt().coerceAtMost(11)
            hue[bin] += 1f
        }
        for (i in hue.indices) hue[i] /= px.size

        // 3) dHash 9x8
        val g = Bitmap.createScaledBitmap(base, 9, 8, true)
        var hash = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (gray(g.getPixel(x, y)) > gray(g.getPixel(x + 1, y))) hash = hash or (1L shl (y * 8 + x))
            }
        }
        return Signature(layout, hash, hue)
    }

    private fun gray(c: Int) = 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)

    private fun similarity(a: Signature, b: Signature): Float {
        var diff = 0f
        for (i in a.layout.indices) diff += abs(a.layout[i] - b.layout[i])
        diff /= a.layout.size * 255f
        val s1 = (1f - diff / 0.30f).coerceIn(0f, 1f)

        val hamming = java.lang.Long.bitCount(a.dhash xor b.dhash)
        val s2 = (1f - hamming / 32f).coerceIn(0f, 1f)

        var s3 = 0f
        for (i in a.hue.indices) s3 += min(a.hue[i], b.hue[i])

        return 0.40f * s1 + 0.35f * s2 + 0.25f * s3
    }
}
