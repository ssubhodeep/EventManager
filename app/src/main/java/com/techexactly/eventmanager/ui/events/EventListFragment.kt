package com.techexactly.eventmanager.ui.events

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.techexactly.eventmanager.R
import com.techexactly.eventmanager.data.model.Event
import com.techexactly.eventmanager.data.model.Resource
import com.techexactly.eventmanager.databinding.FragmentEventListBinding
import com.techexactly.eventmanager.util.EventStatusFilter
import kotlinx.coroutines.launch
import kotlin.math.abs

@AndroidEntryPoint
class EventListFragment : Fragment() {

    private var _binding: FragmentEventListBinding? = null
    private val binding get() = _binding!!

    // Shared with MainActivity (which uses the same call at the Activity scope) so both the
    // offline/syncing banner in the toolbar area and this list observe one Firestore listener.
    private val viewModel: EventListViewModel by activityViewModels()
    private lateinit var adapter: EventAdapter
    private lateinit var loadMoreAdapter: LoadMoreAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEventListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = EventAdapter(
            onEdit = { event ->
                startActivity(
                    Intent(requireContext(), AddEditEventActivity::class.java)
                        .putExtra(AddEditEventActivity.EXTRA_EVENT_ID, event.id)
                )
            },
            onDelete = { event -> confirmDelete(event) }
        )
        loadMoreAdapter = LoadMoreAdapter { viewModel.loadMore() }
        binding.eventsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.eventsRecyclerView.adapter = ConcatAdapter(adapter, loadMoreAdapter)
        attachSwipeToDelete()

        binding.swipeRefresh.setOnRefreshListener {
            // The list is realtime, so "refresh" just re-syncs from the server cache;
            // stop the spinner immediately once Firestore's listener has already given us data.
            binding.swipeRefresh.isRefreshing = false
        }

        binding.addEventFab.setOnClickListener {
            startActivity(Intent(requireContext(), AddEditEventActivity::class.java))
        }

        binding.statusFilterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                R.id.filterUpcoming -> EventStatusFilter.UPCOMING
                R.id.filterPast -> EventStatusFilter.PAST
                else -> EventStatusFilter.ALL
            }
            viewModel.setStatusFilter(filter)
        }

        observeState()
    }

    /** Swipe left/right on a row to delete it, with an "Undo" Snackbar - a faster path than the
     *  explicit delete button for anyone comfortable with the gesture. */
    private fun attachSwipeToDelete() {
        val deleteBackground = ColorDrawable(Color.parseColor("#D32F2F"))

        val callback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            // The "Load more" footer row shares this RecyclerView (via ConcatAdapter) - don't
            // let it be swiped away like an event.
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                if (viewHolder !is EventAdapter.EventViewHolder) return 0
                return super.getMovementFlags(recyclerView, viewHolder)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val event = adapter.currentList[position]
                deleteWithUndo(event)
            }

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                if (abs(dX) > 4f) {
                    deleteBackground.setBounds(itemView.left, itemView.top, itemView.right, itemView.bottom)
                    deleteBackground.draw(c)
                }
                super.onChildDraw(c, rv, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.eventsRecyclerView)
    }

    private fun confirmDelete(event: Event) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete event?")
            .setMessage("\"${event.title}\" will be permanently deleted.")
            .setPositiveButton("Delete") { _, _ -> deleteWithUndo(event) }
            .setNegativeButton("Cancel") { _, _ -> adapter.notifyDataSetChanged() }
            .show()
    }

    private fun deleteWithUndo(event: Event) {
        viewModel.deleteEvent(event)
        Snackbar.make(binding.root, "\"${event.title}\" deleted", Snackbar.LENGTH_LONG)
            .setAction("Undo") { viewModel.restoreEvent(event) }
            .show()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.progressBar.visibility = if (state is Resource.Loading) View.VISIBLE else View.GONE
                        when (state) {
                            is Resource.Success -> {
                                adapter.submitList(state.data.events)
                                loadMoreAdapter.setVisible(state.data.canLoadMore)
                                binding.emptyState.visibility = if (state.data.events.isEmpty()) View.VISIBLE else View.GONE
                                if (state.data.isFiltered) {
                                    binding.emptyStateTitle.text = "No matching events"
                                    binding.emptyStateSubtitle.text = "Try a different search or filter"
                                } else {
                                    binding.emptyStateTitle.text = "No events yet"
                                    binding.emptyStateSubtitle.text = "Tap + to create your first event"
                                }
                            }
                            is Resource.Error -> Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                            else -> Unit
                        }
                    }
                }
                launch {
                    viewModel.errors.collect { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    /** Called by MainActivity when text is entered in the toolbar search field. */
    fun setSearchQuery(query: String) {
        viewModel.setSearchQuery(query)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
