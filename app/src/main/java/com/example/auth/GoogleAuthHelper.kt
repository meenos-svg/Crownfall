package com.example.auth

import android.content.Context
import androidx.credentials.CredentialManager
import kotlinx.coroutines.CoroutineScope

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    // Bypass Firebase/Google Sign-In while migrating Crownfall to Supabase
    onAuthSuccess()
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    // Let player enter directly into Crownfall for testing
    onAuthSuccess()
}

fun signOutUser(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    onSignOutComplete()
}
