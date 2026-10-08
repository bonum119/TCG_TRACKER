package com.example.tcg_tracker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.tcg_tracker.data.CardFolder
import com.example.tcg_tracker.databinding.ItemFolderBinding

class ListsAdapter(
    private var folders: List<CardFolder>,
    private val onFolderClick: (CardFolder) -> Unit,
    private val onDeleteFolder: (CardFolder) -> Unit
) : RecyclerView.Adapter<ListsAdapter.FolderViewHolder>() {

    inner class FolderViewHolder(val binding: ItemFolderBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemFolderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = folders[position]
        with(holder.binding) {
            tvFolderTitle.text = folder.title
            tvFolderDescription.text = folder.description
            tvCardCount.text = root.context.getString(R.string.card_count_format, folder.cardCount)
            root.setOnClickListener { onFolderClick(folder) }
            btnDeleteFolder.setOnClickListener { onDeleteFolder(folder) }
        }
    }

    override fun getItemCount(): Int = folders.size

    fun updateFolders(newFolders: List<CardFolder>) {
        folders = newFolders
        notifyDataSetChanged()
    }
}
