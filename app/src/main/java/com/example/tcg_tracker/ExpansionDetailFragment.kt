package com.example.tcg_tracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tcg_tracker.data.MockRepository
import com.example.tcg_tracker.data.OnlineCardResult
import com.example.tcg_tracker.data.PokemonCard
import com.example.tcg_tracker.data.PriceRepository
import com.example.tcg_tracker.databinding.FragmentExpansionDetailBinding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ExpansionDetailFragment : Fragment() {

    private var _binding: FragmentExpansionDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ExpansionCardsAdapter
    private var expansionName: String = ""
    private var allCardsInExpansion: List<PokemonCard> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExpansionDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ensure back arrow works robustly with popBackStack
        val toolbar = (requireActivity() as? AppCompatActivity)?.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar?.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                findNavController().popBackStack()
            }
        })

        expansionName = arguments?.getString("expansionName") ?: ""

        adapter = ExpansionCardsAdapter(
            cards = emptyList(),
            onCardClick = { card ->
                val bundle = bundleOf("cardId" to card.id)
                findNavController().navigate(R.id.navigation_detail, bundle)
            },
            onToggleOwned = { card ->
                val owned = MockRepository.toggleOwned(card.id)
                val msg = if (owned) getString(R.string.added_to_collection, card.name)
                else getString(R.string.removed_from_collection, card.name)
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                loadExpansionData()
            },
            onToggleWish = { card ->
                val wished = MockRepository.toggleWished(card.id)
                val msg = if (wished) getString(R.string.added_to_wishlist)
                else getString(R.string.removed_from_wishlist)
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                loadExpansionData()
            }
        )

        binding.rvExpansionCards.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ExpansionDetailFragment.adapter
        }

        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            filterCards(checkedIds.firstOrNull())
        }

        loadExpansionData()
    }

    override fun onResume() {
        super.onResume()
        loadExpansionData()
    }

    private fun loadExpansionData() {
        // 1. Load local instant view
        allCardsInExpansion = MockRepository.getCardsForExpansion(expansionName)
        updateUI()

        // 2. Fetch full set online in background
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val onlineResults = PriceRepository.fetchCardsForExpansion(expansionName, appContext)
            if (onlineResults.isNotEmpty() && _binding != null) {
                onlineResults.forEach { result ->
                    toCard(result)
                }
                allCardsInExpansion = MockRepository.getCardsForExpansion(expansionName)
                if (_binding != null) {
                    updateUI()
                }
            }
        }
    }

    private fun updateUI() {
        val ownedCount = allCardsInExpansion.count { it.isOwned }
        val totalCount = allCardsInExpansion.size
        val percent = if (totalCount == 0) 0 else ((ownedCount.toDouble() / totalCount.toDouble()) * 100).toInt()

        binding.tvHeaderTitle.text = getString(R.string.title_expansion_detail, expansionName)
        binding.tvHeaderProgress.text = getString(
            R.string.progress_format,
            ownedCount,
            totalCount,
            percent
        )
        binding.pbHeaderProgress.progress = percent

        binding.chipAll.text = getString(R.string.tab_all_cards, totalCount)
        binding.chipOwned.text = getString(R.string.tab_owned_cards, ownedCount)
        binding.chipMissing.text = getString(R.string.tab_missing_cards, totalCount - ownedCount)

        filterCards(binding.chipGroupFilter.checkedChipId)
    }

    private fun toCard(result: OnlineCardResult): PokemonCard {
        val existing = MockRepository.getCardById(result.id)
        val card = PokemonCard(
            id = result.id,
            name = result.name,
            expansion = result.expansion,
            cardNumber = result.cardNumber,
            rarity = result.rarity,
            type = result.type,
            condition = existing?.condition ?: "Near Mint",
            isOwned = existing?.isOwned ?: false,
            isWished = existing?.isWished ?: false,
            quantity = existing?.quantity ?: 1,
            folderId = existing?.folderId,
            imageUri = existing?.imageUri?.takeIf { !it.startsWith("http") } ?: result.imageUrl,
            priceUsd = result.priceUsd,
            priceEur = result.priceEur,
            storeUrl = result.storeUrl
        )
        MockRepository.upsertCard(card)
        return MockRepository.getCardById(result.id) ?: card
    }

    private fun filterCards(selectedChipId: Int?) {
        val filtered = when (selectedChipId) {
            R.id.chipOwned -> allCardsInExpansion.filter { it.isOwned }
            R.id.chipMissing -> allCardsInExpansion.filter { !it.isOwned }
            else -> allCardsInExpansion
        }

        adapter.updateCards(filtered)

        if (filtered.isEmpty()) {
            binding.tvEmptyCards.visibility = View.VISIBLE
            binding.rvExpansionCards.visibility = View.GONE
        } else {
            binding.tvEmptyCards.visibility = View.GONE
            binding.rvExpansionCards.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val toolbar = (requireActivity() as? AppCompatActivity)?.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar?.setNavigationOnClickListener(null)
        _binding = null
    }
}
