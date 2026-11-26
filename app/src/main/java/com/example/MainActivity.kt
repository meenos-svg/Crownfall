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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.data.SupabaseProvider
import com.example.ui.AuthScreen
import com.example.ui.CrownfallMainShell
import com.example.ui.theme.CrownGold
import com.example.ui.theme.CrownfallTheme
import com.example.ui.theme.ObsidianVoid
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.serialization.json.JsonPrimitive

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    val auth = SupabaseProvider.client.auth
    val status by auth.sessionStatus.collectAsState()

    when (val s = status) {
        is SessionStatus.Authenticated -> {
            val user = s.session.user
            val userId = user?.id.orEmpty()
            val meta = user?.userMetadata
            val displayName = (meta?.get("full_name") as? JsonPrimitive)?.content
                ?: (meta?.get("name") as? JsonPrimitive)?.content
            key(userId) {
                CrownfallMainShell(
                    currentUserId = userId,
                    initialDisplayName = displayName,
                    onSignedOut = { /* sessionStatus switches to NotAuthenticated by itself */ }
                )
            }
        }

        is SessionStatus.Initializing -> LoadingScreen()

        else -> AuthScreen(
            onAuthSuccess = { /* sessionStatus switches to Authenticated by itself */ }
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = CrownGold)
    }
}
