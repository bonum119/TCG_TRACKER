package com.example.tcg_tracker

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
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
import java.io.File

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

        binding.btnExportAlbum.setOnClickListener {
            showExportOptions()
        }

        loadFolderData()
    }

    private fun showExportOptions() {
        val folder = MockRepository.getFolders().find { it.id == folderId } ?: return
        val json = MockRepository.exportAlbumToJson(folderId) ?: return

        val options = arrayOf("Compartir", "Guardar en la carpeta Descargas")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Exportar álbum \"${folder.title}\"")
            .setItems(options) { _, which ->
                if (which == 0) {
                    try {
                        val file = File(requireContext().cacheDir, "tcg_tracker_album.json")
                        file.writeText(json)
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            requireContext(),
                            "${requireContext().packageName}.fileprovider",
                            file
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(intent, "Compartir álbum con un amigo"))
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    try {
                        val filename = "tcg_tracker_album_${folder.title.lowercase().replace(Regex("[^a-z0-9]"), "_")}.json"
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val values = ContentValues().apply {
                                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                            }
                            val resolver = requireContext().contentResolver
                            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                            if (uri != null) {
                                resolver.openOutputStream(uri)?.use { out ->
                                    out.write(json.toByteArray())
                                }
                                Toast.makeText(requireContext(), "Álbum guardado en Descargas", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(requireContext(), "No se pudo guardar el archivo", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            downloadsDir.mkdirs()
                            val file = File(downloadsDir, filename)
                            file.writeText(json)
                            Toast.makeText(requireContext(), "Álbum guardado en Descargas", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(requireContext(), "Error al descargar: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
