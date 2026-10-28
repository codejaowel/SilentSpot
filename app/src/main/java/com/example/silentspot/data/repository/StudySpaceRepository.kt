package com.example.silentspot.data.repository

import com.example.silentspot.data.model.StudySpace
import com.example.silentspot.utils.Constants
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

object StudySpaceRepository {

    private val db get() = FirebaseFirestore.getInstance()

    /** Live list of study spaces, sorted quietest first. Caller must remove() the registration. */
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

    /** Live tail of the latest ESP32 device logs, formatted as console lines. */
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

    /** Live dB history from the ESP32 logs (MAX4466), oldest first, for the graph. */
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
