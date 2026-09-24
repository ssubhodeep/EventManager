package com.techexactly.eventmanager.ui.events

import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.techexactly.eventmanager.R
import com.techexactly.eventmanager.data.model.Event
import com.techexactly.eventmanager.databinding.ItemEventBinding
import com.techexactly.eventmanager.util.DateTimeUtils

class EventAdapter(
    private val onEdit: (Event) -> Unit,
    private val onDelete: (Event) -> Unit
) : ListAdapter<Event, EventAdapter.EventViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class EventViewHolder(private val binding: ItemEventBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(event: Event) {
            val context = binding.root.context
            binding.titleText.text = event.title
            binding.dateTimeText.text = DateTimeUtils.formatForDisplay(event.dateTimeMillis)
            binding.locationText.text = event.location
            binding.locationText.visibility = if (event.location.isBlank()) android.view.View.GONE else android.view.View.VISIBLE

            // Accent bar + badge share one semantic color set per status, defined in colors.xml
            // (and overridden in values-night) so dark mode gets correctly-contrasted tints for free.
            val accentColorRes: Int
            val bgColorRes: Int
            val textColorRes: Int
            if (event.isUpcoming) {
                binding.statusBadge.text = "UPCOMING"
                accentColorRes = R.color.upcoming_accent
                bgColorRes = R.color.upcoming_bg
                textColorRes = R.color.upcoming_text
            } else {
                binding.statusBadge.text = "PAST"
                accentColorRes = R.color.past_accent
                bgColorRes = R.color.past_bg
                textColorRes = R.color.past_text
            }
            val accentColor = ContextCompat.getColor(context, accentColorRes)
            binding.accentBar.setBackgroundColor(accentColor)
            binding.statusBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, bgColorRes))
            binding.statusBadge.setTextColor(ContextCompat.getColor(context, textColorRes))

            binding.root.setOnClickListener { onEdit(event) }
            binding.editButton.setOnClickListener { onEdit(event) }
            binding.deleteButton.setOnClickListener { onDelete(event) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Event>() {
            override fun areItemsTheSame(oldItem: Event, newItem: Event) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Event, newItem: Event) = oldItem == newItem
        }
    }
}
