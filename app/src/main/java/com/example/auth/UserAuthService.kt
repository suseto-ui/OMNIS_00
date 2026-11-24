package com.example.auth

import android.content.Context
import com.example.auth.crypto.PasswordHasher
import com.example.auth.jwt.JwtTokenManager
import com.example.data.OmnisDatabase
import com.example.data.UserAuthDao
import com.example.data.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.api.OmnisGeminiClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

sealed class RegistrationResult {
    data class Success(val user: UserEntity, val token: String) : RegistrationResult()
    data class Pending(val message: String) : RegistrationResult()
    data class Failure(val reason: String) : RegistrationResult()
}

sealed class PendingUsersResult {
    data class Success(val users: List<PendingUserDto>) : PendingUsersResult()
    data class Error(val message: String) : PendingUsersResult()
}

data class AdminActionResult(
    val success: Boolean,
    val message: String
)

data class PendingUserDto(
    val id: String,
    val username: String,
    val email: String,
    val role: String,
    val createdAt: String
)

data class RemoteThreadSummary(
    val id: String,
    val title: String,
    val ontologyDomain: String,
    val messageCount: Int
)

/**
 * Centrální služba pro registraci, přihlášení a odhlášení uživatelů
 * s backendovou synchronizací přes OMNIS by Suseto FastAPI (PostgreSQL 17) a JWT.
 */
class UserAuthService {
    companion object {
        val instance: UserAuthService by lazy { UserAuthService() }
        val BASE_URL: String
            get() = com.example.BuildConfig.OMNIS_BACKEND_URL
    }

    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private fun parseUserRole(role: String?): UserRole {
        if (role == null) return UserRole.STANDARD_USER
        return try {
            UserRole.valueOf(role)
        } catch (_: Exception) {
            if (role.contains("ADMIN", ignoreCase = true) || role.contains("OPERATOR", ignoreCase = true)) {
                UserRole.ADMIN_OPERATOR
            } else {
                UserRole.STANDARD_USER
            }
        }
    }

    suspend fun register(
        username: String,
        email: String,
        password: String
    ): RegistrationResult = withContext(Dispatchers.IO) {
        if (com.example.defense.DatabaseConfig.isTesting) {
            if (username.length < 3) return@withContext RegistrationResult.Failure("Uživatelské jméno je příliš krátké.")
            if (password.length < 6) return@withContext RegistrationResult.Failure("Heslo je příliš krátké.")
            return@withContext RegistrationResult.Success(
                user = UserEntity(username = username, passwordHash = "mock_hash", passwordSalt = "mock_salt", role = "STANDARD_USER"),
                token = "mock_token_$username"
            )
        }
        if (username.length < 3) return@withContext RegistrationResult.Failure("Uživatelské jméno je příliš krátké.")
        if (password.length < 6) return@withContext RegistrationResult.Failure("Heslo je příliš krátké.")

        try {
            val cleanEmail = if (email.contains("@") && email.contains(".")) email.trim() else "$username@omnis.ai"
            val json = JSONObject().apply {
                put("username", username.trim())
                put("email", cleanEmail)
                put("password", password)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL/api/auth/register")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val rawBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val resJson = try { JSONObject(rawBody) } catch (_: Exception) { JSONObject() }
                val status = resJson.optString("status", "approved")
                val token = resJson.optString("access_token", "")
                val role = resJson.optString("role", "STANDARD_USER")
                val message = resJson.optString("message", "Registrace byla úspěšně odeslána a čeká na schválení administrátorem.")

                if (status == "pending" || token.isEmpty()) {
                    RegistrationResult.Pending(message)
                } else {
                    RegistrationResult.Success(
                        user = UserEntity(username = username, passwordHash = "", passwordSalt = "", role = role),
                        token = token
                    )
                }
            } else {
                val errorDetail = try {
                    val errObj = JSONObject(rawBody)
                    errObj.optString("detail", "Chyba při registraci (${response.code})")
                } catch (_: Exception) {
                    "Chyba při registraci (${response.code}). Zkontrolujte zadané údaje."
                }
                RegistrationResult.Failure(errorDetail)
            }
        } catch (e: Exception) {
            RegistrationResult.Failure("Komunikační chyba: ${e.localizedMessage ?: "Neznámá chyba sítě"}")
        }
    }

