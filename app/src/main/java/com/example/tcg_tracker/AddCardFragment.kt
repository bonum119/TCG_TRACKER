package com.example.tcg_tracker

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.tcg_tracker.data.MockRepository
import com.example.tcg_tracker.data.PokemonCard
import com.example.tcg_tracker.data.PriceRepository
import com.example.tcg_tracker.databinding.FragmentAddCardBinding
import com.example.tcg_tracker.util.PriceFormat
import com.example.tcg_tracker.util.loadCardImage
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class AddCardFragment : Fragment() {

    private var _binding: FragmentAddCardBinding? = null
    private val binding get() = _binding!!

    private var selectedImageUriString: String? = null
    private var fetchedPriceUsd: Double? = null
    private var fetchedPriceEur: Double? = null
    private var fetchedStoreUrl: String? = null

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val savedPath = MockRepository.saveBitmapToInternalStorage(requireContext(), bitmap)
            selectedImageUriString = savedPath
            binding.ivCardPreview.loadCardImage(savedPath)
            binding.tvPhotoHint.visibility = View.GONE
        }
    }

    private val getContentLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = MockRepository.saveUriToInternalStorage(requireContext(), uri)
            selectedImageUriString = savedPath ?: uri.toString()
            binding.ivCardPreview.loadCardImage(selectedImageUriString)
            binding.tvPhotoHint.visibility = View.GONE
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                takePictureLauncher.launch(null)
            } catch (_: Exception) {
                Snackbar.make(binding.root, "No se pudo abrir la cámara", Snackbar.LENGTH_SHORT).show()
            }
        } else {
            Snackbar.make(binding.root, "Se requiere permiso de cámara", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddCardBinding.inflate(inflater, container, false)
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

        binding.btnTakePhoto.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                try {
                    takePictureLauncher.launch(null)
                } catch (_: Exception) {
                    Snackbar.make(binding.root, "No se pudo abrir la cámara", Snackbar.LENGTH_SHORT).show()
                }
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        binding.btnPickGallery.setOnClickListener {
            getContentLauncher.launch("image/*")
        }

        binding.btnFetchPrices.setOnClickListener {
            val cardName = binding.etCardName.text?.toString()?.trim().orEmpty()
            val expansion = binding.etExpansion.text?.toString()?.trim().orEmpty()

            if (cardName.isBlank()) {
                binding.tilCardName.error = getString(R.string.error_name_required)
                return@setOnClickListener
            } else {
                binding.tilCardName.error = null
            }

            binding.pbPriceLoading.visibility = View.VISIBLE
            binding.tvPriceResult.visibility = View.GONE

            val appContext = requireContext().applicationContext
            lifecycleScope.launch {
                val priceResult = PriceRepository.fetchCardPrices(cardName, expansion, appContext)
                if (_binding == null) return@launch
                fetchedPriceUsd = priceResult.priceUsd
                fetchedPriceEur = priceResult.priceEur
                fetchedStoreUrl = priceResult.storeUrl

                binding.pbPriceLoading.visibility = View.GONE
                binding.tvPriceResult.visibility = View.VISIBLE

                val usdStr = PriceFormat.usd(priceResult.priceUsd)
                val eurStr = PriceFormat.eur(priceResult.priceEur)

                binding.tvPriceResult.text = getString(R.string.prices_fetched_format, usdStr, eurStr)
            }
        }

        binding.btnSaveCard.setOnClickListener {
            val name = binding.etCardName.text?.toString()?.trim().orEmpty()
            val expansion = binding.etExpansion.text?.toString()?.trim().orEmpty()
            val cardNumber = binding.etCardNumber.text?.toString()?.trim().orEmpty()
            val type = binding.etType.text?.toString()?.trim().orEmpty()
            val rarity = binding.etRarity.text?.toString()?.trim().orEmpty()

            if (name.isBlank()) {
                binding.tilCardName.error = getString(R.string.error_name_required)
                return@setOnClickListener
            }

            val selectedChipId = binding.chipGroupCondition.checkedChipId
            val selectedChip = binding.chipGroupCondition.findViewById<Chip>(selectedChipId)
            val condition = selectedChip?.text?.toString() ?: "Near Mint"

            binding.pbPriceLoading.visibility = View.VISIBLE
            val appContext = requireContext().applicationContext

            lifecycleScope.launch {
                var usd = fetchedPriceUsd
                var eur = fetchedPriceEur
                var store = fetchedStoreUrl

                if (usd == null && eur == null) {
                    val priceResult = PriceRepository.fetchCardPrices(name, expansion, appContext, cardNumber = cardNumber)
                    usd = priceResult.priceUsd
                    eur = priceResult.priceEur
                    store = priceResult.storeUrl
                }

                val newCard = PokemonCard(
                    id = "card_${System.currentTimeMillis()}",
                    name = name,
                    expansion = if (expansion.isBlank()) "Desconocida" else expansion,
                    cardNumber = if (cardNumber.isBlank()) "N/A" else cardNumber,
                    type = if (type.isBlank()) "Incoloro" else type,
                    rarity = if (rarity.isBlank()) "Común" else rarity,
                    condition = condition,
                    isOwned = true,
                    imageUri = selectedImageUriString,
                    priceUsd = usd,
                    priceEur = eur,
                    storeUrl = store
                )

                MockRepository.addCard(newCard)
                if (_binding != null) {
                    binding.pbPriceLoading.visibility = View.GONE
                    Snackbar.make(requireView(), R.string.card_saved_success, Snackbar.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val toolbar = (requireActivity() as? AppCompatActivity)?.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar?.setNavigationOnClickListener(null)
        _binding = null
    }
}
