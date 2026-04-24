package com.mirrormood.ui.wellness

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mirrormood.data.db.WellnessSessionEntity
import com.mirrormood.databinding.ItemSessionHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionHistoryAdapter :
    ListAdapter<WellnessSessionEntity, SessionHistoryAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSessionHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemSessionHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())

        fun bind(session: WellnessSessionEntity) {
            binding.tvSessionType.text = session.type
            binding.tvSessionEmoji.text = when (session.type) {
                "Breathing" -> "🫧"
                "Body Scan" -> "🧘"
                "Gratitude" -> "🙏"
                else -> "✨"
            }
            binding.tvSessionDate.text = dateFormat.format(Date(session.completedAt))

            val totalSeconds = session.durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            binding.tvSessionDuration.text = if (minutes > 0) {
                "${minutes}m ${seconds}s"
            } else {
                "${seconds}s"
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<WellnessSessionEntity>() {
            override fun areItemsTheSame(a: WellnessSessionEntity, b: WellnessSessionEntity) =
                a.id == b.id

            override fun areContentsTheSame(a: WellnessSessionEntity, b: WellnessSessionEntity) =
                a == b
        }
    }
}
