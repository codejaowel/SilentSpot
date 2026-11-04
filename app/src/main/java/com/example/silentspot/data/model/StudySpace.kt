package com.example.silentspot.data.model

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize

@Parcelize
data class StudySpace(
    val id: String = "",
    val name: String = "",
    val location: String = "",
    val noiseLevel: Double = 0.0,
    val threshold: Int = 60,
    val status: String = "QUIET",
    val deviceId: String = "",
    val lastUpdated: Timestamp? = null,
    @get:PropertyName("isAvailable") @set:PropertyName("isAvailable")
    var isAvailable: Boolean = true
) : Parcelable
