package com.example.spotify.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    fun currentUser(): FirebaseUser? = auth.currentUser

    fun signOut() {
        auth.signOut()
    }

    fun resolveCurrentSession(
        onResult: (AuthSessionRoute) -> Unit,
        onError: (String) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            onError("No signed-in user found.")
            return
        }

        if (user.providerData.none { it.providerId == GoogleAuthProvider.PROVIDER_ID }) {
            signOut()
            onError("Google sign-in is required for this app.")
            return
        }

        resolveExistingUser(user, onResult, onError)
    }

    fun resolvePhoneLogin(
        user: FirebaseUser,
        onResult: (AuthSessionRoute) -> Unit,
        onError: (String) -> Unit
    ) {
        resolveExistingUser(user, onResult, onError)
    }

    fun resolveGoogleLogin(
        user: FirebaseUser,
        onResult: (AuthSessionRoute) -> Unit,
        onError: (String) -> Unit
    ) {
        findUserByUid(
            uid = user.uid,
            onSuccess = { existingProfile ->
                if (existingProfile != null) {
                    onResult(AuthSessionRoute.GoHome(existingProfile))
                    return@findUserByUid
                }

                val email = user.email.orEmpty().trim()
                if (email.isBlank()) {
                    onResult(AuthSessionRoute.CompleteRegistration(buildDraft(user)))
                    return@findUserByUid
                }

                findUserByEmail(
                    email = email,
                    onSuccess = { emailProfile ->
                        val notice = if (emailProfile != null && emailProfile.uid != user.uid) {
                            DUPLICATE_EMAIL_NOTICE
                        } else {
                            null
                        }
                        onResult(AuthSessionRoute.CompleteRegistration(buildDraft(user), notice))
                    },
                    onError = onError
                )
            },
            onError = onError
        )
    }

    fun createUserProfile(
        user: FirebaseUser,
        provider: AuthProviderType,
        name: String,
        username: String,
        email: String,
        phone: String,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        val profileName = when (provider) {
            AuthProviderType.GOOGLE -> name.ifBlank { username }
            AuthProviderType.PHONE -> username
        }.trim()

        val profileData = hashMapOf(
            "uid" to user.uid,
            "name" to profileName,
            "username" to username.trim(),
            "email" to email.trim(),
            "phone" to phone.trim(),
            "auth_providers" to listOf(provider.wireName),
            "created_at" to FieldValue.serverTimestamp(),
            "role" to "user"
        )

        firestore.collection(USERS_COLLECTION)
            .document(user.uid)
            .set(profileData)
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener { error -> onError(mapError(error)) }
    }

    private fun resolveExistingUser(
        user: FirebaseUser,
        onResult: (AuthSessionRoute) -> Unit,
        onError: (String) -> Unit
    ) {
        findUserByUid(
            uid = user.uid,
            onSuccess = { existingProfile ->
                if (existingProfile != null) {
                    onResult(AuthSessionRoute.GoHome(existingProfile))
                    return@findUserByUid
                }

                val phone = user.phoneNumber.orEmpty().trim()
                if (phone.isBlank()) {
                    onResult(AuthSessionRoute.CompleteRegistration(buildDraft(user)))
                    return@findUserByUid
                }

                findUserByPhone(
                    phone = phone,
                    onSuccess = { phoneProfile ->
                        if (phoneProfile != null) {
                            val notice = if (phoneProfile.uid != user.uid) PHONE_MATCH_NOTICE else null
                            onResult(AuthSessionRoute.GoHome(phoneProfile, notice))
                        } else {
                            onResult(AuthSessionRoute.CompleteRegistration(buildDraft(user)))
                        }
                    },
                    onError = onError
                )
            },
            onError = onError
        )
    }

    private fun buildDraft(user: FirebaseUser): RegistrationDraft {
        val provider = detectProvider(user)
        return RegistrationDraft(
            provider = provider,
            uid = user.uid,
            name = user.displayName.orEmpty(),
            email = user.email.orEmpty(),
            phone = user.phoneNumber.orEmpty()
        )
    }

    private fun detectProvider(user: FirebaseUser): AuthProviderType {
        return when {
            user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } -> AuthProviderType.GOOGLE
            else -> AuthProviderType.GOOGLE
        }
    }

    private fun findUserByUid(
        uid: String,
        onSuccess: (UserProfile?) -> Unit,
        onError: (String) -> Unit
    ) {
        firestore.collection(USERS_COLLECTION)
            .document(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                onSuccess(snapshot.takeIf { it.exists() }?.toUserProfile())
            }
            .addOnFailureListener { error -> onError(mapError(error)) }
    }

    private fun findUserByPhone(
        phone: String,
        onSuccess: (UserProfile?) -> Unit,
        onError: (String) -> Unit
    ) {
        firestore.collection(USERS_COLLECTION)
            .whereEqualTo("phone", phone)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshots -> onSuccess(snapshots.firstUser()) }
            .addOnFailureListener { error -> onError(mapError(error)) }
    }

    private fun findUserByEmail(
        email: String,
        onSuccess: (UserProfile?) -> Unit,
        onError: (String) -> Unit
    ) {
        firestore.collection(USERS_COLLECTION)
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshots -> onSuccess(snapshots.firstUser()) }
            .addOnFailureListener { error -> onError(mapError(error)) }
    }

    private fun QuerySnapshot.firstUser(): UserProfile? {
        return documents.firstOrNull()?.toUserProfile()
    }

    private fun DocumentSnapshot.toUserProfile(): UserProfile {
        return UserProfile(
            uid = getString("uid").orEmpty(),
            name = getString("name").orEmpty(),
            username = getString("username").orEmpty(),
            email = getString("email"),
            phone = getString("phone"),
            authProviders = (get("auth_providers") as? List<*>)
                ?.filterIsInstance<String>()
                .orEmpty(),
            createdAt = getTimestamp("created_at")
        )
    }

    private fun mapError(error: Exception): String {
        return when (error) {
            is FirebaseNetworkException -> "Network issue. Check your connection and try again."
            is FirebaseAuthInvalidCredentialsException -> "The code or credential is invalid. Please try again."
            is FirebaseAuthInvalidUserException -> "This account is no longer available."
            else -> error.localizedMessage ?: "Something went wrong. Please try again."
        }
    }

    private companion object {
        const val USERS_COLLECTION = "users"
        const val PHONE_MATCH_NOTICE =
            "A profile with this phone number already exists. The account stays separate for now until linking is added."
        const val DUPLICATE_EMAIL_NOTICE =
            "This email already exists in another account. Login is allowed and can be linked later."
    }
}
