package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.BuildConfig
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class GoogleAccountInfo(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String? = null
)

/**
 * Google Sign-In Manager utilizing Jetpack CredentialManager without Firebase Auth SDK.
 * Adheres to zero-trust invariants: single option per GetCredentialRequest,
 * explicit cancellation handling, and Sovereign Backend JWT exchange.
 */
object GoogleAuthManager {
    private const val TAG = "GoogleAuthManager"

    private val _currentUser = MutableStateFlow<GoogleAccountInfo?>(null)
    val currentUser: StateFlow<GoogleAccountInfo?> = _currentUser.asStateFlow()

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    fun initialize(context: Context) {
        val restored = AuthenticationManager.instance.restoreSession(context.applicationContext)
        if (restored != null) {
            _currentUser.value = GoogleAccountInfo(
                uid = restored.username,
                displayName = restored.username,
                email = if (restored.username.contains("@")) restored.username else null
            )
        }
    }

    /**
     * Silent auto-login attempt on app start.
     */
    fun attemptAutoSignIn(
        context: Context,
        scope: CoroutineScope,
        onSuccess: (GoogleAccountInfo, String) -> Unit = { _, _ -> },
        onFailure: () -> Unit = {}
    ) {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            Log.w(TAG, "default_web_client_id not found: ${e.message}")
            onFailure()
            return
        }

        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch(Dispatchers.Main) {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                    val account = extractAccountInfo(googleCred)
                    _currentUser.value = account
                    onSuccess(account, googleCred.idToken)
                } else {
                    onFailure()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Silent sign-in not available: ${e.message}")
                onFailure()
            }
        }
    }

    /**
     * Interactive Google Sign-In flow triggered by user button tap.
     */
    fun signInWithGoogle(
        activity: Activity,
        scope: CoroutineScope,
        onSuccess: (GoogleAccountInfo, String) -> Unit = { _, _ -> },
        onError: (String) -> Unit = {},
        onCancel: () -> Unit = {}
    ) {
        val clientId = try {
            activity.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            val err = "Konfigurace Google Sign-In chybí (default_web_client_id nenalezen)"
            _authError.value = err
            onError(err)
            return
        }

        _isAuthenticating.value = true
        _authError.value = null

        val credentialManager = CredentialManager.create(activity)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch(Dispatchers.Main) {
            try {
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                    val googleIdToken = googleCred.idToken
                    val account = extractAccountInfo(googleCred)
                    _currentUser.value = account
                    _isAuthenticating.value = false
                    if (BuildConfig.DEBUG) {
                        Log.i(TAG, "Google Credential získán (uid=${account.uid})")
                    }
                    onSuccess(account, googleIdToken)
                } else {
                    _isAuthenticating.value = false
                    val err = "Neočekávaný typ přihlašovacích údajů"
                    _authError.value = err
                    onError(err)
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Uživatel zrušil dialog přihlášení Google: ${e.message}")
                _isAuthenticating.value = false
                onCancel()
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In selhal: ${e.message}", e)
                _isAuthenticating.value = false
                val msg = e.localizedMessage ?: "Přihlášení přes Google selhalo"
                _authError.value = msg
                onError(msg)
            }
        }
    }

    fun clearLocalAccount() {
        _currentUser.value = null
        _authError.value = null
    }

    /**
     * Odhlášení uživatele z CredentialManageru.
     */
    fun signOut(context: Context, scope: CoroutineScope, onComplete: () -> Unit = {}) {
        clearLocalAccount()
        val credentialManager = CredentialManager.create(context)
        scope.launch(Dispatchers.IO) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.e(TAG, "Chyba při mazání relace CredentialManageru: ${e.message}", e)
            } finally {
                scope.launch(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    private fun extractAccountInfo(cred: GoogleIdTokenCredential): GoogleAccountInfo {
        val claims = parseIdTokenClaims(cred.idToken)
        val email = cred.id.takeIf { it.contains("@") }
            ?: claims?.optString("email")?.takeIf { it.isNotBlank() }
        val uid = claims?.optString("sub")?.takeIf { it.isNotBlank() }
            ?: email
            ?: cred.id.ifBlank { "google_user" }
        val name = cred.displayName?.takeIf { it.isNotBlank() }
            ?: claims?.optString("name")?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore("@")
        return GoogleAccountInfo(
            uid = uid,
            displayName = name,
            email = email,
            photoUrl = cred.profilePictureUri?.toString()
        )
    }

    private fun parseIdTokenClaims(idToken: String): JSONObject? {
        return try {
            val parts = idToken.split(".")
            if (parts.size < 2) return null
            val decodedBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            JSONObject(String(decodedBytes, Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }
}
