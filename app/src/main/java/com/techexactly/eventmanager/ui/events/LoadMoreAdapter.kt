package com.techexactly.eventmanager.ui.events

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.techexactly.eventmanager.databinding.ItemLoadMoreBinding

/**
 * Single-item footer for [EventListFragment]'s RecyclerView (via ConcatAdapter): a "Load more"
 * row that only exists while there are more already-fetched, real-time events than are
 * currently shown - see [EventListViewModel.loadMore].
 */
class LoadMoreAdapter(private val onLoadMore: () -> Unit) : RecyclerView.Adapter<LoadMoreAdapter.ViewHolder>() {

    private var visible = false

    fun setVisible(visible: Boolean) {
        if (this.visible == visible) return
        this.visible = visible
        if (visible) notifyItemInserted(0) else notifyItemRemoved(0)
    }

    override fun getItemCount() = if (visible) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLoadMoreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.loadMoreButton.setOnClickListener { onLoadMore() }
    }

    class ViewHolder(val binding: ItemLoadMoreBinding) : RecyclerView.ViewHolder(binding.root)
}
