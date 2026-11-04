package com.example.silentspot.data.repository

import com.example.silentspot.data.model.StudySpace
import com.example.silentspot.utils.Constants
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

object StudySpaceRepository {

    private val db get() = FirebaseFirestore.getInstance()

    fun listenToStudySpaces(
        onUpdate: (List<StudySpace>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration =
        db.collection(Constants.COLLECTION_STUDY_SPACES).addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError("Could not load study spaces. Please check your connection.")
                return@addSnapshotListener
            }
            val spaces = snapshot?.documents.orEmpty().mapNotNull { doc ->
                runCatching { doc.toObject(StudySpace::class.java)?.copy(id = doc.id) }.getOrNull()
            }
            onUpdate(spaces.sortedBy { it.noiseLevel })
        }

    fun addStudySpace(
        space: StudySpace,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val doc = db.collection(Constants.COLLECTION_STUDY_SPACES).document()
        val data = hashMapOf(
            "name" to space.name.trim(),
            "location" to space.location.trim(),
            "deviceId" to space.deviceId.trim(),
            "noiseLevel" to space.noiseLevel,
            "threshold" to space.threshold,
            "isAvailable" to space.isAvailable,
            "lastUpdated" to (space.lastUpdated ?: Timestamp.now())
        )
        doc.set(data)
            .addOnSuccessListener { onSuccess(doc.id) }
            .addOnFailureListener { onError(it.localizedMessage ?: "Failed to add study space") }
    }

    fun deleteStudySpace(
        spaceId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (spaceId.isBlank()) {
            onError("Invalid study space ID")
            return
        }
        db.collection(Constants.COLLECTION_STUDY_SPACES).document(spaceId).delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.localizedMessage ?: "Failed to delete study space") }
    }

    fun listenToDeviceLogs(
        deviceId: String,
        limit: Long = 8,
        onUpdate: (List<String>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration =
        db.collection("esp32s").document(deviceId).collection("logs")
            .orderBy("logNumber", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) onError(error.localizedMessage.orEmpty())
                else onUpdate(snapshot?.documents.orEmpty().map(::formatLog))
            }

    fun listenToNoiseHistory(
        deviceId: String,
        limit: Long = 30,
        onUpdate: (List<Float>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration =
        db.collection("esp32s").document(deviceId).collection("logs")
            .orderBy("logNumber", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) onError(error.localizedMessage.orEmpty())
                else onUpdate(snapshot?.documents.orEmpty()
                    .mapNotNull { it.getDouble("noiseLevel")?.toFloat() }
                    .reversed())
            }

    private fun formatLog(doc: DocumentSnapshot): String {
        val status = doc.getString("status") ?: "ONLINE"
        val net = doc.getString("connectedNetwork") ?: "WiFi"
        val rssi = doc.getLong("wifiRssi") ?: 0L
        val uptime = doc.getLong("uptimeSeconds") ?: 0L
        val ip = doc.getString("ipAddress") ?: "0.0.0.0"
        val db = doc.getDouble("noiseLevel")?.let { " | %.1f dB".format(it) }.orEmpty()
        return "[${doc.id}] $status$db | $net (${rssi}dBm) | Uptime: ${uptime}s | IP: $ip"
    }
}
