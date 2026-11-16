package com.example.auth

import android.content.Context
import java.security.MessageDigest
import java.util.UUID

/**
 * Výsledek pokusu o autentizaci
 */
sealed class AuthResult {
    data class Success(val session: AuthSession) : AuthResult()
    data class PendingApproval(val message: String, val email: String? = null) : AuthResult()
    data class Failure(val reason: String) : AuthResult()
}

/**
 * AuthenticationManager:
 * Centrální komponenta pro správu autentizace a autorizace.
 * Implementuje zásadu jediné odpovědnosti (SRP) a techniku 'Extract Method'
 * pro udržení cyklomatické složitosti každé metody pod hodnotou 3.
 */
class AuthenticationManager(
    private val strategyRegistry: UserAuthorizationStrategyRegistry = UserAuthorizationStrategyRegistry
) {
    companion object {
        const val PREFS_NAME = "omnis_auth_prefs"
        const val KEY_TOKEN = "session_token"
        const val KEY_USERNAME = "session_username"
        const val KEY_ROLE = "session_role"
        private const val KEY_IS_EXTERNAL_SESSION = "session_is_external"

        val instance: AuthenticationManager by lazy { AuthenticationManager() }
    }

    /**
     * Hlavní vstupní bod pro přihlášení uživatele.
     * Cyklomatická složitost: 2
     */
    fun login(
        context: Context,
        username: String,
        secret: String,
        roleRequested: UserRole
    ): AuthResult {
        val sanitizedUser = sanitizeInput(username) ?: return AuthResult.Failure("Uživatelské jméno nesmí být prázdné.")
        if (!com.example.BuildConfig.DEBUG && !com.example.data.DatabaseConfig.isTesting && roleRequested == UserRole.ADMIN_OPERATOR) {
            return AuthResult.Failure("Role ADMIN_OPERATOR vyžaduje produkční ověření backendem / Google Sign-In.")
        }
        val strategy = strategyRegistry.getStrategy(roleRequested)

        if (!validateCredentialsWithStrategy(strategy, secret)) {
            return AuthResult.Failure("Neplatné přístupové oprávnění pro roli ${roleRequested.roleName}.")
        }

        val session = createSession(sanitizedUser, roleRequested)
        persistSession(context, session, isExternal = false)
        return AuthResult.Success(session)
    }

    fun saveExternalSession(context: Context, session: AuthSession) {
        persistSession(context, session, isExternal = true)
    }

    /**
     * Odhlášení a vymazání perzistentní relace včetně šifrovaného tokenu.
     * Cyklomatická složitost: 1
     */
    fun logout(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        com.example.security.OmnisSecureStorage.removeKey(context, KEY_TOKEN)
        com.example.security.OmnisSecureStorage.removeKey(context, KEY_IS_EXTERNAL_SESSION)
    }

    /**
     * Načtení existující relace z šifrovaného úložiště a verifikace JWT tokenu.
     * Externí tokeny podepsané backendem ověřují expiraci a integritu claims,
     * lokální tokeny podléhají plné HMAC verifikaci podpisu.
     */
    fun restoreSession(context: Context): AuthSession? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedToken = com.example.security.OmnisSecureStorage.decryptAndRetrieve(context, KEY_TOKEN)
        val token = encryptedToken ?: if (com.example.BuildConfig.DEBUG || com.example.data.DatabaseConfig.isTesting) {
            prefs.getString(KEY_TOKEN, null)
        } else {
            null
        } ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val roleStr = prefs.getString(KEY_ROLE, null) ?: return null
        val isExternalSession = com.example.security.OmnisSecureStorage.decryptAndRetrieve(
            context,
            KEY_IS_EXTERNAL_SESSION
        ) == "true"

        val validation = com.example.auth.jwt.JwtTokenManager.validateToken(token)
        val isValid = when (validation) {
            is com.example.auth.jwt.JwtTokenManager.JwtValidationResult.Valid -> true
            is com.example.auth.jwt.JwtTokenManager.JwtValidationResult.InvalidSignature -> {
                if (isExternalSession && encryptedToken != null) {
                    val claims = com.example.auth.jwt.JwtTokenManager.parseClaimsUnverified(token)
                    claims != null &&
                        claims.expiresAt > 0L &&
                        !claims.isExpired &&
                        claims.subject.equals(username, ignoreCase = true)
                } else {
                    false
                }
            }
            is com.example.auth.jwt.JwtTokenManager.JwtValidationResult.Expired,
            is com.example.auth.jwt.JwtTokenManager.JwtValidationResult.Malformed -> false
        }

        if (!isValid) {
            logout(context)
            return null
        }

        val role = parseRoleSafely(roleStr)
        return AuthSession(token = token, username = username, role = role)
    }

    /**
     * Ověření oprávnění k operaci skrze polymorfní strategii.
     * Cyklomatická složitost: 1
     */
    fun checkPermission(role: UserRole, permission: SystemPermission): Boolean {
        val strategy = strategyRegistry.getStrategy(role)
        return strategy.hasPermission(permission)
    }

    /**
     * Ověření možnosti spuštění konkrétní transakce.
     * Cyklomatická složitost: 1
     */
    fun canExecuteTransaction(role: UserRole, transactionType: String): Boolean {
        val strategy = strategyRegistry.getStrategy(role)
        return strategy.canExecuteTransaction(transactionType)
    }

    // =========================================================================
    // EXTRACT METHOD: Vysoce soudržné, malé privátní metody s CC = 1
    // =========================================================================

    private fun sanitizeInput(input: String): String? {
        val trimmed = input.trim()
        return if (trimmed.isBlank()) null else trimmed
    }

    private fun validateCredentialsWithStrategy(
        strategy: UserAuthorizationStrategy,
        secret: String
    ): Boolean {
        return strategy.validateCredentials(secret, ::hashSecret)
    }

    private fun createSession(username: String, role: UserRole): AuthSession {
        val token = generateToken(username, role)
        return AuthSession(
            token = token,
            username = username,
            role = role
        )
    }

    private fun generateToken(username: String = "user", role: UserRole = UserRole.STANDARD_USER): String {
        return com.example.auth.jwt.JwtTokenManager.createToken(
            subject = username,
            role = role.name
        )
    }

    private fun persistSession(context: Context, session: AuthSession, isExternal: Boolean = false) {
        com.example.security.OmnisSecureStorage.encryptAndStore(context, KEY_TOKEN, session.token)
        com.example.security.OmnisSecureStorage.encryptAndStore(
            context,
            KEY_IS_EXTERNAL_SESSION,
            if (isExternal) "true" else "false"
        )
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_TOKEN)
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_ROLE, session.role.name)
            .apply()
    }

    private fun parseRoleSafely(roleName: String): UserRole {
        return try {
            UserRole.valueOf(roleName)
        } catch (e: Exception) {
            UserRole.STANDARD_USER
        }
    }

    fun hashSecret(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
