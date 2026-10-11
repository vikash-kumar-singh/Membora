package com.membora.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.membora.app.ui.MemboraApp
import com.membora.app.ui.theme.MemboraTheme

class MainActivity : ComponentActivity() {
    private val incomingEmailLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingEmailLink.value = intent?.dataString
        enableEdgeToEdge()
        setContent {
            MemboraTheme {
                MemboraApp(
                    incomingEmailLink = incomingEmailLink.value,
                    onEmailLinkConsumed = { incomingEmailLink.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingEmailLink.value = intent.dataString
    }
}
