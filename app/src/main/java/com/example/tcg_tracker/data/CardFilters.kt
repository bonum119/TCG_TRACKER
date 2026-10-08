package com.example.tcg_tracker.data

data class CollectionOption(
    val label: String,
    val apiSetName: String?,
    val aliases: List<String> = listOfNotNull(apiSetName),
    val isJapanese: Boolean = false
)

data class AttributeOption(
    val label: String,
    /** Cláusula para la API (opcional). Si falla, el filtro local sigue garantizando el resultado. */
    val serverClause: String?,
    val test: (name: String, rarity: String) -> Boolean
)

data class RarityOption(
    val label: String,
    val serverValues: List<String>,
    val test: (rarity: String) -> Boolean
)

object CardCatalog {

    const val ALL_F = "Todas"
    const val ALL_M = "Todos"

    private fun set(apiName: String, label: String = apiName, vararg extra: String) =
        CollectionOption(label, apiName, listOf(apiName) + extra)

    // ---------------------------------------------------------------- Colecciones
    val collections: List<CollectionOption> = listOf(
        CollectionOption(ALL_F, null, emptyList()),
        // Escarlata y Púrpura
        set("Prismatic Evolutions"), set("Surging Sparks"), set("Stellar Crown"),
        set("Shrouded Fable"), set("Twilight Masquerade"), set("Temporal Forces"),
        set("Paldean Fates"), set("Paradox Rift"), set("151"),
        set("Obsidian Flames"), set("Paldea Evolved"), set("Scarlet & Violet"),
        // Espada y Escudo
        set("Crown Zenith"), set("Silver Tempest"), set("Lost Origin"), set("Pokémon GO"),
        set("Astral Radiance"), set("Brilliant Stars"), set("Fusion Strike"),
        set("Celebrations"), set("Evolving Skies"), set("Chilling Reign"),
        set("Battle Styles"), set("Shining Fates"), set("Vivid Voltage"),
        set("Champion's Path"), set("Darkness Ablaze"), set("Rebel Clash"),
        set("Sword & Shield"),
        // Sol y Luna
        set("Cosmic Eclipse"), set("Hidden Fates"), set("Unified Minds"),
        set("Unbroken Bonds"), set("Detective Pikachu"), set("Team Up"),
        set("Lost Thunder"), set("Dragon Majesty"), set("Celestial Storm"),
        set("Forbidden Light"), set("Ultra Prism"), set("Crimson Invasion"),
        set("Shining Legends"), set("Burning Shadows"), set("Guardians Rising"),
        set("Sun & Moon"),
        // XY
        set("Evolutions"), set("Steam Siege"), set("Fates Collide"), set("Generations"),
        set("BREAKthrough"), set("BREAKpoint"), set("Ancient Origins"),
        set("Roaring Skies"), set("Primal Clash"), set("Phantom Forces"),
        set("Furious Fists"), set("Flashfire"), set("XY"),
        // Negro y Blanco
        set("Legendary Treasures"), set("Plasma Blast"), set("Plasma Freeze"),
        set("Plasma Storm"), set("Boundaries Crossed"), set("Dragons Exalted"),
        set("Dark Explorers"), set("Next Destinies"), set("Noble Victories"),
        set("Emerging Powers"), set("Black & White"),
        // HGSS / Platino / Diamante y Perla
        set("Call of Legends"), set("Triumphant"), set("Undaunted"), set("Unleashed"),
        set("HeartGold & SoulSilver"), set("Arceus"), set("Supreme Victors"),
        set("Rising Rivals"), set("Platinum"), set("Stormfront"),
        set("Legends Awakened"), set("Majestic Dawn"), set("Great Encounters"),
        set("Secret Wonders"), set("Mysterious Treasures"), set("Diamond & Pearl"),
        // Clásicas
        set("Neo Destiny"), set("Neo Revelation"), set("Neo Discovery"),
        set("Neo Genesis"), set("Gym Challenge"), set("Gym Heroes"),
        set("Team Rocket"), set("Base Set 2"), set("Fossil"), set("Jungle"),
        set("Base", "Base Set", "Base Set"),
        // Japonesas (la API pública no las incluye: se buscan en local)
        CollectionOption("Ediciones JP", null, emptyList(), isJapanese = true)
    )

    // ---------------------------------------------------------------- Atributos
    private val V_REGEX = Regex("\\bV\\b")
    private val EX_MODERN = Regex("\\bex\\b")
    private val EX_LEGACY = Regex("\\bEX\\b")
    private val MEGA_REGEX = Regex("^(M|Mega) ", RegexOption.IGNORE_CASE)

    private fun attr(label: String, server: String?, test: (String, String) -> Boolean) =
        AttributeOption(label, server, test)

