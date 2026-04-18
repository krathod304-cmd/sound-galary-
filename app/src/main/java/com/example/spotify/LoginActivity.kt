package com.example.spotify

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.spotify.auth.AuthRepository
import com.example.spotify.auth.AuthSessionRoute
import com.example.spotify.auth.IntentKeys
import com.example.spotify.auth.RegistrationDraft
import com.example.spotify.databinding.ActivityLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val repository = AuthRepository()

    private val googleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: run {
            setLoading(false)
            showMessage(getString(R.string.error_generic))
            return@registerForActivityResult
        }

        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            authenticateWithGoogle(account)
        } catch (error: ApiException) {
            setLoading(false)
            showMessage(error.localizedMessage ?: getString(R.string.error_generic))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.googleLoginButton.setOnClickListener {
            launchGoogleSignIn()
        }

        binding.googleConfigNoteText.isVisible = !isGoogleConfigured()
    }

    private fun launchGoogleSignIn() {
        val clientId = resolveGoogleClientId()
        if (clientId.isNullOrBlank()) {
            showMessage(getString(R.string.error_google_not_ready))
            return
        }

        val client = buildGoogleSignInClient(clientId)
        setLoading(true)
        client.signOut().addOnCompleteListener {
            googleLauncher.launch(client.signInIntent)
        }
    }

    private fun authenticateWithGoogle(account: GoogleSignInAccount) {
        val idToken = account.idToken
        if (idToken.isNullOrBlank()) {
            setLoading(false)
            showMessage(getString(R.string.error_generic))
            return
        }

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        FirebaseAuth.getInstance()
            .signInWithCredential(credential)
            .addOnSuccessListener {
                val user = repository.currentUser()
                if (user == null) {
                    setLoading(false)
                    showMessage(getString(R.string.error_no_session))
                    return@addOnSuccessListener
                }

                repository.resolveGoogleLogin(
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

    private fun isGoogleConfigured(): Boolean {
        return !resolveGoogleClientId().isNullOrBlank()
    }

    @SuppressLint("DiscouragedApi")
    private fun resolveGoogleClientId(): String? {
        val generatedResId = resources.getIdentifier(
            "default_web_client_id",
            "string",
            packageName
        )
        if (generatedResId != 0) {
            resources.getString(generatedResId)
                .trim()
                .takeIf { it.isNotBlank() }
                ?.let { return it }
        }

        val fallbackClientId = getString(R.string.firebase_web_client_id).trim()
        return fallbackClientId
            .takeUnless { it.isBlank() || it == "REPLACE_WITH_FIREBASE_WEB_CLIENT_ID" }
    }

    private fun buildGoogleSignInClient(clientId: String): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(this, options)
    }

    private fun setLoading(isLoading: Boolean) {
        binding.loadingView.root.isVisible = isLoading
        binding.googleLoginButton.isEnabled = !isLoading
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}
