package com.example.silentspot.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.silentspot.R
import com.example.silentspot.data.repository.AuthRepository
import com.example.silentspot.databinding.ActivityLoginBinding
import com.example.silentspot.ui.dashboard.DashboardActivity
import com.example.silentspot.utils.isValidEmail
import com.example.silentspot.utils.navigateTo
import com.example.silentspot.utils.textOf
import com.example.silentspot.utils.visibleIf

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener { attemptLogin() }
        binding.tvLoginRegisterLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }
    }

    private fun attemptLogin() = with(binding) {
        val email = etLoginEmail.textOf()
        val password = etLoginPassword.textOf(trim = false)
        val required = getString(R.string.error_field_required)

        tilLoginEmail.error = when {
            email.isEmpty() -> required
            !email.isValidEmail() -> getString(R.string.error_invalid_email)
            else -> null
        }
        tilLoginPassword.error = if (password.isEmpty()) required else null
        if (tilLoginEmail.error != null || tilLoginPassword.error != null) return@with

        setLoading(true)
        AuthRepository.signIn(this@LoginActivity, email, password,
            onSuccess = { navigateTo(DashboardActivity::class.java) },
            onError = {
                setLoading(false)
                tilLoginPassword.error = it
            }
        )
    }

    private fun setLoading(loading: Boolean) = with(binding) {
        layoutLoginLoading.visibleIf(loading)
        listOf(btnLogin, etLoginEmail, etLoginPassword).forEach { it.isEnabled = !loading }
    }
}
