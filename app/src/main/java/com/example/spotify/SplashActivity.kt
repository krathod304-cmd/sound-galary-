package com.example.spotify

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.spotify.auth.AuthRepository
import com.example.spotify.auth.AuthSessionRoute
import com.example.spotify.auth.IntentKeys
import com.example.spotify.auth.RegistrationDraft
import com.example.spotify.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val repository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.postDelayed(::routeUser, SPLASH_DELAY_MS)
    }

    private fun routeUser() {
        val currentUser = repository.currentUser()
        if (currentUser == null) {
            openLogin()
            return
        }

        repository.resolveCurrentSession(
            onResult = { route ->
                when (route) {
                    is AuthSessionRoute.GoHome -> openHome(route.notice)
                    is AuthSessionRoute.CompleteRegistration -> openRegistration(route.draft, route.notice)
                }
            },
            onError = {
                openLogin()
            }
        )
    }

    private fun openLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun openHome(notice: String?) {
        startActivity(
            Intent(this, HomeActivity::class.java).apply {
                putExtra(IntentKeys.EXTRA_NOTICE, notice)
            }
        )
        finish()
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

    private companion object {
        const val SPLASH_DELAY_MS = 750L
    }
}