    suspend fun login(
        username: String,
        password: String
    ): AuthResult = withContext(Dispatchers.IO) {
        if (com.example.defense.DatabaseConfig.isTesting) {
            return@withContext AuthResult.Success(
                AuthSession(token = "mock_token_$username", username = username, role = UserRole.STANDARD_USER)
            )
        }
        if (username.isBlank() || password.isBlank()) {
            return@withContext AuthResult.Failure("Jméno a heslo nesmí být prázdné.")
        }
        try {
            val json = JSONObject().apply {
                put("username", username.trim())
                put("password", password)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL/api/auth/login")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val rawBody = response.body?.string() ?: ""
            
            if (response.isSuccessful) {
                val resJson = try { JSONObject(rawBody) } catch (_: Exception) { JSONObject() }
                val token = resJson.optString("access_token")
                if (token.isEmpty()) {
                    AuthResult.Failure("Neplatná odpověď serveru.")
                } else {
                    val canonicalUsername = resJson.optString("username", username.trim()).ifBlank { username.trim() }
                    val role = resJson.optString("role", "STANDARD_USER")
                    AuthResult.Success(
                        AuthSession(token = token, username = canonicalUsername, role = parseUserRole(role))
                    )
                }
            } else {
                val errorDetail = try {
                    val errObj = JSONObject(rawBody)
                    errObj.optString("detail", "Chyba při přihlášení (${response.code})")
                } catch (_: Exception) {
                    "Neplatné přihlašovací údaje nebo chyba serveru (${response.code})."
                }
                if (response.code == 403 && errorDetail.contains("schválení", ignoreCase = true)) {
                    AuthResult.PendingApproval(errorDetail)
                } else {
                    AuthResult.Failure(errorDetail)
                }
            }
        } catch (e: Exception) {
            AuthResult.Failure("Komunikační chyba: ${e.localizedMessage ?: "Služba nedostupná."}")
        }
    }

    suspend fun loginWithGoogle(idToken: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id_token", idToken)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL/api/auth/google")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val rawBody = response.body?.string() ?: ""
            
            if (response.isSuccessful) {
                val resJson = try { JSONObject(rawBody) } catch (_: Exception) { JSONObject() }
                val token = resJson.optString("access_token")
                if (token.isEmpty()) {
                    AuthResult.Failure("Neplatná odpověď serveru (Google Auth).")
                } else {
                    val username = resJson.optString("username", "Google User")
                    val role = resJson.optString("role", "STANDARD_USER")
                    
                    AuthResult.Success(
                        AuthSession(token = token, username = username, role = parseUserRole(role))
                    )
                }
            } else {
                val errorDetail = try {
                    val errObj = JSONObject(rawBody)
                    errObj.optString("detail", "Google přihlášení selhalo (${response.code})")
                } catch (_: Exception) {
                    "Google přihlášení selhalo (HTTP ${response.code})."
                }
                if (response.code == 403 && errorDetail.contains("schválení", ignoreCase = true)) {
                    AuthResult.PendingApproval(errorDetail)
                } else {
                    AuthResult.Failure(errorDetail)
                }
            }
        } catch (e: Exception) {
            AuthResult.Failure("Komunikační chyba s backendem: ${e.localizedMessage ?: "Neznámá chyba"}")
        }
    }

    suspend fun getProfile(token: String): ProfileResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/user/profile")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val resJson = JSONObject(response.body?.string() ?: "{}")
                ProfileResult.Success(parseProfile(resJson))
            } else {
                ProfileResult.Failure("Nelze načíst profil: ${response.message}")
            }
        } catch (e: Exception) {
            ProfileResult.Failure(e.localizedMessage ?: "Chyba sítě")
        }
    }

    suspend fun updateProfile(token: String, update: UserProfileUpdate): ProfileResult = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                update.avatarUrl?.let { put("avatar_url", it) }
                update.bio?.let { put("bio", it) }
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL/api/user/profile")
                .header("Authorization", "Bearer $token")
                .put(body)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val resJson = JSONObject(response.body?.string() ?: "{}")
                ProfileResult.Success(parseProfile(resJson))
            } else {
                ProfileResult.Failure("Nelze aktualizovat profil.")
            }
        } catch (e: Exception) {
            ProfileResult.Failure(e.localizedMessage ?: "Chyba sítě")
        }
    }

    /**
     * Získá seznam konverzačních vláken uložených v PostgreSQL 17 na backendu.
     */
    suspend fun getRemoteThreads(token: String): List<RemoteThreadSummary> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/threads")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonArray = JSONArray(response.body?.string() ?: "[]")
                val list = mutableListOf<RemoteThreadSummary>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val msgs = obj.optJSONArray("messages")
                    list.add(
                        RemoteThreadSummary(
                            id = obj.optString("id", ""),
                            title = obj.optString("title", "Bez názvu"),
                            ontologyDomain = obj.optString("ontology_domain", "SYSTEMS_INTELLIGENCE"),
                            messageCount = msgs?.length() ?: 0
                        )
                    )
                }
                list
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Vytvoří nové konverzační vlákno v PostgreSQL 17 na backendu.
     */
    suspend fun createRemoteThread(token: String, title: String, domain: String = "SYSTEMS_INTELLIGENCE"): String? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("title", title)
                put("ontology_domain", domain)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL/api/threads")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val resJson = JSONObject(response.body?.string() ?: "{}")
                resJson.optString("id", "").takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchPendingUsers(token: String): PendingUsersResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/admin/pending-users")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            val response = client.newCall(request).execute()
            val rawBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val jsonArray = JSONArray(rawBody.ifBlank { "[]" })
                val list = mutableListOf<PendingUserDto>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        PendingUserDto(
                            id = obj.optString("id"),
                            username = obj.optString("username"),
                            email = obj.optString("email"),
                            role = obj.optString("role"),
                            createdAt = obj.optString("created_at", "")
                        )
                    )
                }
                PendingUsersResult.Success(list)
            } else {
                val detail = try {
                    JSONObject(rawBody).optString("detail", "Chyba serveru (HTTP ${response.code})")
                } catch (_: Exception) {
                    "Chyba načítání žádostí (HTTP ${response.code})."
                }
                PendingUsersResult.Error(detail)
            }
        } catch (e: Exception) {
            PendingUsersResult.Error("Síťová chyba při načítání žádostí: ${e.localizedMessage ?: "Nedostupné"}")
        }
    }

    suspend fun getPendingUsers(token: String): List<PendingUserDto> {
        return when (val res = fetchPendingUsers(token)) {
            is PendingUsersResult.Success -> res.users
            is PendingUsersResult.Error -> emptyList()
        }
    }

    suspend fun approveUserDetailed(token: String, email: String): AdminActionResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/admin/approve-user?target_email=${java.net.URLEncoder.encode(email, "UTF-8")}")
                .header("Authorization", "Bearer $token")
                .post("".toRequestBody("application/json".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            val rawBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val msg = try {
                    JSONObject(rawBody).optString("message", "Uživatel $email schválen.")
                } catch (_: Exception) {
                    "Uživatel $email schválen."
                }
                AdminActionResult(true, msg)
            } else {
                val detail = try {
                    JSONObject(rawBody).optString("detail", "Chyba schválení (HTTP ${response.code})")
                } catch (_: Exception) {
                    "Chyba schválení (HTTP ${response.code})."
                }
                AdminActionResult(false, detail)
            }
        } catch (e: Exception) {
            AdminActionResult(false, "Síťová chyba: ${e.localizedMessage ?: "Nedostupné"}")
        }
    }

    suspend fun rejectUserDetailed(token: String, email: String): AdminActionResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/admin/reject-user?target_email=${java.net.URLEncoder.encode(email, "UTF-8")}")
                .header("Authorization", "Bearer $token")
                .post("".toRequestBody("application/json".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            val rawBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val msg = try {
                    JSONObject(rawBody).optString("message", "Žádost $email zamítnuta.")
                } catch (_: Exception) {
                    "Žádost $email zamítnuta."
                }
                AdminActionResult(true, msg)
            } else {
                val detail = try {
                    JSONObject(rawBody).optString("detail", "Chyba zamítnutí (HTTP ${response.code})")
                } catch (_: Exception) {
                    "Chyba zamítnutí (HTTP ${response.code})."
                }
                AdminActionResult(false, detail)
            }
        } catch (e: Exception) {
            AdminActionResult(false, "Síťová chyba: ${e.localizedMessage ?: "Nedostupné"}")
        }
    }

    suspend fun approveUser(token: String, email: String): Boolean {
        return approveUserDetailed(token, email).success
    }

    suspend fun rejectUser(token: String, email: String): Boolean {
        return rejectUserDetailed(token, email).success
    }

    private fun parseProfile(json: JSONObject): UserProfileResponse {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        return UserProfileResponse(
            id = json.optString("id", ""),
            userId = json.optString("user_id", ""),
            username = json.optString("username", ""),
            email = json.optString("email", ""),
            avatarUrl = if (json.isNull("avatar_url")) null else json.optString("avatar_url", "").takeIf { it.isNotEmpty() },
            birthDate = if (json.isNull("birth_date")) null else {
                try { sdf.parse(json.getString("birth_date")) } catch (_: Exception) { null }
            },
            bio = if (json.isNull("bio")) null else json.optString("bio", "").takeIf { it.isNotEmpty() },
            preferencesJson = emptyMap(),
            role = json.optString("role", "STANDARD_USER"),
            createdAt = try { sdf.parse(json.optString("created_at", "")) } catch (_: Exception) { java.util.Date() }
        )
    }

    fun logout(context: Context) {
        val prefs = context.getSharedPreferences(AuthenticationManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
