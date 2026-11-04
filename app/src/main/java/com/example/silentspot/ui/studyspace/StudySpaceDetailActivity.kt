package com.example.silentspot.ui.studyspace

import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.silentspot.R
import com.example.silentspot.data.model.StudySpace
import com.example.silentspot.data.repository.StudySpaceRepository
import com.example.silentspot.databinding.ActivityStudySpaceDetailBinding
import com.example.silentspot.utils.Constants
import com.example.silentspot.utils.NoiseLevelUtils
import com.example.silentspot.utils.NoiseLevelUtils.applyStatus
import com.example.silentspot.utils.SessionManager
import com.example.silentspot.utils.toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ListenerRegistration

class StudySpaceDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudySpaceDetailBinding
    private var logsListener: ListenerRegistration? = null
    private var historyListener: ListenerRegistration? = null
    private var currentSpace: StudySpace? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUser = SessionManager(this).getUser()
        if (currentUser == null || !currentUser.isAdmin) {
            toast("Access denied: Space details and hardware logs are restricted to Administrators.")
            finish()
            return
        }

        binding = ActivityStudySpaceDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarDetail)
        binding.toolbarDetail.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val space = readSpace() ?: return finish()
        currentSpace = space
        bind(space)
        val deviceId = space.deviceId.ifEmpty { Constants.DEFAULT_DEVICE_ID }
        listenForLogs(deviceId)
        listenForHistory(deviceId, space)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_space_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.action_delete_space) {
            confirmDeleteSpace()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    private fun confirmDeleteSpace() {
        val space = currentSpace ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Study Space?")
            .setMessage("Are you sure you want to permanently delete '${space.name}' (${space.location}) and its sensor mappings?")
            .setPositiveButton("Delete") { _, _ ->
                StudySpaceRepository.deleteStudySpace(space.id,
                    onSuccess = {
                        toast("Deleted study space '${space.name}'")
                        finish()
                    },
                    onError = { err ->
                        toast("Failed to delete space: $err")
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
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
