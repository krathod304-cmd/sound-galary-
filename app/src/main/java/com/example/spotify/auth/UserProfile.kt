package com.example.spotify.auth

import com.google.firebase.Timestamp

data class UserProfile(
    val uid: String,
    val name: String,
    val username: String,
    val email: String?,
    val phone: String?,
    val authProviders: List<String>,
    val createdAt: Timestamp?
)
