package com.example.tcg_tracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tcg_tracker.data.MockRepository
import com.example.tcg_tracker.databinding.DialogAddFolderBinding
import com.example.tcg_tracker.databinding.FragmentListsBinding
import java.io.File

class ListsFragment : Fragment() {

    private var _binding: FragmentListsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ListsAdapter

    private val importJsonLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val jsonStr = requireContext().contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() }
                if (!jsonStr.isNullOrBlank()) {
                    val success = MockRepository.importFromJson(jsonStr)
                    if (success) {
                        loadFolders()
                        Toast.makeText(requireContext(), getString(R.string.import_success), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.import_error), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ListsAdapter(
            folders = emptyList(),
            onFolderClick = { folder ->
                val bundle = bundleOf("folderId" to folder.id)
                findNavController().navigate(R.id.navigation_folder_detail, bundle)
            },
            onDeleteFolder = { folder ->
                AlertDialog.Builder(requireContext(), com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                    .setTitle(getString(R.string.dialog_delete_folder_title))
                    .setMessage(getString(R.string.dialog_delete_folder_msg, folder.title))
                    .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                        MockRepository.deleteFolder(folder.id)
                        loadFolders()
                        Toast.makeText(requireContext(), getString(R.string.folder_deleted), Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .show()
            }
        )

        binding.rvFolders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ListsFragment.adapter
        }

        binding.btnAddFolder.setOnClickListener {
            showAddFolderDialog()
        }

        binding.btnExportAlbums.setOnClickListener {
            try {
                val json = MockRepository.exportToJson()
                val file = File(requireContext().cacheDir, "tcg_tracker_backup.json")
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
                startActivity(Intent.createChooser(intent, getString(R.string.export_chooser_title)))
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnImportAlbums.setOnClickListener {
            importJsonLauncher.launch("application/json")
        }

        loadFolders()
    }

    override fun onResume() {
        super.onResume()
        loadFolders()
    }

    private fun loadFolders() {
        adapter.updateFolders(MockRepository.getFolders())
    }

    private fun showAddFolderDialog() {
        val dialogBinding = DialogAddFolderBinding.inflate(layoutInflater)

        AlertDialog.Builder(requireContext(), com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setTitle(R.string.dialog_create_folder_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.btn_create) { _, _ ->
                val title = dialogBinding.etFolderTitle.text?.toString().orEmpty()
                val desc = dialogBinding.etFolderDescription.text?.toString().orEmpty()

                if (title.isNotBlank()) {
                    MockRepository.addFolder(title, desc)
                    loadFolders()
                    Toast.makeText(requireContext(), getString(R.string.folder_created), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
