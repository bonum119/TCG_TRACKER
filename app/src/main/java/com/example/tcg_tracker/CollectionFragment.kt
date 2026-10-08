package com.example.tcg_tracker

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.tcg_tracker.data.MockRepository
import com.example.tcg_tracker.databinding.FragmentCollectionBinding
import com.example.tcg_tracker.util.FolderDialogs
import com.google.android.material.slider.RangeSlider

class CollectionFragment : Fragment() {

    private var _binding: FragmentCollectionBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: CollectionAdapter
    private var selectedCondition: String? = null
    private var minPrice: Double = 0.0
    private var maxPrice: Double = 500.0

    enum class SortMode(val labelRes: Int) {
        RECENTLY_ADDED(R.string.sort_recent),
        OLDEST_ADDED(R.string.sort_oldest),
        HIGHEST_PRICE(R.string.sort_highest_price),
        LOWEST_PRICE(R.string.sort_lowest_price),
        OLDEST_SET(R.string.sort_oldest_set),
        NEWEST_SET(R.string.sort_newest_set)
    }

    private var currentSortMode: SortMode = SortMode.RECENTLY_ADDED

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCollectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = CollectionAdapter(
            cards = emptyList(),
            onCardClick = { card ->
                val bundle = bundleOf("cardId" to card.id)
                findNavController().navigate(R.id.navigation_detail, bundle)
            },
            onCardLongClick = { card -> FolderDialogs.showAddToList(requireContext(), card) }
        )

        adapter.onSelectionChanged = { count ->
            if (count > 0) {
                binding.layoutBatchActions.visibility = View.VISIBLE
                binding.tvSelectedCount.text = getString(R.string.selected_count, count)
            } else {
                binding.layoutBatchActions.visibility = View.GONE
            }
        }

        binding.btnCancelBatch.setOnClickListener {
            adapter.isSelectionMode = false
            binding.layoutBatchActions.visibility = View.GONE
        }