    val attributes: List<AttributeOption> = listOf(
        attr(ALL_M, null) { _, _ -> true },
        attr("V", "subtypes:V") { n, _ -> V_REGEX.containsMatchIn(n) },
        attr("VMAX", "subtypes:VMAX") { n, _ -> n.contains("VMAX", true) },
        attr("VSTAR", "subtypes:VSTAR") { n, _ -> n.contains("VSTAR", true) },
        attr("ex (Escarlata y Púrpura)", "subtypes:ex") { n, _ -> EX_MODERN.containsMatchIn(n) },
        attr("GX", "subtypes:GX") { n, _ -> n.contains("GX") },
        attr("EX (clásicas)", "subtypes:EX") { n, _ -> EX_LEGACY.containsMatchIn(n) },
        attr("Mega", "subtypes:MEGA") { n, _ -> MEGA_REGEX.containsMatchIn(n) },
        attr("BREAK", "subtypes:BREAK") { n, _ -> n.contains("BREAK") },
        attr("Radiant", null) { n, _ -> n.startsWith("Radiant", true) },
        attr("Forma de Alola", "name:alolan*") { n, _ -> n.contains("Alola", true) },
        attr("Forma de Galar", "name:galarian*") { n, _ -> n.contains("Galarian", true) },
        attr("Forma de Hisui", "name:hisuian*") { n, _ -> n.contains("Hisuian", true) },
        attr("Forma de Paldea", "name:paldean*") { n, _ -> n.contains("Paldean", true) },
        attr("Shiny / Variocolor", null) { n, r -> r.contains("Shiny", true) || n.contains("Shiny", true) }
    )

    // ---------------------------------------------------------------- Rarezas
    private fun rar(label: String, server: List<String>, test: (String) -> Boolean) =
        RarityOption(label, server, test)

    val rarities: List<RarityOption> = listOf(
        rar(ALL_F, emptyList()) { true },
        rar("Common", listOf("Common")) { it.equals("Common", true) || it.equals("Común", true) },
        rar("Uncommon", listOf("Uncommon")) { it.equals("Uncommon", true) },
        rar("Rare", listOf("Rare")) { it.equals("Rare", true) },
        rar("Rare Holo", listOf("Rare Holo")) { it.contains("Holo", true) },
        rar("Double Rare", listOf("Double Rare")) { it.contains("Double Rare", true) },
        rar("Ultra Rare", listOf("Ultra Rare", "Rare Ultra")) { it.contains("Ultra", true) },
        rar("Illustration Rare", listOf("Illustration Rare")) { it.equals("Illustration Rare", true) },
        rar("Special Illustration Rare", listOf("Special Illustration Rare")) { it.contains("Special Illustration", true) },
        rar("Secret Rare", listOf("Secret Rare", "Rare Secret", "Hyper Rare", "Rare Rainbow")) {
            it.contains("Secret", true) || it.contains("Hyper", true) || it.contains("Rainbow", true)
        },
        rar("Shiny Rare", listOf("Shiny Rare", "Rare Shiny", "Shiny Ultra Rare")) { it.contains("Shiny", true) },
        rar("Promo", listOf("Promo")) { it.contains("Promo", true) }
    )
}

data class CardFilters(
    val collection: String = CardCatalog.ALL_F,
    val attribute: String = CardCatalog.ALL_M,
    val rarity: String = CardCatalog.ALL_F
) {
    private fun String.isAll() = isBlank() || startsWith("Todas", true) || startsWith("Todos", true)

    val collectionOption: CollectionOption?
        get() = if (collection.isAll()) null
        else CardCatalog.collections.firstOrNull { it.label.equals(collection, true) }

    val attributeOption: AttributeOption?
        get() = if (attribute.isAll()) null
        else CardCatalog.attributes.firstOrNull { it.label.equals(attribute, true) }

    val rarityOption: RarityOption?
        get() = if (rarity.isAll()) null
        else CardCatalog.rarities.firstOrNull { it.label.equals(rarity, true) }

    val isJapanese: Boolean get() = collectionOption?.isJapanese == true

    val activeCount: Int
        get() = listOf(collection, attribute, rarity).count { !it.isAll() }

    fun matches(name: String, expansion: String, rarity: String, type: String): Boolean {
        if (!collection.isAll()) {
            val opt = collectionOption
            val ok = when {
                opt == null -> expansion.contains(collection, true)
                opt.isJapanese -> expansion.contains("JP", true) || expansion.contains("Japan", true)
                opt.aliases.isEmpty() -> true
                else -> opt.aliases.any { it.equals(expansion, true) }
            }
            if (!ok) return false
        }
        if (!attribute.isAll()) {
            val opt = attributeOption
            val ok = if (opt == null) name.contains(attribute, true) else opt.test(name, rarity)
            if (!ok) return false
        }
        if (!this.rarity.isAll()) {
            val opt = rarityOption
            val ok = if (opt == null) rarity.contains(this.rarity, true) else opt.test(rarity)
            if (!ok) return false
        }
        return true
    }

    fun matches(card: PokemonCard): Boolean =
        matches(card.name, card.expansion, card.rarity, card.type)
}
