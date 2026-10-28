package com.example.silentspot.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.Toast

fun View.visibleIf(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}

fun Context.toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

/** Starts [destination] as a fresh task and finishes the current activity. */
fun Activity.navigateTo(destination: Class<*>) {
    startActivity(Intent(this, destination).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    finish()
}

fun EditText.textOf(trim: Boolean = true): String = text?.toString()?.let { if (trim) it.trim() else it }.orEmpty()

fun String.isValidEmail(): Boolean = Patterns.EMAIL_ADDRESS.matcher(this).matches()
