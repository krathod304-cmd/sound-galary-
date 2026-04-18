package com.example.spotify

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.spotify.auth.AuthRepository
import com.example.spotify.auth.AuthSessionRoute
import com.example.spotify.auth.IntentKeys
import com.example.spotify.auth.PhoneNumberNormalizer
import com.example.spotify.auth.RegistrationDraft
import com.example.spotify.databinding.ActivityPhoneAuthBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class PhoneAuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhoneAuthBinding
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val repository = AuthRepository()

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var lastPhoneNumber: String = ""
    private var autoSendOtpOnOpen: Boolean = false

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            val smsCode = credential.smsCode
            if (!smsCode.isNullOrBlank()) {
                binding.otpEditText.setText(smsCode)
            }
            signInWithPhoneCredential(credential)
        }

        override fun onVerificationFailed(error: FirebaseException) {
            setLoading(false)
            showMessage(mapPhoneError(error))
        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            this@PhoneAuthActivity.verificationId = verificationId
            resendToken = token
            binding.otpContainer.isVisible = true
            if (binding.phoneEntryCard.isVisible) {
                binding.codeSentText.isVisible = true
                binding.codeSentText.text = getString(R.string.phone_status_sent, lastPhoneNumber)
            }
            showOtpEntryMode(lastPhoneNumber, isSending = false)
            setLoading(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhoneAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefilledPhone = intent.getStringExtra(IntentKeys.EXTRA_PHONE_PREFILL)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        autoSendOtpOnOpen = intent.getBooleanExtra(IntentKeys.EXTRA_AUTO_SEND_OTP, false) &&
            !prefilledPhone.isNullOrBlank()

        prefilledPhone?.let { phone ->
            binding.phoneEditText.setText(phone)
            binding.phoneEditText.setSelection(phone.length)
        }

        if (autoSendOtpOnOpen && prefilledPhone != null) {
            showOtpEntryMode(prefilledPhone, isSending = true)
            binding.root.post { requestOtp(forceResend = false) }
        }

        binding.sendOtpButton.setOnClickListener {
            requestOtp(forceResend = false)
        }

        binding.verifyOtpButton.setOnClickListener {
            verifyCode()
        }

        binding.resendCodeButton.setOnClickListener {
            requestOtp(forceResend = true)
        }
    }

    private fun requestOtp(forceResend: Boolean) {
        val rawPhone = binding.phoneEditText.text?.toString().orEmpty()
        val phone = PhoneNumberNormalizer.normalize(rawPhone)
        if (phone == null) {
            binding.phoneInputLayout.error = getString(R.string.error_phone_required)
            return
        }
        binding.phoneInputLayout.error = null
        if (binding.phoneEditText.text?.toString() != phone) {
            binding.phoneEditText.setText(phone)
            binding.phoneEditText.setSelection(phone.length)
        }
        lastPhoneNumber = phone
        if (autoSendOtpOnOpen) {
            showOtpEntryMode(phone, isSending = true)
        }

        setLoading(true)
        val optionsBuilder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)

        if (forceResend && resendToken != null) {
            optionsBuilder.setForceResendingToken(resendToken!!)
        }

        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
    }

    private fun showOtpEntryMode(phone: String, isSending: Boolean) {
        binding.phoneTitleText.text = getString(R.string.phone_code_screen_title)
        binding.phoneSubtitleText.text = getString(R.string.phone_code_screen_subtitle, phone)
        binding.phoneEntryCard.isVisible = false
        binding.otpContainer.isVisible = true
        binding.otpStatusText.isVisible = true
        binding.otpStatusText.text = getString(
            if (isSending) R.string.phone_status_sending else R.string.phone_status_sent,
            phone
        )
    }

    private fun verifyCode() {
        val code = binding.otpEditText.text?.toString()?.trim().orEmpty()
        val localVerificationId = verificationId
        if (code.length != 6 || localVerificationId.isNullOrBlank()) {
            binding.otpInputLayout.error = getString(R.string.error_otp_required)
            return
        }
        binding.otpInputLayout.error = null
        signInWithPhoneCredential(PhoneAuthProvider.getCredential(localVerificationId, code))
    }

    private fun signInWithPhoneCredential(credential: PhoneAuthCredential) {
        setLoading(true)
        auth.signInWithCredential(credential)
            .addOnSuccessListener {
                val user = repository.currentUser()
                if (user == null) {
                    setLoading(false)
                    showMessage(getString(R.string.error_no_session))
                    return@addOnSuccessListener
                }

                repository.resolvePhoneLogin(
                    user = user,
                    onResult = { route ->
                        setLoading(false)
                        when (route) {
                            is AuthSessionRoute.GoHome -> openHome(route.notice)
                            is AuthSessionRoute.CompleteRegistration -> openRegistration(route.draft, route.notice)
                        }
                    },
                    onError = { message ->
                        setLoading(false)
                        showMessage(message)
                    }
                )
            }
            .addOnFailureListener { error ->
                setLoading(false)
                showMessage(error.localizedMessage ?: getString(R.string.error_generic))
            }
    }

    private fun openHome(notice: String?) {
        startActivity(
            Intent(this, HomeActivity::class.java).apply {
                putExtra(IntentKeys.EXTRA_NOTICE, notice)
            }
        )
        finishAffinity()
    }

    private fun openRegistration(draft: RegistrationDraft, notice: String?) {
        startActivity(
            Intent(this, RegistrationActivity::class.java).apply {
                putExtra(IntentKeys.EXTRA_PROVIDER, draft.provider.wireName)
                putExtra(IntentKeys.EXTRA_UID, draft.uid)
                putExtra(IntentKeys.EXTRA_NAME, draft.name)
                putExtra(IntentKeys.EXTRA_EMAIL, draft.email)
                putExtra(IntentKeys.EXTRA_PHONE, draft.phone)
                putExtra(IntentKeys.EXTRA_NOTICE, notice)
            }
        )
        finish()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.loadingView.root.isVisible = isLoading
        binding.phoneEditText.isEnabled = !isLoading
        binding.sendOtpButton.isEnabled = !isLoading
        binding.verifyOtpButton.isEnabled = !isLoading
        binding.resendCodeButton.isEnabled = !isLoading
    }

    private fun mapPhoneError(error: FirebaseException): String {
        val errorCode = error.localizedMessage.orEmpty().uppercase()
        return when (error) {
            is FirebaseTooManyRequestsException -> "Too many OTP requests. Please wait before trying again."
            is FirebaseNetworkException -> "Network issue. Check your connection and retry."
            else -> when {
                "BILLING_NOT_ENABLED" in errorCode -> getString(R.string.error_phone_billing_not_enabled)
                else -> error.localizedMessage ?: getString(R.string.error_generic)
            }
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}
