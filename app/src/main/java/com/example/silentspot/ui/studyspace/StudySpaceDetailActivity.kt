package com.example.silentspot.ui.studyspace

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.silentspot.R
import com.example.silentspot.data.model.StudySpace
import com.example.silentspot.data.repository.StudySpaceRepository
import com.example.silentspot.databinding.ActivityStudySpaceDetailBinding
import com.example.silentspot.utils.Constants
import com.example.silentspot.utils.NoiseLevelUtils
import com.example.silentspot.utils.NoiseLevelUtils.applyStatus
import com.google.firebase.firestore.ListenerRegistration

class StudySpaceDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudySpaceDetailBinding
    private var logsListener: ListenerRegistration? = null
    private var historyListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudySpaceDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarDetail)
        binding.toolbarDetail.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val space = readSpace() ?: return finish()
        bind(space)
        val deviceId = space.deviceId.ifEmpty { Constants.DEFAULT_DEVICE_ID }
        listenForLogs(deviceId)
        listenForHistory(deviceId, space)
    }

    @Suppress("DEPRECATION")
    private fun readSpace(): StudySpace? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent.getParcelableExtra(Constants.EXTRA_STUDY_SPACE, StudySpace::class.java)
        else intent.getParcelableExtra(Constants.EXTRA_STUDY_SPACE)

    private fun bind(space: StudySpace) = with(binding) {
        val deviceId = space.deviceId.ifEmpty { Constants.DEFAULT_DEVICE_ID }

        tvDetailSpaceName.text = space.name
        tvDetailLocation.text = space.location
        tvDetailDb.text = NoiseLevelUtils.formatDb(space.noiseLevel)

        val status = tvDetailStatus.applyStatus(space.noiseLevel)
        progressNoiseLevel.progress = NoiseLevelUtils.progress(space.noiseLevel, space.threshold)
        progressNoiseLevel.setIndicatorColor(color(NoiseLevelUtils.statusColor(status)))

        tvDetailThresholdLabel.text = getString(R.string.label_threshold, space.threshold)
        tvDetailDeviceId.text = deviceId
        tvDetailLastUpdated.text = NoiseLevelUtils.formatTimestamp(space.lastUpdated)

        tvDetailAvailability.setText(if (space.isAvailable) R.string.label_available else R.string.label_unavailable)
        tvDetailAvailability.setTextColor(color(if (space.isAvailable) R.color.colorQuiet else R.color.colorNoisy))

        tvDeviceAreaInfo.text = "Device ID: $deviceId | Area: ${space.name}"
    }

    private fun listenForLogs(deviceId: String) {
        logsListener = StudySpaceRepository.listenToDeviceLogs(deviceId,
            onUpdate = { lines ->
                binding.tvDeviceLogsConsole.text =
                    if (lines.isEmpty()) "Waiting for incoming logs from $deviceId..." else lines.joinToString("\n")
            },
            onError = { binding.tvDeviceLogsConsole.text = "Error reading logs: $it" }
        )
    }

    /** Feeds the graph and keeps the big dB number live with the newest reading. */
    private fun listenForHistory(deviceId: String, space: StudySpace) {
        historyListener = StudySpaceRepository.listenToNoiseHistory(deviceId,
            onUpdate = { values ->
                binding.noiseGraph.setData(values, space.threshold)
                values.lastOrNull()?.let { bind(space.copy(noiseLevel = it.toDouble())) }
            },
            onError = { binding.tvGraphCaption.text = "Error reading history: $it" }
        )
    }

    private fun color(res: Int) = ContextCompat.getColor(this, res)

    override fun onDestroy() {
        logsListener?.remove()
        historyListener?.remove()
        super.onDestroy()
    }
}
