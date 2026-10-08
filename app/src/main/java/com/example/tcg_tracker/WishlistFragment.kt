package com.example.tcg_tracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tcg_tracker.data.MockRepository
import com.example.tcg_tracker.databinding.FragmentWishlistBinding
import com.example.tcg_tracker.util.PriceFormat
import com.google.android.material.snackbar.Snackbar

class WishlistFragment : Fragment() {

    private var _binding: FragmentWishlistBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: WishlistAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWishlistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = WishlistAdapter(
            cards = emptyList(),
            onCardClick = { card ->
                val bundle = bundleOf("cardId" to card.id)
                findNavController().navigate(R.id.navigation_detail, bundle)
            },
            onMarkOwned = { card ->
                MockRepository.toggleOwned(card.id)
                Snackbar.make(binding.root, getString(R.string.added_to_collection, card.name), Snackbar.LENGTH_SHORT).show()
                loadWishlistData()
            },
            onRemoveWish = { card ->
                MockRepository.toggleWished(card.id)
                Snackbar.make(binding.root, getString(R.string.removed_from_wishlist), Snackbar.LENGTH_SHORT).show()
                loadWishlistData()
            }
        )

        binding.rvWishlist.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@WishlistFragment.adapter
        }

        binding.fabSearchWish.setOnClickListener {
            findNavController().navigate(R.id.navigation_search)
        }

        loadWishlistData()
    }

    override fun onResume() {
        super.onResume()
        loadWishlistData()
    }

    private fun loadWishlistData() {
        val wishedCards = MockRepository.getWishedCards()
        adapter.updateCards(wishedCards)

        val totalValueEur = wishedCards.sumOf { it.priceEur ?: 0.0 }
        val valueStr = PriceFormat.eur(totalValueEur)

        binding.tvWishlistSummary.text = getString(
            R.string.wishlist_summary_format,
            wishedCards.size,
            valueStr
        )

        if (wishedCards.isEmpty()) {
            binding.tvEmptyWishlist.visibility = View.VISIBLE
            binding.rvWishlist.visibility = View.GONE
        } else {
            binding.tvEmptyWishlist.visibility = View.GONE
            binding.rvWishlist.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
