package com.example.spotify.auth

object PhoneNumberNormalizer {
    private const val INDIA_COUNTRY_CODE = "91"

    fun normalize(rawPhone: String): String? {
        val trimmed = rawPhone.trim()
        if (trimmed.isBlank()) {
            return null
        }

        val digits = trimmed.filter { it.isDigit() }
        if (digits.isBlank()) {
            return null
        }

        return when {
            trimmed.startsWith("+") && digits.length in 10..15 -> "+$digits"
            digits.length == 10 -> "+$INDIA_COUNTRY_CODE$digits"
            digits.length == 11 && digits.startsWith("0") -> "+$INDIA_COUNTRY_CODE${digits.drop(1)}"
            digits.length in 11..15 -> "+$digits"
            else -> null
        }
    }
}
