package com.techexactly.eventmanager.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.techexactly.eventmanager.databinding.FragmentDashboardBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpChartAppearance()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.totalEventsText.text = state.total.toString()
                    binding.upcomingEventsText.text = state.upcoming.toString()
                    binding.pastEventsText.text = state.past.toString()
                    binding.cacheNoticeText.visibility = if (state.isFromCache) View.VISIBLE else View.GONE
                    renderChart(state)
                }
            }
        }
    }

    private fun setUpChartAppearance() = with(binding.eventsBarChart) {
        description.isEnabled = false
        legend.isEnabled = false
        setFitBars(true)
        setNoDataText("Add some events to see your stats here")
        axisRight.isEnabled = false
        axisLeft.axisMinimum = 0f
        axisLeft.granularity = 1f
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)
    }

    private fun renderChart(state: DashboardUiState) {
        if (state.monthCounts.isEmpty()) {
            binding.eventsBarChart.clear()
            binding.chartEmptyText.visibility = View.VISIBLE
            binding.chartCard.visibility = View.GONE
            return
        }
        binding.chartEmptyText.visibility = View.GONE
        binding.chartCard.visibility = View.VISIBLE

        val entries = state.monthCounts.mapIndexed { index, count -> BarEntry(index.toFloat(), count.toFloat()) }
        val dataSet = BarDataSet(entries, "Events").apply {
            color = 0xFF3F51B5.toInt()
            valueTextSize = 12f
        }
        binding.eventsBarChart.xAxis.valueFormatter = IndexAxisValueFormatter(state.monthLabels)
        binding.eventsBarChart.data = BarData(dataSet).apply { barWidth = 0.6f }
        binding.eventsBarChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
