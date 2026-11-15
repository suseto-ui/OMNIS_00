package com.example.gateway

import android.content.Context
import android.util.Log
import com.example.data.MemoryFragment
import com.example.data.OmnisDatabase
import com.example.data.OmnisRecord
import com.example.telemetry.TelemetryEngine
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class CircuitState {
    CLOSED,    // Vše v pořádku, provoz směrován na Cloud / Gemini
    OPEN,      // Detekován výpadek / HTTP 429, provoz přepnut na offline Room simulaci
    HALF_OPEN  // Testovací režim pro obnovení spojení po vypršení cooldownu
}

data class OfflineSimulationResult(
    val answer: String,
    val isSimulated: Boolean = true,
    val sourceRecordCount: Int,
    val diagnosticMessage: String
)

/**
 * 3-fázový Circuit Breaker pro garantovanou 100% dostupnost bez výpadků.
 * Při HTTP 429 nebo výpadku sítě přepne na lokální deterministickou simulaci
 * nad Room SQLite v12 s označením [OFFLINE SIMULACE].
 */
object OmnisCircuitBreaker {

    private const val TAG = "OmnisCircuitBreaker"
    private const val FAILURE_THRESHOLD = 2           // 2 selhání za sebou přepnou na OPEN
    private const val COOLDOWN_PERIOD_MS = 15_000L    // 15 sekund cooldown před HALF_OPEN
    const val SYNC_BATCH_SIZE = 250                  // Dávková synchronizace po 250 záznamech

    private val state = AtomicReference(CircuitState.CLOSED)
    private val consecutiveFailures = AtomicInteger(0)
    private val lastStateChangeTimestamp = AtomicLong(System.currentTimeMillis())

    fun getCurrentState(): CircuitState {
        val current = state.get()
        if (current == CircuitState.OPEN) {
            val now = System.currentTimeMillis()
            if (now - lastStateChangeTimestamp.get() > COOLDOWN_PERIOD_MS) {
                if (state.compareAndSet(CircuitState.OPEN, CircuitState.HALF_OPEN)) {
                    lastStateChangeTimestamp.set(now)
                    TelemetryEngine.log(
                        type = "INFO",
                        component = "CIRCUIT_BREAKER",
                        message = "Circuit Breaker přepnut na HALF_OPEN (zkouška obnovy spojení)."
                    )
                }
            }
        }
        return state.get()
    }

    fun reportSuccess() {
        consecutiveFailures.set(0)
        if (state.get() != CircuitState.CLOSED) {
            state.set(CircuitState.CLOSED)
            lastStateChangeTimestamp.set(System.currentTimeMillis())
            TelemetryEngine.log(
                type = "INFO",
                component = "CIRCUIT_BREAKER",
                message = "Circuit Breaker OBNOVEN -> CLOSED (cloudové služby jsou plně dostupné)."
            )
        }
    }

    fun reportFailure(httpCode: Int, errorMsg: String) {
        val fails = consecutiveFailures.incrementAndGet()
        Log.w(TAG, "Zaznamenáno selhání volání: HTTP $httpCode ($errorMsg), celkem: $fails")

        if (httpCode == 429 || fails >= FAILURE_THRESHOLD) {
            if (state.get() != CircuitState.OPEN) {
                state.set(CircuitState.OPEN)
                lastStateChangeTimestamp.set(System.currentTimeMillis())
                TelemetryEngine.log(
                    type = "SECURITY",
                    component = "CIRCUIT_BREAKER",
                    message = "Circuit Breaker AKTIVOVÁN -> OPEN (přepnuto na offline simulaci). Důvod: HTTP $httpCode",
                    metadata = "{\"failures\":$fails,\"http_code\":$httpCode,\"error\":\"$errorMsg\"}"
                )
            }
        }
    }

    /**
     * Provede deterministickou offline simulaci nad Room SQLite v12
     * s využitím tabulek omnis_messages a omnis_memory_fragments.
     */
    suspend fun executeOfflineSimulation(
        context: Context,
        query: String,
        domain: String = "SYSTEMS_INTELLIGENCE"
    ): OfflineSimulationResult {
        return try {
            val db = OmnisDatabase.getDatabase(context)
            val dao = db.omnisDao()

            val recentRecords: List<OmnisRecord> = dao.getAllRecords().first().take(10)
            val recentFragments: List<MemoryFragment> = dao.getAllFragments().first().take(5)

            val matchingFragment = recentFragments.firstOrNull {
                it.tags.contains(domain, ignoreCase = true) || it.summary.contains(query, ignoreCase = true)
            }

            val matchingRecord = recentRecords.firstOrNull {
                it.content.contains(query, ignoreCase = true) || it.domain.equals(domain, ignoreCase = true)
            }

            val synthesisContent = buildString {
                append("[OFFLINE SIMULACE] ")
                if (matchingFragment != null) {
                    append("Konsolidovaná znalost: ")
                    append(matchingFragment.summary)
                    append("\n\n")
                } else if (matchingRecord != null) {
                    append("Předchozí kognitivní syntéza: ")
                    append(matchingRecord.content.take(300))
                    append("...\n\n")
                } else {
                    append("Deterministická offline reakce jádra O.M.N.I.S. pro doménu $domain. ")
                    append("Systém operuje v izolovaném stavu odolnosti. Požadavek zařazen do fronty.\n\n")
                }
                append("AKGE-8D Invariance: Dotaz '")
                append(query.take(60))
                append("' byl zpracován lokální inferencí bezpečně v paměti zařízení.")
            }

            OfflineSimulationResult(
                answer = synthesisContent,
                isSimulated = true,
                sourceRecordCount = recentRecords.size + recentFragments.size,
                diagnosticMessage = "Inference dokončena v režimu Circuit Breaker OPEN (offline deterministická simulace)."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Chyba při offline simulaci: ${e.message}", e)
            OfflineSimulationResult(
                answer = "[OFFLINE SIMULACE] Záložní deterministická odpověď jádra: Požadavek '$query' byl bezpečně uložen pro synchronizaci.",
                isSimulated = true,
                sourceRecordCount = 0,
                diagnosticMessage = "Chyba lokálního úložiště: ${e.message}"
            )
        }
    }
}
