package com.example.spotify.auth

data class RegistrationDraft(
    val provider: AuthProviderType,
    val uid: String,
    val name: String,
    val email: String,
    val phone: String
)
