package com.example.tcg_tracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.tcg_tracker.data.MockRepository
import com.example.tcg_tracker.data.PokemonCard
import com.example.tcg_tracker.data.PriceRepository
import com.example.tcg_tracker.databinding.FragmentDetailBinding
import com.example.tcg_tracker.util.PriceFormat
import com.example.tcg_tracker.util.loadCardImage
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.util.Locale

class DetailFragment : Fragment() {

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    private var currentCard: PokemonCard? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
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

        val cardId = arguments?.getString("cardId") ?: ""
        currentCard = MockRepository.getCardById(cardId)

        val card = currentCard
        if (card == null) {
            findNavController().popBackStack()
            return
        }

        binding.ivHeroCardImage.loadCardImage(card.imageUri)
        binding.tvDetailTitle.text = card.name
        binding.tvDetailSubtitle.text = getString(
            R.string.card_expansion_format,
            card.expansion,
            card.cardNumber
        )
        binding.tvRarity.text = getString(R.string.rarity_label, card.rarity)
        binding.tvType.text = getString(R.string.type_label, card.type)

        updateWishlistButton(card.isWished)
        binding.btnToggleWishlist.setOnClickListener {
            val isWished = MockRepository.toggleWished(card.id)
            updateWishlistButton(isWished)
            val msg = if (isWished) R.string.added_to_wishlist else R.string.removed_from_wishlist
            Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
        }

        updatePriceUI(card.priceUsd, card.priceEur, card.storeUrl)

        binding.btnRefreshPrices.setOnClickListener {
            binding.pbPriceLoadingDetail.visibility = View.VISIBLE
            val appContext = requireContext().applicationContext
            lifecycleScope.launch {
                val prices = PriceRepository.fetchCardPrices(
                    cardName = card.name,
                    expansion = card.expansion,
                    context = appContext,
                    cardId = card.id,
                    cardNumber = card.cardNumber
                )
                if (_binding == null) return@launch

                val found = prices.priceUsd != null || prices.priceEur != null
                // `card` es el mismo objeto que guarda el repositorio; solo se pisa lo que trae valor real
                MockRepository.updateCardPrices(card.id, prices.priceUsd, prices.priceEur, prices.storeUrl)

                binding.pbPriceLoadingDetail.visibility = View.GONE
                updatePriceUI(card.priceUsd, card.priceEur, card.storeUrl)
                if (found) Snackbar.make(binding.root, R.string.prices_updated, Snackbar.LENGTH_SHORT).show()
                else Snackbar.make(binding.root, "La web no publica precio para esta carta", Snackbar.LENGTH_LONG).show()
            }
        }

        when (card.condition.lowercase(Locale.ROOT)) {
            "mint" -> binding.chipMint.isChecked = true
            "near mint" -> binding.chipNearMint.isChecked = true
            "played" -> binding.chipPlayed.isChecked = true
            "damaged" -> binding.chipDamaged.isChecked = true
            else -> binding.chipNearMint.isChecked = true
        }

        var currentQuantity = card.quantity
        binding.tvQuantity.text = currentQuantity.toString()

        binding.btnPlus.setOnClickListener {
            currentQuantity++
            binding.tvQuantity.text = currentQuantity.toString()
        }

        binding.btnMinus.setOnClickListener {
            if (currentQuantity > 1) {
                currentQuantity--
                binding.tvQuantity.text = currentQuantity.toString()
            }
        }

        binding.btnSave.setOnClickListener {
            val selectedChipId = binding.chipGroupCondition.checkedChipId
            val selectedChip = binding.chipGroupCondition.findViewById<Chip>(selectedChipId)
            val newCondition = selectedChip?.text?.toString() ?: "Near Mint"

            MockRepository.updateCardCondition(card.id, newCondition)
            MockRepository.updateCardQuantity(card.id, currentQuantity)

            Snackbar.make(binding.root, R.string.changes_saved, Snackbar.LENGTH_SHORT).show()
        }

        binding.btnDeleteCard.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext(), com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("¿Eliminar carta?")
                .setMessage("¿Seguro que quieres borrar \"${card.name}\" de tu colección y listas?")
                .setPositiveButton("Eliminar") { _, _ ->
                    MockRepository.deleteCard(card.id)
                    Snackbar.make(binding.root, "Carta eliminada", Snackbar.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun updateWishlistButton(isWished: Boolean) {
        if (isWished) {
            binding.btnToggleWishlist.setIconResource(R.drawable.ic_heart_filled)
            binding.btnToggleWishlist.setText(R.string.btn_toggle_wish)
        } else {
            binding.btnToggleWishlist.setIconResource(R.drawable.ic_heart_outline)
            binding.btnToggleWishlist.setText(R.string.btn_toggle_wish)
        }
    }

    private fun updatePriceUI(usd: Double?, eur: Double?, storeUrl: String?) {
        val usdText = "TCGPlayer (USD): ${PriceFormat.usd(usd)}"
        val eurText = "Cardmarket (EUR): ${PriceFormat.eur(eur)}"

        binding.tvPriceUsd.text = usdText
        binding.tvPriceEur.text = eurText

        if (!storeUrl.isNullOrBlank()) {
            binding.btnOpenStore.visibility = View.VISIBLE
            binding.btnOpenStore.setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(storeUrl))
                    startActivity(intent)
                } catch (_: Exception) {
                    Snackbar.make(binding.root, "Error al abrir la tienda online", Snackbar.LENGTH_SHORT).show()
                }
            }
        } else {
            binding.btnOpenStore.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val toolbar = (requireActivity() as? AppCompatActivity)?.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar?.setNavigationOnClickListener(null)
        _binding = null
    }
}
