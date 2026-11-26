package com.example.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.BuildConfig
import com.example.data.SupabaseProvider
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

private const val TAG = "GoogleAuth"

private val webClientId: String get() = BuildConfig.GOOGLE_WEB_CLIENT_ID

private fun isConfigured(): Boolean =
    webClientId.isNotBlank() && webClientId.endsWith(".apps.googleusercontent.com")

/** Google gets the hashed nonce, Supabase gets the raw one, so the token can't be replayed. */
private fun newNonce(): Pair<String, String> {
    val raw = UUID.randomUUID().toString()
    val hashed = MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray())
        .joinToString("") { "%02x".format(it) }
    return raw to hashed
}

private suspend fun signInToSupabase(
    context: Context,
    credentialManager: CredentialManager,
    request: GetCredentialRequest,
    rawNonce: String
) {
    val credential = credentialManager.getCredential(context, request).credential
    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        val googleToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        SupabaseProvider.client.auth.signInWith(IDToken) {
            idToken = googleToken
            provider = Google
            nonce = rawNonce
        }
    } else {
        throw IllegalStateException("Unexpected sign-in response from Google.")
    }
}

/** Silent sign-in on app start: reuses a saved Supabase session, or an already-approved Google account. */
fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (SupabaseProvider.client.auth.currentSessionOrNull() != null) {
        onAuthSuccess()
        return
    }
    if (!isConfigured()) {
        onUnauthenticated()
        return
    }
    scope.launch {
        try {
            val (raw, hashed) = newNonce()
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(webClientId)
                .setFilterByAuthorizedAccounts(true)
                .setAutoSelectEnabled(true)
                .setNonce(hashed)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            signInToSupabase(context, credentialManager, request, raw)
            onAuthSuccess()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.i(TAG, "No silent sign-in available: ${e.message}")
            onUnauthenticated()
        }
    }
}

/** The "Sign in with Google" button. */
fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    if (!isConfigured()) {
        onAuthError("Google sign-in isn't set up yet (GOOGLE_WEB_CLIENT_ID secret is missing).")
        return
    }
    scope.launch {
        try {
            val (raw, hashed) = newNonce()
            val option = GetSignInWithGoogleOption.Builder(serverClientId = webClientId)
                .setNonce(hashed)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            signInToSupabase(context, credentialManager, request, raw)
            onAuthSuccess()
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            onAuthCancelled()
        } catch (e: RestException) {
            Log.e(TAG, "Supabase rejected Google sign-in", e)
            onAuthError("Server sign-in failed: ${e.error}")
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in failed", e)
            onAuthError(e.message ?: "Google sign-in failed.")
        }
    }
}

fun signOutUser(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        runCatching { SupabaseProvider.client.auth.signOut() }
            .onFailure { Log.w(TAG, "Supabase sign-out failed", it) }
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
            .onFailure { Log.w(TAG, "Clearing Google credential failed", it) }
        onSignOutComplete()
    }
}
