package com.example.tcg_tracker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.tcg_tracker.data.PokemonCard
import com.example.tcg_tracker.databinding.ItemCardWishlistBinding
import com.example.tcg_tracker.util.PriceFormat
import com.example.tcg_tracker.util.loadCardImage

class WishlistAdapter(
    private var cards: List<PokemonCard>,
    private val onCardClick: (PokemonCard) -> Unit,
    private val onMarkOwned: (PokemonCard) -> Unit,
    private val onRemoveWish: (PokemonCard) -> Unit
) : RecyclerView.Adapter<WishlistAdapter.WishlistViewHolder>() {

    inner class WishlistViewHolder(val binding: ItemCardWishlistBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WishlistViewHolder {
        val binding = ItemCardWishlistBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WishlistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WishlistViewHolder, position: Int) {
        val card = cards[position]
        with(holder.binding) {
            ivCardThumbnail.loadCardImage(card.imageUri)
            tvCardName.text = card.name
            tvExpansion.text = root.context.getString(
                R.string.card_expansion_format,
                card.expansion,
                card.cardNumber
            )
            val eurStr = PriceFormat.eur(card.priceEur)
            val usdStr = PriceFormat.usd(card.priceUsd)
            tvPrice.text = if (card.priceEur != null) eurStr else usdStr

            root.setOnClickListener { onCardClick(card) }
            btnMarkOwned.setOnClickListener { onMarkOwned(card) }
            btnRemoveWish.setOnClickListener { onRemoveWish(card) }
        }
    }

    override fun getItemCount(): Int = cards.size

    fun updateCards(newCards: List<PokemonCard>) {
        cards = newCards
        notifyDataSetChanged()
    }
}
