package com.example.silentspot.data.repository

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.silentspot.data.model.User
import com.example.silentspot.utils.Constants
import com.example.silentspot.utils.PasswordUtils
import com.example.silentspot.utils.SessionManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

object AuthRepository {

    private const val TAG = "AuthRepository"
    private const val NETWORK_TIMEOUT_MS = 12_000L
    private val mainHandler = Handler(Looper.getMainLooper())

    private val users get() = FirebaseFirestore.getInstance().collection(Constants.COLLECTION_USERS)

    fun currentUser(context: Context): User? = SessionManager(context).getUser()

    fun isLoggedIn(context: Context): Boolean = SessionManager(context).isLoggedIn()

    fun signOut(context: Context) = SessionManager(context).clear()

    fun signIn(
        context: Context,
        email: String,
        password: String,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        var completed = false
        val timeoutRunnable = Runnable {
            if (!completed) {
                completed = true
                onError("Connection timed out. Please check your internet connection and try again.")
            }
        }
        mainHandler.postDelayed(timeoutRunnable, NETWORK_TIMEOUT_MS)

        users.whereEqualTo("email", email).get()
            .addOnSuccessListener { result ->
                if (completed) return@addOnSuccessListener
                completed = true
                mainHandler.removeCallbacks(timeoutRunnable)

                val user = result.documents
                    .firstOrNull { PasswordUtils.verify(password, it.getString("password").orEmpty()) }
                    ?.let { it.toObject(User::class.java)?.copy(uid = it.id) }

                if (user == null) {
                    onError("Invalid email or password. Please try again.")
                } else {
                    SessionManager(context).save(user)
                    onSuccess(user)
                }
            }
            .addOnFailureListener {
                if (completed) return@addOnFailureListener
                completed = true
                mainHandler.removeCallbacks(timeoutRunnable)
                onError(errorMessage(it))
            }
    }

    fun register(
        context: Context,
        firstName: String,
        middleName: String,
        lastName: String,
        email: String,
        password: String,
        role: String = if (email.contains("admin", ignoreCase = true)) Constants.ROLE_ADMIN else Constants.ROLE_STUDENT,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        var completed = false
        val timeoutRunnable = Runnable {
            if (!completed) {
                completed = true
                onError("Connection timed out. Please check your internet connection and try again.")
            }
        }
        mainHandler.postDelayed(timeoutRunnable, NETWORK_TIMEOUT_MS)

        users.whereEqualTo("email", email).get()
            .addOnSuccessListener { existing ->
                if (completed) return@addOnSuccessListener

                if (!existing.isEmpty) {
                    completed = true
                    mainHandler.removeCallbacks(timeoutRunnable)
                    onError("This email address is already registered.")
                    return@addOnSuccessListener
                }

                val doc = users.document()
                val user = User(
                    uid = doc.id,
                    firstName = firstName,
                    middleName = middleName,
                    lastName = lastName,
                    fullName = listOf(firstName, middleName, lastName).filter { it.isNotBlank() }.joinToString(" "),
                    email = email,
                    password = PasswordUtils.hash(password),
                    role = role,
                    createdAt = Timestamp.now()
                )

                doc.set(user)
                    .addOnSuccessListener {
                        if (completed) return@addOnSuccessListener
                        completed = true
                        mainHandler.removeCallbacks(timeoutRunnable)
                        SessionManager(context).save(user)
                        onSuccess(user)
                    }
                    .addOnFailureListener {
                        if (completed) return@addOnFailureListener
                        completed = true
                        mainHandler.removeCallbacks(timeoutRunnable)
                        onError(errorMessage(it))
                    }
            }
            .addOnFailureListener {
                if (completed) return@addOnFailureListener
                completed = true
                mainHandler.removeCallbacks(timeoutRunnable)
                onError(errorMessage(it))
            }
    }

    fun fetchProfile(context: Context, onResult: (User?) -> Unit) {
        val session = SessionManager(context)
        val cached = session.getUser() ?: return onResult(null)

        users.document(cached.uid).get()
            .addOnSuccessListener { doc ->
                val fresh = doc.toObject(User::class.java)?.copy(uid = doc.id)
                fresh?.let(session::save)
                onResult(fresh ?: cached)
            }
            .addOnFailureListener { onResult(cached) }
    }

    private fun errorMessage(e: Exception): String {
        Log.e(TAG, "Firestore error", e)
        val msg = e.message.orEmpty()
        return when {
            msg.contains("PERMISSION_DENIED", true) ->
                "Firestore Permission Denied: Please update Security Rules in Firebase Console."
            msg.contains("UNAVAILABLE", true) ->
                "Firestore Unavailable: Please check your internet connection."
            msg.contains("NOT_FOUND", true) ->
                "Firestore Database not found: Please create the database in Firebase Console."
            else -> e.localizedMessage ?: "Database connection error."
        }
    }
}
