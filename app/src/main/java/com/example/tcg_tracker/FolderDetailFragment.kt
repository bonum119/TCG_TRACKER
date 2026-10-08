package com.example.tcg_tracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.tcg_tracker.data.MockRepository
import com.example.tcg_tracker.databinding.FragmentFolderDetailBinding
import com.example.tcg_tracker.util.FolderDialogs
import com.example.tcg_tracker.util.PriceFormat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar

class FolderDetailFragment : Fragment() {

    private var _binding: FragmentFolderDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: CollectionAdapter
    private var folderId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFolderDetailBinding.inflate(inflater, container, false)
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

        folderId = arguments?.getString("folderId") ?: ""

        adapter = CollectionAdapter(
            cards = emptyList(),
            onCardClick = { card ->
                val bundle = bundleOf("cardId" to card.id)
                findNavController().navigate(R.id.navigation_detail, bundle)
            },
            onCardLongClick = { card ->
                FolderDialogs.showAddToList(requireContext(), card) {
                    loadFolderData()
                }
            }
        )

        binding.rvFolderCards.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = this@FolderDetailFragment.adapter
        }

        loadFolderData()
    }

    override fun onResume() {
        super.onResume()
        loadFolderData()
    }

    private fun loadFolderData() {
        val folder = MockRepository.getFolders().find { it.id == folderId }
        if (folder == null) {
            findNavController().popBackStack()
            return
        }

        val cardsInFolder = MockRepository.getCardsInFolder(folderId)
        adapter.updateCards(cardsInFolder)

        binding.tvFolderTitleDetail.text = folder.title
        if (folder.description.isNotBlank()) {
            binding.tvFolderDescDetail.visibility = View.VISIBLE
            binding.tvFolderDescDetail.text = folder.description
        } else {
            binding.tvFolderDescDetail.visibility = View.GONE
        }

        val totalValueEur = cardsInFolder.sumOf { it.priceEur ?: 0.0 }
        val valueStr = PriceFormat.eur(totalValueEur)
        binding.tvFolderStatsDetail.text = getString(
            R.string.wishlist_summary_format,
            cardsInFolder.size,
            valueStr
        )

        if (cardsInFolder.isEmpty()) {
            binding.tvEmptyFolder.visibility = View.VISIBLE
            binding.rvFolderCards.visibility = View.GONE
        } else {
            binding.tvEmptyFolder.visibility = View.GONE
            binding.rvFolderCards.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val toolbar = (requireActivity() as? AppCompatActivity)?.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar?.setNavigationOnClickListener(null)
        _binding = null
    }
}
