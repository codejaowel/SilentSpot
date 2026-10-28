package com.example.silentspot.ui.auth

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.silentspot.data.repository.AuthRepository
import com.example.silentspot.databinding.ActivitySplashBinding
import com.example.silentspot.ui.dashboard.DashboardActivity
import com.example.silentspot.utils.Constants
import com.example.silentspot.utils.navigateTo

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.layoutSplashContent.animate().alpha(1f).translationY(0f).setDuration(600).setStartDelay(150)
        binding.progressSplash.animate().alpha(1f).setDuration(400).setStartDelay(500)

        // postDelayed on the view is auto-cancelled if the activity is destroyed.
        binding.root.postDelayed({
            val next = if (AuthRepository.isLoggedIn(this)) DashboardActivity::class.java else LoginActivity::class.java
            navigateTo(next)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, Constants.SPLASH_DELAY_MS)
    }
}