        binding.btnAddToAlbumBatch.setOnClickListener {
            val folders = MockRepository.getFolders()
            if (folders.isEmpty()) {
                Toast.makeText(requireContext(), "Crea un álbum primero en la pestaña Álbumes", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val names = folders.map { it.title }.toTypedArray()
            AlertDialog.Builder(requireContext(), com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle(getString(R.string.add_to_album))
                .setItems(names) { _, which ->
                    val targetFolder = folders[which]
                    var addedCount = 0
                    adapter.selectedIds.forEach { cardId ->
                        if (MockRepository.addCardToFolder(targetFolder.id, cardId)) {
                            addedCount++
                        }
                    }
                    Toast.makeText(requireContext(), "$addedCount carta(s) añadidas a \"${targetFolder.title}\"", Toast.LENGTH_SHORT).show()
                    adapter.isSelectionMode = false
                    binding.layoutBatchActions.visibility = View.GONE
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }

        binding.rvCollection.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = this@CollectionFragment.adapter
        }

        binding.btnAddCardTop.setOnClickListener {
            findNavController().navigate(R.id.navigation_add_card)
        }

        binding.btnSort.text = getString(R.string.sort_by, getString(currentSortMode.labelRes))
        binding.btnSort.setOnClickListener {
            val modes = SortMode.entries
            val labels = modes.map { getString(it.labelRes) }.toTypedArray()
            AlertDialog.Builder(requireContext(), com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle(getString(R.string.filter_dialog_title))
                .setItems(labels) { _, which ->
                    currentSortMode = modes[which]
                    binding.btnSort.text = getString(R.string.sort_by, getString(currentSortMode.labelRes))
                    loadData()
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }

        binding.etCollectionSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                loadData()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.chipDuplicates.setOnCheckedChangeListener { _, _ ->
            loadData()
        }

        binding.chipCondition.setOnClickListener {
            if (selectedCondition != null) {
                selectedCondition = null
                binding.chipCondition.isChecked = false
                binding.chipCondition.text = getString(R.string.filter_condition)
                loadData()
            } else {
                binding.chipCondition.isChecked = false
                val conditions = arrayOf(getString(R.string.all_conditions), "Mint", "Near Mint", "Played", "Damaged")
                AlertDialog.Builder(requireContext(), com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                    .setTitle(getString(R.string.filter_condition))
                    .setItems(conditions) { _, which ->
                        if (which == 0) {
                            selectedCondition = null
                            binding.chipCondition.isChecked = false
                            binding.chipCondition.text = getString(R.string.filter_condition)
                        } else {
                            selectedCondition = conditions[which]
                            binding.chipCondition.isChecked = true
                            binding.chipCondition.text = "${getString(R.string.filter_condition)}: $selectedCondition"
                        }
                        loadData()
                    }
                    .setNegativeButton(getString(R.string.btn_cancel)) { _, _ ->
                        binding.chipCondition.isChecked = selectedCondition != null
                    }
                    .setOnCancelListener {
                        binding.chipCondition.isChecked = selectedCondition != null
                    }
                    .show()
            }
        }

        binding.chipPrice.setOnClickListener {
            if (minPrice > 0.0 || maxPrice < 500.0) {
                minPrice = 0.0
                maxPrice = 500.0
                binding.chipPrice.isChecked = false
                binding.chipPrice.text = getString(R.string.filter_price)
                loadData()
            } else {
                binding.chipPrice.isChecked = false
                val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_price_range, null)
                val slider = dialogView.findViewById<RangeSlider>(R.id.rangeSlider)
                val tvLabel = dialogView.findViewById<TextView>(R.id.tvPriceRangeLabel)

                slider.values = listOf(minPrice.toFloat(), if (maxPrice > 500.0) 500f else maxPrice.toFloat())
                tvLabel.text = if (slider.values[1] >= 500f) "${slider.values[0].toInt()} €+" else "${slider.values[0].toInt()} € - ${slider.values[1].toInt()} €"

                slider.addOnChangeListener { sliderObj, _, _ ->
                    val v = sliderObj.values
                    tvLabel.text = if (v[1] >= 500f) "${v[0].toInt()} €+" else "${v[0].toInt()} € - ${v[1].toInt()} €"
                }

                AlertDialog.Builder(requireContext(), com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                    .setTitle(getString(R.string.filter_price))
                    .setView(dialogView)
                    .setPositiveButton(getString(R.string.btn_apply_filters)) { _, _ ->
                        val v = slider.values
                        minPrice = v[0].toDouble()
                        maxPrice = v[1].toDouble()
                        binding.chipPrice.isChecked = true
                        binding.chipPrice.text = if (maxPrice >= 500.0) "${minPrice.toInt()} €+" else "${minPrice.toInt()} € - ${maxPrice.toInt()} €"
                        loadData()
                    }
                    .setNeutralButton(getString(R.string.btn_clear_filters)) { _, _ ->
                        minPrice = 0.0
                        maxPrice = 500.0
                        binding.chipPrice.isChecked = false
                        binding.chipPrice.text = getString(R.string.filter_price)
                        loadData()
                    }
                    .setNegativeButton(getString(R.string.btn_cancel)) { _, _ ->
                        binding.chipPrice.isChecked = minPrice > 0.0 || maxPrice < 500.0
                    }
                    .setOnCancelListener {
                        binding.chipPrice.isChecked = minPrice > 0.0 || maxPrice < 500.0
                    }
                    .show()
            }
        }

        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        val ownedCards = MockRepository.getOwnedCards()
        val searchQuery = binding.etCollectionSearch.text?.toString()?.trim().orEmpty()

        val filtered = ownedCards.filter { card ->
            val matchesSearch = searchQuery.isBlank() || card.name.contains(searchQuery, true) || card.expansion.contains(searchQuery, true)
            val matchesDuplicates = !binding.chipDuplicates.isChecked || card.quantity > 1
            val matchesCondition = selectedCondition == null || card.condition.equals(selectedCondition, true)
            val matchesPrice = when {
                minPrice == 0.0 && maxPrice >= 500.0 -> true
                maxPrice >= 500.0 -> (card.priceEur ?: 0.0) >= minPrice
                else -> (card.priceEur ?: 0.0) in minPrice..maxPrice
            }
            matchesSearch && matchesDuplicates && matchesCondition && matchesPrice
        }

        val sorted = when (currentSortMode) {
            SortMode.RECENTLY_ADDED -> filtered
            SortMode.OLDEST_ADDED -> filtered.reversed()
            SortMode.HIGHEST_PRICE -> filtered.sortedByDescending { it.priceEur ?: 0.0 }
            SortMode.LOWEST_PRICE -> filtered.sortedBy { it.priceEur ?: 0.0 }
            SortMode.OLDEST_SET -> filtered.sortedBy { it.expansion }
            SortMode.NEWEST_SET -> filtered.sortedByDescending { it.expansion }
        }

        adapter.updateCards(sorted)
        binding.tvTotalCount.text = getString(R.string.card_count_format, ownedCards.size)

        if (sorted.isEmpty()) {
            binding.tvEmptyCollection.visibility = View.VISIBLE
            binding.rvCollection.visibility = View.GONE
        } else {
            binding.tvEmptyCollection.visibility = View.GONE
            binding.rvCollection.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
