package com.example.tcg_tracker

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tcg_tracker.data.ExpansionStats
import com.example.tcg_tracker.data.MockRepository
import com.example.tcg_tracker.databinding.FragmentExpansionsBinding

class ExpansionsFragment : Fragment() {

    private var _binding: FragmentExpansionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ExpansionsAdapter
    private var allExpansions: List<ExpansionStats> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExpansionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ExpansionsAdapter(emptyList()) { stats ->
            val bundle = bundleOf("expansionName" to stats.name)
            findNavController().navigate(R.id.navigation_expansion_detail, bundle)
        }

        binding.rvExpansions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ExpansionsFragment.adapter
        }

        binding.etSearchExpansion.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterExpansions(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        allExpansions = MockRepository.getExpansionsWithStats()
        filterExpansions(binding.etSearchExpansion.text?.toString().orEmpty())
    }

    private fun filterExpansions(query: String) {
        val filtered = if (query.isBlank()) {
            allExpansions
        } else {
            allExpansions.filter { it.name.contains(query, ignoreCase = true) }
        }
        adapter.updateExpansions(filtered)

        if (filtered.isEmpty()) {
            binding.tvEmptyExpansions.visibility = View.VISIBLE
            binding.rvExpansions.visibility = View.GONE
        } else {
            binding.tvEmptyExpansions.visibility = View.GONE
            binding.rvExpansions.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
