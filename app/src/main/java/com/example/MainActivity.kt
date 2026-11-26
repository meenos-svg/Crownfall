package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.CrownfallMainShell
import com.example.ui.theme.CrownfallTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Catch any uncaught background/Firestore errors so the app displays them instead of closing
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stackTrace = throwable.stackTraceToString()
            runOnUiThread {
                setContent {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color(0xFF121212))
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Crash Log:\n\n$stackTrace",
                            color = androidx.compose.ui.graphics.Color(0xFFFF6B6B)
                        )
                    }
                }
            }
            if (stackTrace.contains("Native") || throwable is OutOfMemoryError) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            CrownfallTheme {
                CrownfallRoot()
            }
        }
    }
}

@Composable
private fun CrownfallRoot() {
    var isSignedIn by remember { mutableStateOf(true) }

    if (isSignedIn) {
        CrownfallMainShell(
            currentUserId = "local-player-1",
            initialDisplayName = "Tempora",
            onSignedOut = {
                isSignedIn = false
            }
        )
    } else {
        CrownfallMainShell(
            currentUserId = "local-player-1",
            initialDisplayName = "Tempora",
            onSignedOut = {}
        )
    }
}
