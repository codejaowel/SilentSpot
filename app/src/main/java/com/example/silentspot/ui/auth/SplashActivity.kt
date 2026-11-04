package com.example.silentspot.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.silentspot.data.repository.AuthRepository
import com.example.silentspot.databinding.ActivitySplashBinding
import com.example.silentspot.ui.dashboard.DashboardActivity
import com.example.silentspot.utils.Constants

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.layoutSplashContent.animate().alpha(1f).translationY(0f).setDuration(600).setStartDelay(150)
        binding.progressSplash.animate().alpha(1f).setDuration(400).setStartDelay(500)

        binding.root.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed

            val next = runCatching {
                if (AuthRepository.isLoggedIn(this)) DashboardActivity::class.java else LoginActivity::class.java
            }.getOrElse { error ->
                Log.e("SplashActivity", "Failed to check session state", error)
                LoginActivity::class.java
            }

            startActivity(Intent(this, next))
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, Constants.SPLASH_DELAY_MS)
    }
}
