package com.example.silentspot.utils

import android.content.Context
import com.example.silentspot.data.model.User

class SessionManager(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("silentspot_user_session", Context.MODE_PRIVATE)

    fun save(user: User) {
        prefs.edit()
            .putString("user_uid", user.uid)
            .putString("first_name", user.firstName)
            .putString("middle_name", user.middleName)
            .putString("last_name", user.lastName)
            .putString("full_name", user.fullName)
            .putString("email", user.email)
            .putString("role", user.role)
            .putBoolean("is_logged_in", true)
            .apply()
    }

    fun getUser(): User? {
        if (!isLoggedIn()) return null
        return User(
            uid = get("user_uid"),
            firstName = get("first_name"),
            middleName = get("middle_name"),
            lastName = get("last_name"),
            fullName = get("full_name"),
            email = get("email"),
            role = get("role").ifEmpty { Constants.ROLE_STUDENT }
        )
    }

    fun userId(): String = get("user_uid")

    fun isLoggedIn(): Boolean = prefs.getBoolean("is_logged_in", false) && userId().isNotEmpty()

    fun clear() = prefs.edit().clear().apply()

    private fun get(key: String) = prefs.getString(key, null).orEmpty()
}
