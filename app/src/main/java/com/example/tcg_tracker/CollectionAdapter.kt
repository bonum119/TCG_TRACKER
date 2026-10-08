package com.example.tcg_tracker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.tcg_tracker.data.PokemonCard
import com.example.tcg_tracker.databinding.ItemCardGridBinding
import com.example.tcg_tracker.util.loadCardImage

class CollectionAdapter(
    private var cards: List<PokemonCard>,
    private val onCardClick: (PokemonCard) -> Unit,
    private val onCardLongClick: (PokemonCard) -> Unit = {}
) : RecyclerView.Adapter<CollectionAdapter.CardViewHolder>() {

    inner class CardViewHolder(val binding: ItemCardGridBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val binding = ItemCardGridBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val card = cards[position]
        with(holder.binding) {
            ivCardImage.loadCardImage(card.imageUri)
            tvCardName.text = card.name
            tvExpansion.text = root.context.getString(
                R.string.card_expansion_format,
                card.expansion,
                card.cardNumber
            )
            tvCondition.text = card.condition
            root.setOnClickListener { onCardClick(card) }
            root.setOnLongClickListener {
                onCardLongClick(card)
                true
            }
        }
    }

    override fun getItemCount(): Int = cards.size

    fun updateCards(newCards: List<PokemonCard>) {
        cards = newCards
        notifyDataSetChanged()
    }
}