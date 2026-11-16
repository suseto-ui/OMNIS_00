package com.example.api

import android.app.ActivityManager
import android.content.Context
import com.example.gateway.AkgeInterceptor
import com.example.gateway.OmnisCircuitBreaker
import com.example.telemetry.TelemetryEngine
import com.example.ui.UserExperienceMode
import kotlinx.coroutines.withTimeoutOrNull

enum class HybridRouteMode {
    LOCAL_FAST_PATH,
    CLOUD_DELEGATED,
    OFFLINE_FALLBACK
}

data class HybridDeviceCapability(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val meetsMinRamThreshold: Boolean,
    val isLowRamDevice: Boolean
)

data class HybridOrchestrationOutcome(
    val routeMode: HybridRouteMode,
    val answer: String,
    val latencyMs: Long,
    val isFallbackTriggered: Boolean,
    val diagnosticNote: String
)

/**
 * Play-Policy-compliant Hybrid Inference Orchestrator.
 * - Zero dynamic code loading (no .so/.dex/.jar downloads or execution).
 * - Enforces >= 4 GB RAM hardware gate for fast-path local cache/orchestration.
 * - Enforces strict 1500 ms latency budget before safely delegating to Cloud Gemini or Room offline fallback.
 */
object HybridInferenceOrchestrator {

    const val MIN_RAM_BYTES: Long = 4L * 1024L * 1024L * 1024L // 4 GB
    const val FAST_PATH_BUDGET_MS: Long = 1500L

    @Volatile
    var isFastPathFeatureEnabled: Boolean = true

    @Volatile
    internal var deviceCapabilityProviderOverride: ((Context) -> HybridDeviceCapability)? = null

    fun inspectDeviceCapability(context: Context): HybridDeviceCapability {
        deviceCapabilityProviderOverride?.let { return it(context) }

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        if (am != null) {
            am.getMemoryInfo(memInfo)
        }
        val total = memInfo.totalMem.coerceAtLeast(0L)
        val avail = memInfo.availMem.coerceAtLeast(0L)
        val lowRam = am?.isLowRamDevice ?: (total in 1 until MIN_RAM_BYTES)

        return HybridDeviceCapability(
            totalRamBytes = total,
            availableRamBytes = avail,
            meetsMinRamThreshold = total >= MIN_RAM_BYTES && !lowRam,
            isLowRamDevice = lowRam
        )
    }

    suspend fun dispatchHybridInference(
        context: Context,
        prompt: String,
        domain: String = "SYSTEMS_INTELLIGENCE",
        userMode: UserExperienceMode = UserExperienceMode.STANDARD,
        localExecutionBlock: (suspend () -> String?)? = null
    ): HybridOrchestrationOutcome {
        val startMs = System.currentTimeMillis()
        val capability = inspectDeviceCapability(context)

        // Gate 1: Fast-Path Local / Cache Evaluation within 1500ms budget if RAM >= 4 GB
        if (isFastPathFeatureEnabled && capability.meetsMinRamThreshold) {
            val fastResult = withTimeoutOrNull(FAST_PATH_BUDGET_MS) {
                val intercept = AkgeInterceptor.interceptPrompt(
                    context = context,
                    prompt = prompt,
                    domain = domain,
                    userMode = userMode
                )
                if (!intercept.isProceedAllowed) {
                    return@withTimeoutOrNull "EXPLICIT GROUNDED REFUSAL: ${intercept.diagnosticNote}"
                }
                if (intercept.isCacheHit && !intercept.cachedAnswer.isNullOrBlank()) {
                    return@withTimeoutOrNull intercept.cachedAnswer
                }
                localExecutionBlock?.invoke()
            }

            if (!fastResult.isNullOrBlank()) {
                val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                TelemetryEngine.log(
                    type = "INFO",
                    component = "HYBRID_ORCHESTRATOR",
                    message = "Lokální fast-path odbaven za ${elapsed}ms (RAM=${capability.totalRamBytes / (1024 * 1024)}MB)."
                )
                return HybridOrchestrationOutcome(
                    routeMode = HybridRouteMode.LOCAL_FAST_PATH,
                    answer = fastResult,
                    latencyMs = elapsed,
                    isFallbackTriggered = false,
                    diagnosticNote = "Hardware-gated fast path completed in ${elapsed}ms."
                )
            }
        }

        // Gate 2: Budget exceeded or hardware < 4 GB RAM -> Cloud delegation or Room Offline Fallback
        val elapsedBeforeFallback = System.currentTimeMillis() - startMs
        val intercept = AkgeInterceptor.interceptPrompt(
            context = context,
            prompt = prompt,
            domain = domain,
            userMode = userMode
        )

        return if (!intercept.isProceedAllowed) {
            HybridOrchestrationOutcome(
                routeMode = HybridRouteMode.OFFLINE_FALLBACK,
                answer = "EXPLICIT GROUNDED REFUSAL: ${intercept.diagnosticNote}",
                latencyMs = System.currentTimeMillis() - startMs,
                isFallbackTriggered = true,
                diagnosticNote = intercept.diagnosticNote
            )
        } else if (intercept.executionMode == "OFFLINE_SIMULATION" && !intercept.cachedAnswer.isNullOrBlank()) {
            HybridOrchestrationOutcome(
                routeMode = HybridRouteMode.OFFLINE_FALLBACK,
                answer = intercept.cachedAnswer,
                latencyMs = System.currentTimeMillis() - startMs,
                isFallbackTriggered = true,
                diagnosticNote = intercept.diagnosticNote
            )
        } else {
            val offline = OmnisCircuitBreaker.executeOfflineSimulation(context, prompt, domain)
            val reason = if (!capability.meetsMinRamThreshold) {
                "Hardware gate (< 4 GB RAM) delegated safely."
            } else {
                "Fast-path exceeded ${FAST_PATH_BUDGET_MS}ms budget (${elapsedBeforeFallback}ms); cloud/fallback delegation active."
            }
            HybridOrchestrationOutcome(
                routeMode = HybridRouteMode.CLOUD_DELEGATED,
                answer = offline.answer,
                latencyMs = System.currentTimeMillis() - startMs,
                isFallbackTriggered = true,
                diagnosticNote = reason
            )
        }
    }
}
