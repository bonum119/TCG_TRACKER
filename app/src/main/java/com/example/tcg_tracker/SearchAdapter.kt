package com.example.tcg_tracker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.tcg_tracker.data.PokemonCard
import com.example.tcg_tracker.databinding.ItemCardSearchBinding
import com.example.tcg_tracker.util.PriceFormat
import com.example.tcg_tracker.util.loadCardImage

class SearchAdapter(
    private var cards: List<PokemonCard>,
    private val onCardClick: (PokemonCard) -> Unit,
    private val onAddToggle: (PokemonCard) -> Unit,
    private val onCardLongClick: (PokemonCard) -> Unit = {}
) : RecyclerView.Adapter<SearchAdapter.SearchViewHolder>() {

    inner class SearchViewHolder(val binding: ItemCardSearchBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val binding = ItemCardSearchBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SearchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        val card = cards[position]
        with(holder.binding) {
            ivCardThumbnail.loadCardImage(card.imageUri)
            tvCardName.text = card.name
            tvExpansion.text = root.context.getString(
                R.string.card_expansion_format,
                card.expansion,
                card.cardNumber
            )
            tvRarityBadge.text = card.rarity

            val usdStr = PriceFormat.usd(card.priceUsd, "$--")
            val eurStr = PriceFormat.eur(card.priceEur, "-- €")
            tvPrices.text = root.context.getString(R.string.search_prices_format, usdStr, eurStr)

            val isTrending = (card.priceEur != null && card.priceEur!! > 35.0) ||
                    (card.priceUsd != null && card.priceUsd!! > 45.0) ||
                    card.rarity.contains("Secret", true) ||
                    card.rarity.contains("Ultra", true)

            if (isTrending) {
                tvTrendingBadge.visibility = android.view.View.VISIBLE
                tvTrendingBadge.text = root.context.getString(R.string.trending_badge)
            } else {
                tvTrendingBadge.visibility = android.view.View.GONE
            }

            val iconRes = if (card.isOwned) R.drawable.ic_check else R.drawable.ic_add
            btnAddToggle.setIconResource(iconRes)

            btnAddToggle.setIconTintResource(
                if (card.isOwned) R.color.gray_primary else R.color.text_secondary
            )

            root.setOnClickListener { onCardClick(card) }
            root.setOnLongClickListener {
                onCardLongClick(card)
                true
            }
            btnAddToggle.setOnClickListener { onAddToggle(card) }
        }
    }

    override fun getItemCount(): Int = cards.size

    fun updateCards(newCards: List<PokemonCard>) {
        cards = newCards
        notifyDataSetChanged()
    }
}
