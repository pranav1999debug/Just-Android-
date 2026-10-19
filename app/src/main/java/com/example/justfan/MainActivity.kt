package com.example.justfan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.justfan.data.remote.SupabaseClient
import com.example.justfan.ui.JustFanAppRoot
import com.example.justfan.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            JustFanAppRoot(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null && data.scheme == "justfan" && data.host == "auth-callback") {
            val fragment = data.fragment ?: data.encodedFragment
            if (!fragment.isNullOrBlank()) {
                try {
                    val params = fragment.split("&").associate {
                        val parts = it.split("=", limit = 2)
                        val k = parts.getOrElse(0) { "" }
                        val v = parts.getOrElse(1) { "" }
                        k to Uri.decode(v)
                    }
                    val accessToken = params["access_token"]
                    val refreshToken = params["refresh_token"]
                    if (!accessToken.isNullOrBlank()) {
                        val (jwtUid, jwtEmail, jwtName) = SupabaseClient.extractUserFromJwt(accessToken)
                        val resolvedEmail = (jwtEmail ?: params["email"]).orEmpty()
                        if (resolvedEmail.isNotBlank()) {
                            val resolvedName = (jwtName ?: params["name"] ?: resolvedEmail.substringBefore("@"))
                            viewModel.signInWithGoogle(
                                email = resolvedEmail,
                                name = resolvedName,
                                accessToken = accessToken,
                                refreshToken = refreshToken,
                                userId = jwtUid ?: params["user_id"]
                            )
                            Log.i("MainActivity", "OAuth callback signed in $resolvedEmail")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error parsing auth-callback URI", e)
                }
            }
        }
    }
}
