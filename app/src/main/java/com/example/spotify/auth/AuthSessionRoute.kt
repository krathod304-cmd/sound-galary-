package com.example.spotify.auth

sealed interface AuthSessionRoute {
    data class GoHome(
        val profile: UserProfile?,
        val notice: String? = null
    ) : AuthSessionRoute

    data class CompleteRegistration(
        val draft: RegistrationDraft,
        val notice: String? = null
    ) : AuthSessionRoute
}
