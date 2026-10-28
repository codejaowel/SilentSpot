package com.example.silentspot.ui.dashboard

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.silentspot.R
import com.example.silentspot.adapter.StudySpaceAdapter
import com.example.silentspot.data.model.StudySpace
import com.example.silentspot.data.repository.AuthRepository
import com.example.silentspot.data.repository.StudySpaceRepository
import com.example.silentspot.databinding.ActivityDashboardBinding
import com.example.silentspot.ui.auth.LoginActivity
import com.example.silentspot.ui.studyspace.StudySpaceDetailActivity
import com.example.silentspot.utils.Constants
import com.example.silentspot.utils.NoiseLevelUtils
import com.example.silentspot.utils.navigateTo
import com.example.silentspot.utils.visibleIf
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private var spacesListener: ListenerRegistration? = null

    private val adapter = StudySpaceAdapter { space ->
        startActivity(Intent(this, StudySpaceDetailActivity::class.java).putExtra(Constants.EXTRA_STUDY_SPACE, space))
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarDashboard)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.btnLogoutHeader.setOnClickListener { confirmLogout() }
        binding.toolbarDashboard.setOnMenuItemClickListener {
            (it.itemId == R.id.action_logout).also { isLogout -> if (isLogout) confirmLogout() }
        }

        binding.rvStudySpaces.layoutManager = LinearLayoutManager(this)
        binding.rvStudySpaces.adapter = adapter
        binding.rvStudySpaces.isNestedScrollingEnabled = false

        binding.swipeRefreshDashboard.setColorSchemeResources(R.color.colorPrimary)
        binding.swipeRefreshDashboard.setOnRefreshListener { listenForStudySpaces() }

        loadGreeting()
        listenForStudySpaces()
        FirebaseMessaging.getInstance().subscribeToTopic(Constants.FCM_TOPIC_NOISY_ALERTS)
    }

    private fun loadGreeting() {
        binding.tvDashboardGreeting.text = "Good day,"
        AuthRepository.fetchProfile(this) { user ->
            binding.tvDashboardUserName.text = user?.firstName
                ?.ifBlank { user.fullName.substringBefore(" ") }
                ?.ifBlank { null } ?: "Student"
        }
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

    /** Single place that drives every dashboard view state. */
    private fun render(spaces: List<StudySpace> = emptyList(), loading: Boolean = false) = with(binding) {
        if (!loading) swipeRefreshDashboard.isRefreshing = false
        layoutDashboardLoading.visibleIf(loading)
        layoutDashboardEmpty.visibleIf(!loading && spaces.isEmpty())
        rvStudySpaces.visibleIf(!loading && spaces.isNotEmpty())
        if (loading) return@with

        adapter.submitList(spaces)
        tvStatSpacesCount.text = "${spaces.size} Active"
        tvStatAvgNoise.text = if (spaces.isEmpty()) "-- dB" else "${spaces.map { it.noiseLevel }.average().toInt()} dB"

        val quietest = NoiseLevelUtils.findQuietest(spaces)
        cardRecommendation.visibleIf(quietest != null)
        tvRecommendEmpty.visibleIf(quietest == null)
        if (quietest != null) {
            tvRecommendName.text = quietest.name
            tvRecommendDb.text = "${NoiseLevelUtils.formatDb(quietest.noiseLevel)} dB"
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.title_logout_confirm)
            .setMessage(R.string.msg_logout_confirm)
            .setPositiveButton(R.string.btn_confirm) { _, _ ->
                spacesListener?.remove()
                AuthRepository.signOut(this)
                navigateTo(LoginActivity::class.java)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    override fun onDestroy() {
        spacesListener?.remove()
        super.onDestroy()
    }
}
