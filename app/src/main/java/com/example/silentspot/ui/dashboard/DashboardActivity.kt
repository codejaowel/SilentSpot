package com.example.silentspot.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.silentspot.R
import com.example.silentspot.data.model.StudySpace
import com.example.silentspot.data.model.User
import com.example.silentspot.data.repository.AuthRepository
import com.example.silentspot.data.repository.StudySpaceRepository
import com.example.silentspot.databinding.ActivityDashboardBinding
import com.example.silentspot.databinding.DialogAddStudySpaceBinding
import com.example.silentspot.ui.auth.LoginActivity
import com.example.silentspot.ui.studyspace.StudySpaceDetailActivity
import com.example.silentspot.utils.Constants
import com.example.silentspot.utils.NoiseLevelUtils
import com.example.silentspot.utils.NoiseLevelUtils.applyStatus
import com.example.silentspot.utils.navigateTo
import com.example.silentspot.utils.textOf
import com.example.silentspot.utils.toast
import com.example.silentspot.utils.visibleIf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private var spacesListener: ListenerRegistration? = null
    private var historyListener: ListenerRegistration? = null

    private var currentSpaces: List<StudySpace> = emptyList()
    private var selectedSpace: StudySpace? = null
    private var selectedSpaceId: String? = null
    private var currentSpaceNames: List<String> = emptyList()
    private var activeListeningDeviceId: String? = null
    private var isAdmin: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarDashboard)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.btnLogoutHeader.setOnClickListener { confirmLogout() }

        binding.swipeRefreshDashboard.setColorSchemeResources(R.color.colorPrimary)
        binding.swipeRefreshDashboard.setOnRefreshListener { listenForStudySpaces() }

        // Admin Management Actions
        binding.btnAddStudySpace.setOnClickListener { showAddSpaceDialog() }
        binding.btnDeleteSelectedSpace.setOnClickListener { confirmDeleteSelectedSpace() }

        loadGreeting()
        listenForStudySpaces()

        Thread {
            runCatching {
                FirebaseMessaging.getInstance().subscribeToTopic(Constants.FCM_TOPIC_NOISY_ALERTS)
            }.onFailure { Log.w("DashboardActivity", "FCM subscription failed", it) }
        }.start()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_dashboard, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.action_logout) {
            confirmLogout()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    private fun loadGreeting() {
        val cachedUser = AuthRepository.currentUser(this)
        updateUserRole(cachedUser)

        AuthRepository.fetchProfile(this) { user ->
            updateUserRole(user)
        }
    }

    private fun updateUserRole(user: User?) {
        val name = user?.firstName
            ?.ifBlank { user.fullName.substringBefore(" ") }
            ?.ifBlank { null } ?: "Student"

        isAdmin = user?.isAdmin == true

        binding.tvDashboardGreeting.text = if (isAdmin) "Administrator Mode" else "Good day,"
        binding.tvDashboardUserName.text = name
        binding.tvDashboardRoleBadge.visibleIf(isAdmin)

        binding.layoutAdminControls.visibleIf(isAdmin)
        binding.btnViewRoomDetails.visibleIf(isAdmin)
        binding.btnDeleteSelectedSpace.visibleIf(isAdmin && selectedSpace != null)
        binding.tvSelectedRoomDevice.visibleIf(isAdmin)
    }

    private fun listenForStudySpaces() {
        render(loading = true)
        spacesListener?.remove()
        spacesListener = StudySpaceRepository.listenToStudySpaces(
            onUpdate = { render(spaces = it) },
            onError = {
                render()
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
            }
        )
    }

    private fun render(spaces: List<StudySpace> = emptyList(), loading: Boolean = false) = with(binding) {
        if (!loading) swipeRefreshDashboard.isRefreshing = false
        layoutDashboardLoading.visibleIf(loading)
        layoutDashboardEmpty.visibleIf(!loading && spaces.isEmpty())
        layoutCampusOverview.visibleIf(!loading && spaces.isNotEmpty())
        cardLiveGraphMonitor.visibleIf(!loading && spaces.isNotEmpty())
        if (loading) return@with

        currentSpaces = spaces

        // 1. Campus-wide Overview Stats (Placed ABOVE the graph)
        tvStatSpacesCount.text = "${spaces.size} Active"
        val avgDb = if (spaces.isEmpty()) 0.0 else spaces.map { it.noiseLevel }.average()
        tvStatAvgNoise.text = if (spaces.isEmpty()) "-- dB" else "${avgDb.toInt()} dB"

        val quietest = NoiseLevelUtils.findQuietest(spaces)
        cardRecommendation.visibleIf(quietest != null)
        tvRecommendEmpty.visibleIf(quietest == null)
        if (quietest != null) {
            tvRecommendName.text = quietest.name
            tvRecommendDb.text = "${NoiseLevelUtils.formatDb(quietest.noiseLevel)} dB"
            cardRecommendation.setOnClickListener {
                selectRoom(quietest)
                scrollDashboard.smoothScrollTo(0, cardLiveGraphMonitor.top)
            }
        }

        // 2. Setup Dropdown Selector for All Spaces (Placed BELOW the graph)
        if (spaces.isNotEmpty()) {
            val spaceNames = spaces.map { it.name }
            if (currentSpaceNames != spaceNames) {
                currentSpaceNames = spaceNames
                val adapter = ArrayAdapter(this@DashboardActivity, android.R.layout.simple_dropdown_item_1line, spaceNames)
                actvSpaceSelector.setAdapter(adapter)

                actvSpaceSelector.setOnItemClickListener { _, _, position, _ ->
                    val chosen = spaces.getOrNull(position) ?: return@setOnItemClickListener
                    selectRoom(chosen)
                }
            }

            val matchingSpace = spaces.find { it.id == selectedSpaceId }
            if (matchingSpace != null) {
                updateRoomLabels(matchingSpace)
            } else {
                val spaceToSelect = quietest ?: spaces.first()
                selectRoom(spaceToSelect)
            }
        } else {
            selectedSpace = null
            selectedSpaceId = null
            activeListeningDeviceId = null
            historyListener?.remove()
            historyListener = null
            btnDeleteSelectedSpace.visibleIf(false)
        }
    }

    private fun updateRoomLabels(space: StudySpace) = with(binding) {
        selectedSpace = space
        selectedSpaceId = space.id

        if (actvSpaceSelector.text.toString() != space.name) {
            actvSpaceSelector.setText(space.name, false)
        }

        tvSelectedRoomName.text = space.name
        tvSelectedRoomLocation.text = space.location.ifEmpty { "Campus Area" }
        tvSelectedRoomDb.text = "${NoiseLevelUtils.formatDb(space.noiseLevel)} dB"
        tvGraphStatusBadge.applyStatus(space.noiseLevel)

        tvSelectedRoomThreshold.text = "Threshold: ${space.threshold} dB"
        tvSelectedRoomAvailability.text = if (space.isAvailable) "Available" else "Occupied"
        tvSelectedRoomAvailability.setTextColor(
            ContextCompat.getColor(this@DashboardActivity, if (space.isAvailable) R.color.colorQuiet else R.color.colorNoisy)
        )

        val deviceId = space.deviceId.ifEmpty { Constants.DEFAULT_DEVICE_ID }
        tvSelectedRoomDevice.text = "Sensor: $deviceId"

        btnViewRoomDetails.visibleIf(isAdmin)
        btnDeleteSelectedSpace.visibleIf(isAdmin)
        tvSelectedRoomDevice.visibleIf(isAdmin)

        if (isAdmin) {
            btnViewRoomDetails.setOnClickListener {
                startActivity(Intent(this@DashboardActivity, StudySpaceDetailActivity::class.java).putExtra(Constants.EXTRA_STUDY_SPACE, space))
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            }
        } else {
            btnViewRoomDetails.setOnClickListener(null)
        }
    }

    private fun selectRoom(space: StudySpace) = with(binding) {
        updateRoomLabels(space)

        val deviceId = space.deviceId.ifEmpty { Constants.DEFAULT_DEVICE_ID }
        if (activeListeningDeviceId == deviceId && historyListener != null) {
            return@with
        }
        activeListeningDeviceId = deviceId

        historyListener?.remove()
        noiseGraphDashboard.setData(emptyList(), space.threshold)
        tvSelectedRoomGraphCaption.text = "Connecting live stream for ${space.name}..."

        historyListener = StudySpaceRepository.listenToNoiseHistory(deviceId, limit = 30,
            onUpdate = { values ->
                noiseGraphDashboard.setData(values, space.threshold)
                tvSelectedRoomGraphCaption.text = "Live sensor waveform (${values.size} readings • dashed line = threshold limit):"
                if (values.isNotEmpty()) {
                    val latest = values.last().toDouble()
                    tvSelectedRoomDb.text = "${NoiseLevelUtils.formatDb(latest)} dB"
                    tvGraphStatusBadge.applyStatus(latest)
                }
            },
            onError = { err ->
                tvSelectedRoomGraphCaption.text = "Sensor history unavailable: $err"
            }
        )
    }

    private fun showAddSpaceDialog() {
        if (!isAdmin) {
            toast("Administrator privileges required.")
            return
        }

        val dialogBinding = DialogAddStudySpaceBinding.inflate(LayoutInflater.from(this))
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Create Space", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val name = dialogBinding.etSpaceName.textOf()
                val location = dialogBinding.etSpaceLocation.textOf()
                val deviceId = dialogBinding.etDeviceId.textOf().ifEmpty { "esp32_sensor_${System.currentTimeMillis() % 1000}" }
                val isAvailable = dialogBinding.switchAvailable.isChecked

                if (name.isEmpty()) {
                    dialogBinding.tilSpaceName.error = "Space name is required"
                    return@setOnClickListener
                }
                dialogBinding.tilSpaceName.error = null

                val threshold = 60

                val newSpace = StudySpace(
                    name = name,
                    location = location.ifEmpty { "Campus" },
                    deviceId = deviceId,
                    noiseLevel = 30.0,
                    threshold = threshold,
                    isAvailable = isAvailable,
                    lastUpdated = Timestamp.now()
                )

                StudySpaceRepository.addStudySpace(newSpace,
                    onSuccess = { newId ->
                        toast("Study space '$name' created successfully.")
                        selectedSpaceId = newId
                        dialog.dismiss()
                    },
                    onError = { err ->
                        toast("Failed to create space: $err")
                    }
                )
            }
        }

        dialog.show()
    }

    private fun confirmDeleteSelectedSpace() {
        if (!isAdmin) {
            toast("Administrator privileges required.")
            return
        }

        val space = selectedSpace ?: run {
            toast("No study space selected to delete.")
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Study Space?")
            .setMessage("Are you sure you want to permanently delete '${space.name}' (${space.location}) and remove its ESP32 sensor mapping (${space.deviceId})?")
            .setPositiveButton("Delete") { _, _ ->
                StudySpaceRepository.deleteStudySpace(space.id,
                    onSuccess = {
                        toast("Deleted '${space.name}'")
                        selectedSpaceId = null
                        selectedSpace = null
                    },
                    onError = { err ->
                        toast("Failed to delete space: $err")
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.title_logout_confirm)
            .setMessage(R.string.msg_logout_confirm)
            .setPositiveButton(R.string.btn_confirm) { _, _ ->
                historyListener?.remove()
                spacesListener?.remove()
                AuthRepository.signOut(this)
                navigateTo(LoginActivity::class.java)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    override fun onDestroy() {
        historyListener?.remove()
        spacesListener?.remove()
        activeListeningDeviceId = null
        super.onDestroy()
    }
}
