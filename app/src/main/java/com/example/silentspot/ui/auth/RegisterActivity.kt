package com.example.silentspot.ui.auth

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.silentspot.R
import com.example.silentspot.data.repository.AuthRepository
import com.example.silentspot.databinding.ActivityRegisterBinding
import com.example.silentspot.ui.dashboard.DashboardActivity
import com.example.silentspot.utils.isValidEmail
import com.example.silentspot.utils.navigateTo
import com.example.silentspot.utils.textOf
import com.example.silentspot.utils.toast
import com.example.silentspot.utils.visibleIf

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRegister.setOnClickListener { attemptRegister() }
        binding.tvRegisterLoginLink.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }
    }

    private fun attemptRegister() = with(binding) {
        val firstName = etRegisterFirstName.textOf()
        val middleName = etRegisterMiddleName.textOf()
        val lastName = etRegisterLastName.textOf()
        val email = etRegisterEmail.textOf()
        val password = etRegisterPassword.textOf(trim = false)
        val confirm = etRegisterConfirmPassword.textOf(trim = false)
        val required = getString(R.string.error_field_required)

        tilRegisterFirstName.error = if (firstName.isEmpty()) required else null
        tilRegisterLastName.error = if (lastName.isEmpty()) required else null
        tilRegisterEmail.error = when {
            email.isEmpty() -> required
            !email.isValidEmail() -> getString(R.string.error_invalid_email)
            else -> null
        }
        tilRegisterPassword.error = when {
            password.isEmpty() -> required
            password.length < 6 -> getString(R.string.error_password_too_short)
            else -> null
        }
        tilRegisterConfirmPassword.error = when {
            confirm.isEmpty() -> required
            confirm != password -> getString(R.string.error_passwords_no_match)
            else -> null
        }

        val fields = listOf(tilRegisterFirstName, tilRegisterLastName, tilRegisterEmail, tilRegisterPassword, tilRegisterConfirmPassword)
        if (fields.any { it.error != null }) return@with

        setLoading(true)
        AuthRepository.register(this@RegisterActivity, firstName, middleName, lastName, email, password,
            onSuccess = {
                toast(getString(R.string.msg_register_success))
                navigateTo(DashboardActivity::class.java)
            },
            onError = {
                setLoading(false)
                tilRegisterEmail.error = it
            }
        )
    }

    private fun setLoading(loading: Boolean) = with(binding) {
        layoutRegisterLoading.visibleIf(loading)
        listOf(
            btnRegister, etRegisterFirstName, etRegisterMiddleName, etRegisterLastName,
            etRegisterEmail, etRegisterPassword, etRegisterConfirmPassword
        ).forEach { it.isEnabled = !loading }
    }
}
