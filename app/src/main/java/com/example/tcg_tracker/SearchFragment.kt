package com.example.tcg_tracker

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tcg_tracker.data.CardCatalog
import com.example.tcg_tracker.data.CardFilters
import com.example.tcg_tracker.data.MockRepository
import com.example.tcg_tracker.data.OnlineCardResult
import com.example.tcg_tracker.data.PokemonCard
import com.example.tcg_tracker.data.PriceRepository
import com.example.tcg_tracker.databinding.FragmentSearchBinding
import com.example.tcg_tracker.util.CardScanner
import com.example.tcg_tracker.util.FolderDialogs
import com.example.tcg_tracker.util.decodeUprightBitmap
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SearchAdapter

    private var filters = CardFilters()
    private var searchJob: Job? = null
    private var currentCards: List<PokemonCard> = emptyList()
    private var suppressTextWatcher = false
    private var pendingPhotoUri: Uri? = null
    private var defaultNoResultsText: CharSequence = ""

    // ------------------------------------------------------------------ Cámara

    /** Foto a resolución completa (Android 10+): mucho mejor para OCR y comparación visual. */
    private val takeFullPictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingPhotoUri
        pendingPhotoUri = null
        if (uri == null) return@registerForActivityResult
        if (success && _binding != null) {
            val appContext = requireContext().applicationContext
            runScan {
                withContext(Dispatchers.IO) { decodeUprightBitmap(appContext, uri) }
                    .also { deleteQuietly(uri) }
            }
        } else {
            deleteQuietly(uri)
        }
    }

    /** Respaldo: miniatura de baja resolución. */
    private val takePreviewLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null && _binding != null) runScan { bitmap }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) launchCamera()
        else _binding?.let {
            Snackbar.make(it.root, "Se requiere permiso de cámara para escanear cartas", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        defaultNoResultsText = binding.tvNoResults.text

        adapter = SearchAdapter(
            cards = emptyList(),
            onCardClick = { card ->
                val buyUrl = card.storeUrl?.takeIf { it.isNotBlank() }
                    ?: "https://www.tcgplayer.com/search/pokemon/product?q=${URLEncoder.encode("${card.name} ${card.expansion}", "UTF-8")}"
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(buyUrl)))
                } catch (_: Exception) {
                    findNavController().navigate(R.id.navigation_detail, bundleOf("cardId" to card.id))
                }
            },
            onAddToggle = { card ->
                val owned = MockRepository.toggleOwned(card.id)
                adapter.updateCards(currentCards) // sin volver a pedir nada a la red
                val message = if (owned) getString(R.string.added_to_collection, card.name)
                else getString(R.string.removed_from_collection, card.name)
                val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                if (owned) {
                    snackbar.setAction("Añadir a lista") {
                        FolderDialogs.showAddToList(requireContext(), card)
                    }
                }
                snackbar.show()
            },
            onCardLongClick = { card -> FolderDialogs.showAddToList(requireContext(), card) }
        )

        binding.rvSearch.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SearchFragment.adapter
        }

        binding.btnScanPhoto.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchCamera()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        binding.btnOpenFilterDialog.setOnClickListener { showFilterDialog() }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!suppressTextWatcher) performSearch(debounceMs = 450L)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        performSearch()
    }

    // ------------------------------------------------------------------ Búsqueda

    /**
     * Busca online sin rendirse durante 30 s (reintentos + consulta cada vez más laxa).
     * Si pasa el tiempo sin resultados online, muestra lo que haya en local.
     */
    private fun performSearch(debounceMs: Long = 0L) {
        searchJob?.cancel()
        val query = binding.etSearch.text?.toString().orEmpty().trim()
        val activeFilters = filters
        val appContext = requireContext().applicationContext

        // Show local cache/results instantly for immediate progressive display
        val localCards = MockRepository.searchLocal(query, activeFilters)
        if (localCards.isNotEmpty()) {
            showResults(localCards)
        } else {
            showLoading(getString(R.string.searching_cards))
        }

        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            val ticker = startTicker(getString(R.string.searching_cards))
            try {
                if (debounceMs > 0) delay(debounceMs)

                if (!activeFilters.isJapanese) {
                    val online = PriceRepository.searchCardsWithRetry(query, activeFilters, appContext)
                    if (online.isNotEmpty()) {
                        showResults(online.map(::toCard))
                    } else if (localCards.isEmpty()) {
                        showResults(emptyList())
                    }
                }
            } finally {
                ticker.cancel()
            }
        }
    }

    /** Convierte el resultado online en la carta "canónica" del repositorio (así owned/listas se mantienen). */
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
            folderId = existing?.folderId,
            imageUri = existing?.imageUri?.takeIf { !it.startsWith("http") } ?: result.imageUrl,
            priceUsd = result.priceUsd,
            priceEur = result.priceEur,
            storeUrl = result.storeUrl
        )
        MockRepository.upsertCard(card)
        return MockRepository.getCardById(result.id) ?: card
    }

    private fun showLoading(label: String) {
        val b = _binding ?: return
        b.pbSearch.visibility = View.VISIBLE
        b.rvSearch.visibility = View.GONE
        b.tvNoResults.text = "$label…"
        b.tvNoResults.visibility = View.VISIBLE
    }

    private fun showResults(cards: List<PokemonCard>) {
        val b = _binding ?: return
        currentCards = cards
        b.pbSearch.visibility = View.GONE
        adapter.updateCards(cards)
        b.rvSearch.scheduleLayoutAnimation()
        if (cards.isEmpty()) {
            b.tvNoResults.text = defaultNoResultsText
            b.tvNoResults.visibility = View.VISIBLE
            b.rvSearch.visibility = View.GONE
        } else {
            b.tvNoResults.visibility = View.GONE
            b.rvSearch.visibility = View.VISIBLE
        }
    }

    /** Muestra el estado de la búsqueda continua e informa si se demora en responder. */
    private fun CoroutineScope.startTicker(label: String): Job = launch {
        var seconds = 0
        while (true) {
            _binding?.let {
                val statusText = when {
                    seconds < 10 -> "$label…"
                    seconds < 25 -> "$label… ${getString(R.string.search_taking_longer)}"
                    else -> getString(R.string.search_in_progress)
                }
                it.tvNoResults.text = statusText
                it.tvNoResults.visibility = View.VISIBLE
            }
            delay(1000)
            seconds++
        }
    }

    // ------------------------------------------------------------------ Escáner de cartas

    private fun launchCamera() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = createScanUri()
            if (uri != null) {
                pendingPhotoUri = uri
                try {
                    takeFullPictureLauncher.launch(uri)
                    return
                } catch (_: Exception) {
                    pendingPhotoUri = null
                    deleteQuietly(uri)
                }
            }
        }
        try {
            takePreviewLauncher.launch(null)
        } catch (_: Exception) {
            _binding?.let { Snackbar.make(it.root, "No se pudo abrir la cámara", Snackbar.LENGTH_SHORT).show() }
        }
    }

    private fun createScanUri(): Uri? = try {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "scan_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TCGTracker")
            }
        }
        requireContext().contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    } catch (_: Exception) {
        null
    }

    private fun deleteQuietly(uri: Uri) {
        try {
            context?.contentResolver?.delete(uri, null, null)
        } catch (_: Exception) {
        }
    }

    /**
     * Foto -> OCR (nombre / número) -> candidatos de la web -> ranking por parecido visual.
     * Muestra los resultados ordenados de más a menos parecido.
     */
    private fun runScan(loadBitmap: suspend () -> Bitmap?) {
        searchJob?.cancel()
        showLoading(getString(R.string.analyzing_card))
        val appContext = requireContext().applicationContext

        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            val ticker = startTicker(getString(R.string.comparing_web))
            try {
                val photo = loadBitmap()
                if (photo == null) {
                    showResults(emptyList())
                    _binding?.let { Snackbar.make(it.root, "No se pudo leer la foto", Snackbar.LENGTH_SHORT).show() }
                    return@launch
                }

                val text = try {
                    CardScanner.recognizeText(photo)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    "" // sin OCR seguimos solo con el parecido visual
                }
                val hints = CardScanner.parseHints(text)

                _binding?.let {
                    suppressTextWatcher = true
                    val guess = hints.nameQueries.firstOrNull().orEmpty()
                    it.etSearch.setText(guess)
                    it.etSearch.setSelection(guess.length)
                    suppressTextWatcher = false
                }

                val matches = CardScanner.findMatches(photo, hints, appContext)
                showResults(matches.map { toCard(it.card) })

                _binding?.let { b ->
                    val msg = matches.firstOrNull()?.let {
                        "Mejor coincidencia: ${it.card.name} (${(it.score * 100).toInt()}%)"
                    } ?: "No se encontró ninguna carta parecida"
                    Snackbar.make(b.root, msg, Snackbar.LENGTH_LONG).show()
                }
            } finally {
                ticker.cancel()
            }
        }
    }

    // ------------------------------------------------------------------ Filtros

    private fun showFilterDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_filter, null)
        val actvCollection = dialogView.findViewById<AutoCompleteTextView>(R.id.actvCollection)
        val actvAttribute = dialogView.findViewById<AutoCompleteTextView>(R.id.actvAttribute)
        val actvRarity = dialogView.findViewById<AutoCompleteTextView>(R.id.actvRarity)
        val btnApply = dialogView.findViewById<MaterialButton>(R.id.btnApplyFilters)
        val btnClear = dialogView.findViewById<MaterialButton>(R.id.btnClearFilters)

        fun bind(view: AutoCompleteTextView, items: List<String>, selected: String) {
            view.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, items))
            view.setText(selected, false)
        }

        bind(actvCollection, CardCatalog.collections.map { it.label }, filters.collection)
        bind(actvAttribute, CardCatalog.attributes.map { it.label }, filters.attribute)
        bind(actvRarity, CardCatalog.rarities.map { it.label }, filters.rarity)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        btnApply.setOnClickListener {
            filters = CardFilters(
                collection = actvCollection.text?.toString()?.takeIf { it.isNotBlank() } ?: CardCatalog.ALL_F,
                attribute = actvAttribute.text?.toString()?.takeIf { it.isNotBlank() } ?: CardCatalog.ALL_M,
                rarity = actvRarity.text?.toString()?.takeIf { it.isNotBlank() } ?: CardCatalog.ALL_F
            )
            dialog.dismiss()
            performSearch()
        }

        btnClear.setOnClickListener {
            filters = CardFilters()
            dialog.dismiss()
            performSearch()
        }

        dialog.show()
    }

    // ------------------------------------------------------------------ Ciclo de vida

    override fun onResume() {
        super.onResume()
        // Solo refresca el estado "en colección"; no relanza la búsqueda de red.
        if (_binding != null) adapter.updateCards(currentCards)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
    }
}
