package com.example.silentspot.data.model

import com.example.silentspot.utils.Constants
import com.google.firebase.Timestamp

data class User(
    val uid: String = "",
    val firstName: String = "",
    val middleName: String = "",
    val lastName: String = "",
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val role: String = Constants.ROLE_STUDENT,
    val createdAt: Timestamp? = null
) {
    val isAdmin: Boolean get() = role.equals(Constants.ROLE_ADMIN, ignoreCase = true)
}
