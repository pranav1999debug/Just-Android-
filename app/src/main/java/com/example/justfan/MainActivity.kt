package com.example.justfan

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.justfan.ui.JustFanAppRoot
import com.example.justfan.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAuthIntent(intent)
        setContent {
            JustFanAppRoot(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "justfan" && uri.host == "auth") {
            viewModel.handleGoogleOAuthUri(uri)
        }
    }
}
