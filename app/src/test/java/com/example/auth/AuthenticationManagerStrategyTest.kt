package com.example.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuthenticationManagerStrategyTest {

    private lateinit var context: Context
    private lateinit var authManager: AuthenticationManager

    private val testAdminSecret = "unit-test-operator-key-991"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        authManager = AuthenticationManager.instance
        authManager.logout(context)
        System.setProperty("OMNIS_ADMIN_SECRET_HASHES", authManager.hashSecret(testAdminSecret))
    }

    @org.junit.After
    fun tearDown() {
        System.clearProperty("OMNIS_ADMIN_SECRET_HASHES")
        authManager.logout(context)
    }

    @Test
    fun testAdminStrategy_fullPermissionsAndCredentialVerification() {
        val strategy = UserAuthorizationStrategyRegistry.getStrategy(UserRole.ADMIN_OPERATOR)

        assertEquals(UserRole.ADMIN_OPERATOR, strategy.role)
        assertTrue(strategy.canAccessSystemActions())
        assertTrue(strategy.canAccessDevDiagnostic())
        assertTrue(strategy.canModifySystemState())
        assertTrue(strategy.hasPermission(SystemPermission.ACCESS_SYSTEM_ACTIONS))
        assertTrue(strategy.hasPermission(SystemPermission.EXECUTE_DATA_SYNC))
        assertTrue(strategy.hasPermission(SystemPermission.MODIFY_SYSTEM_STATE))

        // Ověření platného testovacího hashovaného klíče
        assertTrue(strategy.validateCredentials(testAdminSecret, authManager::hashSecret))

        // Tvrdě zakázané výchozí / neplatné hodnoty
        assertFalse(strategy.validateCredentials("omnis2026", authManager::hashSecret))
        assertFalse(strategy.validateCredentials("admin", authManager::hashSecret))
        assertFalse(strategy.validateCredentials("wrong_password", authManager::hashSecret))
        assertFalse(strategy.validateCredentials("", authManager::hashSecret))

        // Fail-closed: bez nakonfigurovaného hashe musí selhat i testovací klíč
        System.clearProperty("OMNIS_ADMIN_SECRET_HASHES")
        assertFalse(strategy.validateCredentials(testAdminSecret, authManager::hashSecret))
    }

    @Test
    fun testStandardUserStrategy_restrictedPermissions() {
        val strategy = UserAuthorizationStrategyRegistry.getStrategy(UserRole.STANDARD_USER)

        assertEquals(UserRole.STANDARD_USER, strategy.role)
        assertFalse(strategy.canAccessSystemActions())
        assertFalse(strategy.canAccessDevDiagnostic())
        assertFalse(strategy.canModifySystemState())

        // Povolené standardní čtecí a chatové oprávnění
        assertTrue(strategy.hasPermission(SystemPermission.ACCESS_KNOWLEDGE_BASE))
        assertTrue(strategy.hasPermission(SystemPermission.ACCESS_TELEMETRY))
        assertFalse(strategy.hasPermission(SystemPermission.MODIFY_SYSTEM_STATE))
        assertFalse(strategy.hasPermission(SystemPermission.EXECUTE_DATA_SYNC))

        // Běžný uživatel nevyžaduje tajný klíč
        assertTrue(strategy.validateCredentials("", authManager::hashSecret))
    }

    @Test
    fun testAuditorStrategy_governanceOnlyPermissions() {
        val strategy = AuditorAuthorizationStrategy()

        assertEquals(UserRole.STANDARD_USER, strategy.role)
        assertFalse(strategy.canAccessSystemActions())
        assertTrue(strategy.canAccessDevDiagnostic())
        assertFalse(strategy.canModifySystemState())
        assertTrue(strategy.hasPermission(SystemPermission.AUDIT_GOVERNANCE))
        assertFalse(strategy.hasPermission(SystemPermission.MODIFY_SYSTEM_STATE))
    }

    @Test
    fun testAuthenticationManager_loginSuccessAndSessionPersistence() {
        val result = authManager.login(context, "operator1", testAdminSecret, UserRole.ADMIN_OPERATOR)
        assertTrue(result is AuthResult.Success)

        val success = result as AuthResult.Success
        assertEquals("operator1", success.session.username)
        assertEquals(UserRole.ADMIN_OPERATOR, success.session.role)
        assertTrue(success.session.token.isNotBlank() && (success.session.token.startsWith("omnis_jwt_") || success.session.token.startsWith("eyJ")))

        // Token nesmí být uložen v nešifrovaných SharedPreferences
        val plainPrefs = context.getSharedPreferences(AuthenticationManager.PREFS_NAME, Context.MODE_PRIVATE)
        assertNull(plainPrefs.getString(AuthenticationManager.KEY_TOKEN, null))

        // Obnova session ze šifrovaného úložiště
        val restored = authManager.restoreSession(context)
        assertNotNull(restored)
        assertEquals("operator1", restored!!.username)
        assertEquals(UserRole.ADMIN_OPERATOR, restored.role)
    }

    @Test
    fun testAuthenticationManager_rejectsTamperedTokenOnRestore() {
        val prefs = context.getSharedPreferences(AuthenticationManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(AuthenticationManager.KEY_TOKEN, "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhdHRhY2tlciIsInJvbGUiOiJBRE1JTl9PUEVSQVRPUiJ9.tampered_sig")
            .putString(AuthenticationManager.KEY_USERNAME, "attacker")
            .putString(AuthenticationManager.KEY_ROLE, UserRole.ADMIN_OPERATOR.name)
            .apply()

        assertNull(authManager.restoreSession(context))
    }

    @Test
    fun testAuthenticationManager_loginFailureOnInvalidSecret() {
        val result = authManager.login(context, "operator1", "bad_pass", UserRole.ADMIN_OPERATOR)
        assertTrue(result is AuthResult.Failure)
        val failure = result as AuthResult.Failure
        assertTrue(failure.reason.contains("Neplatné přístupové oprávnění"))
    }

    @Test
    fun testAuthenticationManager_blankUsernameValidation() {
        val result = authManager.login(context, "   ", testAdminSecret, UserRole.ADMIN_OPERATOR)
        assertTrue(result is AuthResult.Failure)
        val failure = result as AuthResult.Failure
        assertTrue(failure.reason.contains("Uživatelské jméno nesmí být prázdné"))
    }

    @Test
    fun testOmnisAuthService_backwardCompatibilityIntegration() {
        OmnisAuthService.logout(context)
        assertFalse(OmnisAuthService.isAuthenticated.value)

        val loggedIn = OmnisAuthService.login(context, "alice", "", UserRole.STANDARD_USER)
        assertTrue(loggedIn)
        assertTrue(OmnisAuthService.isAuthenticated.value)
        assertEquals(UserRole.STANDARD_USER, OmnisAuthService.currentUserRole.value)
    }

    @Test
    fun testAuthenticationManager_preservesValidUnexpiredExternalSession() {
        val backendSignedToken = com.example.auth.jwt.JwtTokenManager.createToken(
            subject = "backend_user",
            role = UserRole.STANDARD_USER.name,
            durationSeconds = 3600,
            key = "remote_backend_signing_secret"
        )
        val externalSession = AuthSession(
            token = backendSignedToken,
            username = "backend_user",
            role = UserRole.STANDARD_USER
        )

        authManager.saveExternalSession(context, externalSession)

        val restored = authManager.restoreSession(context)
        assertNotNull(restored)
        assertEquals("backend_user", restored?.username)
        assertEquals(UserRole.STANDARD_USER, restored?.role)
        assertEquals(backendSignedToken, restored?.token)
    }

    @Test
    fun testAuthenticationManager_rejectsExpiredExternalSession() {
        val expiredBackendToken = com.example.auth.jwt.JwtTokenManager.createToken(
            subject = "backend_user",
            role = UserRole.STANDARD_USER.name,
            durationSeconds = -60,
            key = "remote_backend_signing_secret"
        )
        authManager.saveExternalSession(
            context,
            AuthSession(
                token = expiredBackendToken,
                username = "backend_user",
                role = UserRole.STANDARD_USER
            )
        )

        assertNull(authManager.restoreSession(context))
    }
}
