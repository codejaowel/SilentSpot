package com.example.silentspot.utils

object Constants {

    const val COLLECTION_USERS = "users"
    const val COLLECTION_STUDY_SPACES = "studySpaces"

    const val QUIET_MAX_DB = 40.0
    const val MODERATE_MAX_DB = 60.0

    const val STATUS_QUIET = "QUIET"
    const val STATUS_MODERATE = "MODERATE"
    const val STATUS_NOISY = "NOISY"

    const val ROLE_STUDENT = "student"
    const val ROLE_ADMIN = "admin"

    const val NOTIFICATION_CHANNEL_ID = "silentspot_noise_alerts"
    const val NOTIFICATION_CHANNEL_NAME = "Noise Alerts"
    const val FCM_TOPIC_NOISY_ALERTS = "noisy_alerts"

    const val EXTRA_STUDY_SPACE = "extra_study_space"
    const val SPLASH_DELAY_MS = 1500L
    const val DEFAULT_DEVICE_ID = "esp32_sensor_01"
}
