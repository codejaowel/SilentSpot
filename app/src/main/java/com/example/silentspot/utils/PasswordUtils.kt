package com.example.silentspot.utils

import java.security.MessageDigest

object PasswordUtils {

    fun hash(password: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(password.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun verify(raw: String, stored: String): Boolean =
        raw.isNotEmpty() && stored.isNotEmpty() &&
            (hash(raw).equals(stored, ignoreCase = true) || raw == stored)
}
