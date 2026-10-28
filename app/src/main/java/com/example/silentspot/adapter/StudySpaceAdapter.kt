package com.example.silentspot.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.silentspot.data.model.StudySpace
import com.example.silentspot.databinding.ItemStudySpaceBinding
import com.example.silentspot.utils.NoiseLevelUtils
import com.example.silentspot.utils.NoiseLevelUtils.applyStatus

class StudySpaceAdapter(
    private val onItemClick: (StudySpace) -> Unit
) : ListAdapter<StudySpace, StudySpaceAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemStudySpaceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val b: ItemStudySpaceBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(space: StudySpace) {
            b.tvSpaceName.text = space.name
            b.tvSpaceLocation.text = space.location
            b.tvSpaceDb.text = "${NoiseLevelUtils.formatDb(space.noiseLevel)} dB"
            b.tvSpaceStatus.applyStatus(space.noiseLevel)
            b.tvSpaceLastUpdated.text = NoiseLevelUtils.formatTimestamp(space.lastUpdated)
            b.root.setOnClickListener { onItemClick(space) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<StudySpace>() {
        override fun areItemsTheSame(old: StudySpace, new: StudySpace) = old.id == new.id
        override fun areContentsTheSame(old: StudySpace, new: StudySpace) = old == new
    }
}
