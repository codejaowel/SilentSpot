package com.example.silentspot.data.model

import com.google.firebase.Timestamp

data class User(
    val uid: String = "",
    val firstName: String = "",
    val middleName: String = "",
    val lastName: String = "",
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val role: String = "student",
    val createdAt: Timestamp? = null
)
