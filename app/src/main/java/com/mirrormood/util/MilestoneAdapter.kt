package com.mirrormood.util

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mirrormood.data.Milestone
import com.mirrormood.databinding.ItemMilestoneBinding

class MilestoneAdapter : ListAdapter<Milestone, MilestoneAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMilestoneBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemMilestoneBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(milestone: Milestone) {
            binding.tvMilestoneEmoji.text = milestone.emoji
            binding.tvMilestoneTitle.text = milestone.title
            binding.tvMilestoneDescription.text = milestone.description
            binding.tvMilestoneProgressText.text = "${milestone.currentAmount} / ${milestone.target}"
            binding.progressMilestone.progress = milestone.progress

            if (milestone.isUnlocked) {
                binding.clMilestoneContainer.alpha = 1.0f
                binding.progressMilestone.visibility = View.GONE
                binding.tvMilestoneProgressText.text = "Unlocked!"
            } else {
                binding.clMilestoneContainer.alpha = 0.5f
                binding.progressMilestone.visibility = View.VISIBLE
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Milestone>() {
        override fun areItemsTheSame(oldItem: Milestone, newItem: Milestone) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Milestone, newItem: Milestone) = oldItem == newItem
    }
}
