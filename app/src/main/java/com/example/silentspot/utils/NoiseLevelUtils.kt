package com.example.silentspot.utils

import android.text.format.DateUtils
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.silentspot.R
import com.example.silentspot.data.model.StudySpace
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

object NoiseLevelUtils {

    fun classify(db: Double): String = when {
        db <= Constants.QUIET_MAX_DB -> Constants.STATUS_QUIET
        db <= Constants.MODERATE_MAX_DB -> Constants.STATUS_MODERATE
        else -> Constants.STATUS_NOISY
    }

    fun statusColor(status: String): Int = when (status) {
        Constants.STATUS_QUIET -> R.color.colorQuiet
        Constants.STATUS_MODERATE -> R.color.colorModerate
        Constants.STATUS_NOISY -> R.color.colorNoisy
        else -> R.color.colorTextMuted
    }

    private fun statusBackground(status: String): Int = when (status) {
        Constants.STATUS_MODERATE -> R.drawable.bg_status_moderate
        Constants.STATUS_NOISY -> R.drawable.bg_status_noisy
        else -> R.drawable.bg_status_quiet
    }

    fun TextView.applyStatus(db: Double): String {
        val status = classify(db)
        text = status
        setBackgroundResource(statusBackground(status))
        setTextColor(ContextCompat.getColor(context, statusColor(status)))
        return status
    }

    fun formatTimestamp(timestamp: Timestamp?): String {
        val time = timestamp?.toDate()?.time ?: return "Unknown"
        val diff = System.currentTimeMillis() - time
        return when {
            diff < DateUtils.MINUTE_IN_MILLIS -> "Just now"
            diff < DateUtils.HOUR_IN_MILLIS -> "${diff / DateUtils.MINUTE_IN_MILLIS} min ago"
            diff < DateUtils.DAY_IN_MILLIS -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(time)
            else -> SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(time)
        }
    }

    fun findQuietest(spaces: List<StudySpace>): StudySpace? =
        spaces.filter { it.isAvailable }.minByOrNull { it.noiseLevel }

    fun formatDb(db: Double): String =
        if (db % 1.0 == 0.0) db.toLong().toString() else String.format(Locale.getDefault(), "%.1f", db)

    fun progress(db: Double, threshold: Int): Int {
        val max = maxOf(threshold.toDouble(), Constants.MODERATE_MAX_DB) * 1.5
        return (db / max * 100).toInt().coerceIn(0, 100)
    }
}
