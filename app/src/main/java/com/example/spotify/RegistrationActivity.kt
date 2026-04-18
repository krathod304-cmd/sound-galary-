package com.example.spotify

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.spotify.auth.AuthProviderType
import com.example.spotify.auth.AuthRepository
import com.example.spotify.auth.IntentKeys
import com.example.spotify.databinding.ActivityRegistrationBinding
import com.google.android.material.snackbar.Snackbar

class RegistrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegistrationBinding
    private val repository = AuthRepository()

    private val provider by lazy(LazyThreadSafetyMode.NONE) {
        AuthProviderType.fromWireName(intent.getStringExtra(IntentKeys.EXTRA_PROVIDER))
    }

    private val prefillName by lazy(LazyThreadSafetyMode.NONE) {
        intent.getStringExtra(IntentKeys.EXTRA_NAME).orEmpty()
    }

    private val prefillEmail by lazy(LazyThreadSafetyMode.NONE) {
        intent.getStringExtra(IntentKeys.EXTRA_EMAIL).orEmpty()
    }

    private val prefillPhone by lazy(LazyThreadSafetyMode.NONE) {
        intent.getStringExtra(IntentKeys.EXTRA_PHONE).orEmpty()
    }

    private val pendingNotice by lazy(LazyThreadSafetyMode.NONE) {
        intent.getStringExtra(IntentKeys.EXTRA_NOTICE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configureUi()
        binding.createProfileButton.setOnClickListener {
            createProfile()
        }
    }

    private fun configureUi() {
        binding.linkNoticeText.isVisible = !pendingNotice.isNullOrBlank()
        binding.linkNoticeText.text = pendingNotice

        when (provider) {
            AuthProviderType.PHONE -> {
                binding.providerChipText.text = getString(R.string.registration_provider_phone)
                binding.registrationTitleText.text = getString(R.string.registration_title_phone)
                binding.registrationSubtitleText.text = getString(R.string.registration_subtitle_phone)
                binding.verifiedPhoneCard.isVisible = true
                binding.verifiedPhoneValueText.text = prefillPhone
                binding.nameFieldGroup.isVisible = false
                binding.phoneFieldGroup.isVisible = false
                binding.emailEditText.setText(prefillEmail)
            }

            AuthProviderType.GOOGLE -> {
                binding.providerChipText.text = getString(R.string.registration_provider_google)
                binding.registrationTitleText.text = getString(R.string.registration_title_google)
                binding.registrationSubtitleText.text = getString(R.string.registration_subtitle_google)
                binding.nameFieldGroup.isVisible = true
                binding.phoneFieldGroup.isVisible = true
                binding.nameEditText.setText(prefillName)
                binding.emailEditText.setText(prefillEmail)
                binding.phoneProfileEditText.setText(prefillPhone)
                binding.emailInputLayout.helperText = getString(R.string.registration_google_email_locked)
                binding.emailEditText.isFocusable = false
                binding.emailEditText.isFocusableInTouchMode = false
                binding.emailEditText.isClickable = false
                binding.emailEditText.isCursorVisible = false
            }
        }
    }

    private fun createProfile() {
        clearErrors()

        val username = binding.usernameEditText.text?.toString()?.trim().orEmpty()
        if (username.length < 3) {
            binding.usernameInputLayout.error = getString(R.string.error_username_required)
            return
        }

        val email = when (provider) {
            AuthProviderType.GOOGLE -> prefillEmail
            AuthProviderType.PHONE -> binding.emailEditText.text?.toString()?.trim().orEmpty()
        }
        if (email.isBlank()) {
            binding.emailInputLayout.error = getString(R.string.error_email_required)
            return
        }

        val name = when (provider) {
            AuthProviderType.GOOGLE -> binding.nameEditText.text?.toString()?.trim().orEmpty()
            AuthProviderType.PHONE -> username
        }
        if (provider == AuthProviderType.GOOGLE && name.isBlank()) {
            binding.nameInputLayout.error = getString(R.string.error_name_required)
            return
        }

        val phone = when (provider) {
            AuthProviderType.GOOGLE -> binding.phoneProfileEditText.text?.toString()?.trim().orEmpty()
            AuthProviderType.PHONE -> prefillPhone
        }
        if (provider == AuthProviderType.GOOGLE && phone.length < 10) {
            binding.phoneProfileInputLayout.error = getString(R.string.error_google_phone_required)
            return
        }

        val currentUser = repository.currentUser()
        if (currentUser == null) {
            showMessage(getString(R.string.error_no_session))
            openLogin()
            return
        }

        setLoading(true)
        repository.createUserProfile(
            user = currentUser,
            provider = provider,
            name = name,
            username = username,
            email = email,
            phone = phone,
            onComplete = {
                setLoading(false)
                Toast.makeText(this, R.string.message_profile_saved, Toast.LENGTH_SHORT).show()
                openHome(pendingNotice)
            },
            onError = { message ->
                setLoading(false)
                showMessage(message)
            }
        )
    }

    private fun clearErrors() {
        binding.nameInputLayout.error = null
        binding.emailInputLayout.error = null
        binding.phoneProfileInputLayout.error = null
        binding.usernameInputLayout.error = null
    }

    private fun setLoading(isLoading: Boolean) {
        binding.loadingView.root.isVisible = isLoading
        binding.createProfileButton.isEnabled = !isLoading
    }

    private fun openHome(notice: String?) {
        startActivity(
            Intent(this, HomeActivity::class.java).apply {
                putExtra(IntentKeys.EXTRA_NOTICE, notice)
            }
        )
        finishAffinity()
    }

    private fun openLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finishAffinity()
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}
