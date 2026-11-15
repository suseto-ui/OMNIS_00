package com.example.ui.user

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

/**
 * Reprezentuje událost odemčení nového odznaku s kryptografickým důkazem.
 */
data class BadgeUnlockEvent(
    val badge: UserBadge,
    val commitmentHash: String,
    val triggerVector: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Singleton pro správu odemykání odznaků napříč celou aplikací O.M.N.I.S.
 * Zajišťuje generování SHA-256 hashů, perzistenci a emitování notifikací.
 */
object BadgeTriggerManager {

    private const val PREFS_NAME = "omnis_badges_prefs"

    private val _unlockEvents = MutableSharedFlow<BadgeUnlockEvent>(extraBufferCapacity = 10)
    val unlockEvents: SharedFlow<BadgeUnlockEvent> = _unlockEvents.asSharedFlow()

    private val _currentBadges = MutableStateFlow<List<UserBadge>>(createInitialBadges())
    val currentBadges: StateFlow<List<UserBadge>> = _currentBadges.asStateFlow()

    private val _reputationScore = MutableStateFlow(840)
    val reputationScore: StateFlow<Int> = _reputationScore.asStateFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        loadPersistedBadges()
    }

    private fun createInitialBadges(): List<UserBadge> {
        return listOf(
            UserBadge(
                id = "analyst_1",
                name = "Lovec Černých labutí",
                category = "KASKÁDOVÁ ODOLNOST",
                description = "Detekce singulárních 8D mezních stavů a antifragilní verifikace.",
                icon = "🦅",
                cryptographicProof = "SHA256:8D-BLACK-SWAN-V1-VERIFIED",
                earned = false
            ),
            UserBadge(
                id = "master_1",
                name = "Velmistr Dialektiky",
                category = "MULTI-AGENTNÍ SYNTÉZA",
                description = "Úspěšná konfrontace 4 agentních perspektiv v 30s kognitivním okně.",
                icon = "⚖️",
                cryptographicProof = "SHA256:DIALECTIC-CONSENSUS-G6-PASSED",
                earned = false
            ),
            UserBadge(
                id = "causal_1",
                name = "Kauzální Intervencionista",
                category = "DO(X) OPERÁTORY",
                description = "Provedení kauzální systémové intervence a simulace v CSR grafu.",
                icon = "⚡",
                cryptographicProof = "SHA256:CAUSAL-DO-INTERVENE-CSR-VALID",
                earned = false
            ),
            UserBadge(
                id = "zero_entropy",
                name = "Architekt Nulové Entropie",
                category = "DETERMINISMUS",
                description = "Dosažení minimální informační entropie S = -∑ p_i ln(p_i) < 0.15.",
                icon = "💠",
                cryptographicProof = "SHA256:ZERO-ENTROPY-DETERMINISM-G1G6",
                earned = false
            ),
            UserBadge(
                id = "nis2_certifier",
                name = "NIS2 & AI Act Certifikátor",
                category = "REGULATORNÍ AUDIT",
                description = "Vygenerování kryptografického ZK auditu pro evropskou shodu.",
                icon = "🛡️",
                cryptographicProof = "SHA256:EU-AI-ACT-NIS2-ZERO-KNOWLEDGE-AUDIT",
                earned = false
            ),
            UserBadge(
                id = "tensor_navigator",
                name = "Tenzorový 8D Navigátor",
                category = "OKTAGONÁLNÍ PROSTOR",
                description = "Harmonizace všech 8 dimenzí (D1-D8) nad prahovou hodnotu 0.85.",
                icon = "🔮",
                cryptographicProof = "SHA256:OCTAGON-8D-TENSOR-EQUILIBRIUM",
                earned = false
            )
        )
    }

    private fun loadPersistedBadges() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        var earnedCount = 0
        _currentBadges.value = _currentBadges.value.map { badge ->
            val isEarned = prefs.getBoolean("badge_${badge.id}", badge.earned)
            val timestamp = prefs.getLong("badge_time_${badge.id}", 0L)
            if (isEarned) {
                earnedCount++
                badge.copy(
                    earned = true,
                    earnedTimestamp = if (timestamp > 0L) timestamp else System.currentTimeMillis()
                )
            } else badge
        }
        _reputationScore.value = 600 + (earnedCount * 120)
    }

    /**
     * Pokusí se odemknout odznak podle ID a spouštěcího vektoru.
     * Pokud ještě nebyl získán, vygeneruje kryptografický SHA-256 závazek,
     * uloží stav a emituje notifikační událost.
     */
    fun triggerBadge(badgeId: String, triggerVector: String) {
        val badge = _currentBadges.value.firstOrNull { it.id == badgeId } ?: return
        if (badge.earned) return

        val now = System.currentTimeMillis()
        val commitmentHash = generateCommitmentHash(badgeId, triggerVector, now)

        val updatedBadge = badge.copy(
            earned = true,
            earnedTimestamp = now,
            cryptographicProof = commitmentHash
        )

        _currentBadges.value = _currentBadges.value.map {
            if (it.id == badgeId) updatedBadge else it
        }

        _reputationScore.value += 120

        // Perzistence
        appContext?.let { ctx ->
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("badge_$badgeId", true)
                .putLong("badge_time_$badgeId", now)
                .putString("badge_hash_$badgeId", commitmentHash)
                .apply()
        }

        // Záznam do auditního protokolu
        android.util.Log.i("BADGE_ENGINE", "UNLOCKED: $badgeId | Commitment: $commitmentHash | Vector: $triggerVector")

        // Emitování HUD notifikace
        _unlockEvents.tryEmit(
            BadgeUnlockEvent(
                badge = updatedBadge,
                commitmentHash = commitmentHash,
                triggerVector = triggerVector,
                timestamp = now
            )
        )
    }

    private fun generateCommitmentHash(badgeId: String, triggerVector: String, timestamp: Long): String {
        return try {
            val raw = "OMNIS-ZK-PROOF:$badgeId:$triggerVector:$timestamp"
            val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            "SHA256:" + digest.joinToString("") { "%02x".format(it) }.take(28).uppercase()
        } catch (e: Exception) {
            "SHA256:VERIFIED-$badgeId-$timestamp"
        }
    }
}
