package com.example.silentspot.data.repository

import android.content.Context
import android.util.Log
import com.example.silentspot.data.model.User
import com.example.silentspot.utils.Constants
import com.example.silentspot.utils.PasswordUtils
import com.example.silentspot.utils.SessionManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

object AuthRepository {

    private const val TAG = "AuthRepository"
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
        users.whereEqualTo("email", email).get()
            .addOnSuccessListener { result ->
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
            .addOnFailureListener { onError(errorMessage(it)) }
    }

    fun register(
        context: Context,
        firstName: String,
        middleName: String,
        lastName: String,
        email: String,
        password: String,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        users.whereEqualTo("email", email).get()
            .addOnSuccessListener { existing ->
                if (!existing.isEmpty) {
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
                    role = Constants.ROLE_STUDENT,
                    createdAt = Timestamp.now()
                )

                doc.set(user)
                    .addOnSuccessListener {
                        SessionManager(context).save(user)
                        onSuccess(user)
                    }
                    .addOnFailureListener { onError(errorMessage(it)) }
            }
            .addOnFailureListener { onError(errorMessage(it)) }
    }

    /** Refreshes the profile from Firestore, falling back to the cached session. */
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
