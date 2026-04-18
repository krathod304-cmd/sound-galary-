package com.example.spotify.auth

enum class AuthProviderType(val wireName: String) {
    PHONE("phone"),
    GOOGLE("google");

    companion object {
        fun fromWireName(value: String?): AuthProviderType {
            return entries.firstOrNull { it.wireName == value } ?: PHONE
        }
    }
}
