package com.example.tcg_tracker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.tcg_tracker.data.ExpansionStats
import com.example.tcg_tracker.databinding.ItemExpansionBinding
import com.example.tcg_tracker.util.PriceFormat

class ExpansionsAdapter(
    private var expansions: List<ExpansionStats>,
    private val onExpansionClick: (ExpansionStats) -> Unit
) : RecyclerView.Adapter<ExpansionsAdapter.ExpansionViewHolder>() {

    class ExpansionViewHolder(val binding: ItemExpansionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpansionViewHolder {
        val binding = ItemExpansionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ExpansionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExpansionViewHolder, position: Int) {
        val stats = expansions[position]
        with(holder.binding) {
            tvExpansionName.text = stats.name
            val percent = stats.completionPercentage
            tvExpansionPercentage.text = "$percent%"
            pbExpansionProgress.progress = percent
            tvExpansionProgress.text = root.context.getString(
                R.string.progress_format,
                stats.ownedCount,
                stats.totalCount,
                percent
            )
            tvExpansionValue.text = root.context.getString(
                R.string.value_format,
                PriceFormat.eur(stats.totalEurValue)
            )

            root.setOnClickListener { onExpansionClick(stats) }
        }
    }

    override fun getItemCount(): Int = expansions.size

    fun updateExpansions(newExpansions: List<ExpansionStats>) {
        expansions = newExpansions
        notifyDataSetChanged()
    }
}
