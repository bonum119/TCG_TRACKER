package com.example.tcg_tracker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.tcg_tracker.data.PokemonCard
import com.example.tcg_tracker.databinding.ItemCardExpansionBinding
import com.example.tcg_tracker.util.PriceFormat
import com.example.tcg_tracker.util.loadCardImage

class ExpansionCardsAdapter(
    private var cards: List<PokemonCard>,
    private val onCardClick: (PokemonCard) -> Unit,
    private val onToggleOwned: (PokemonCard) -> Unit,
    private val onToggleWish: (PokemonCard) -> Unit
) : RecyclerView.Adapter<ExpansionCardsAdapter.CardViewHolder>() {

    class CardViewHolder(val binding: ItemCardExpansionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val binding = ItemCardExpansionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val card = cards[position]
        val context = holder.binding.root.context
        with(holder.binding) {
            ivCardThumbnail.loadCardImage(card.imageUri)
            tvCardName.text = card.name
            tvCardNumber.text = "#${card.cardNumber} · ${card.rarity}"

            val priceStr = PriceFormat.eur(card.priceEur) ?: PriceFormat.usd(card.priceUsd)
            tvPrice.text = priceStr ?: "-- €"

            if (card.isOwned) {
                ivCardThumbnail.alpha = 1.0f
                tvOwnedStatus.text = context.getString(R.string.status_owned)
                tvOwnedStatus.setTextColor(ContextCompat.getColor(context, R.color.green_accent))
                btnToggleOwned.text = context.getString(R.string.status_owned)
                btnToggleOwned.setIconResource(R.drawable.ic_check)
            } else {
                ivCardThumbnail.alpha = 0.5f
                tvOwnedStatus.text = context.getString(R.string.status_missing)
                tvOwnedStatus.setTextColor(ContextCompat.getColor(context, R.color.red_accent))
                btnToggleOwned.text = context.getString(R.string.btn_mark_owned)
                btnToggleOwned.setIconResource(R.drawable.ic_add)
            }

            if (card.isWished) {
                btnToggleWish.setIconResource(R.drawable.ic_heart_filled)
            } else {
                btnToggleWish.setIconResource(R.drawable.ic_heart_outline)
            }

            root.setOnClickListener { onCardClick(card) }
            btnToggleOwned.setOnClickListener { onToggleOwned(card) }
            btnToggleWish.setOnClickListener { onToggleWish(card) }
        }
    }

    override fun getItemCount(): Int = cards.size

    fun updateCards(newCards: List<PokemonCard>) {
        cards = newCards
        notifyDataSetChanged()
    }
}
