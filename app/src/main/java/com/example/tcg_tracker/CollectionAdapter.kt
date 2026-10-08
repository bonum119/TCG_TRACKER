package com.example.tcg_tracker

import android.view.LayoutInflater
import android.view.View
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

    var isSelectionMode: Boolean = false
        set(value) {
            field = value
            if (!value) selectedIds.clear()
            notifyDataSetChanged()
        }

    val selectedIds = mutableSetOf<String>()
    var onSelectionChanged: (Int) -> Unit = {}

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

            if (isSelectionMode) {
                cbSelected.visibility = View.VISIBLE
                cbSelected.isChecked = selectedIds.contains(card.id)
                cbSelected.setOnClickListener {
                    if (selectedIds.contains(card.id)) selectedIds.remove(card.id)
                    else selectedIds.add(card.id)
                    onSelectionChanged(selectedIds.size)
                }
            } else {
                cbSelected.visibility = View.GONE
            }

            root.setOnClickListener {
                if (isSelectionMode) {
                    if (selectedIds.contains(card.id)) selectedIds.remove(card.id)
                    else selectedIds.add(card.id)
                    notifyItemChanged(position)
                    onSelectionChanged(selectedIds.size)
                } else {
                    onCardClick(card)
                }
            }
            root.setOnLongClickListener {
                if (!isSelectionMode) {
                    isSelectionMode = true
                    selectedIds.add(card.id)
                    notifyDataSetChanged()
                    onSelectionChanged(selectedIds.size)
                    true
                } else {
                    onCardLongClick(card)
                    true
                }
            }
        }
    }

    override fun getItemCount(): Int = cards.size

    fun updateCards(newCards: List<PokemonCard>) {
        cards = newCards
        notifyDataSetChanged()
    }
}
