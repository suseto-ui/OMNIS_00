package com.example.api

import android.util.Log
import com.example.telemetry.TelemetryEngine
import java.util.concurrent.atomic.AtomicInteger

/**
 * Rotační pool API klíčů pro Google GenAI / Gemini s algoritmem Round-Robin
 * a automatickým přepínáním při detekci HTTP 429 / Rate Limit.
 */
object GeminiKeyPool {

    private const val TAG = "GeminiKeyPool"

    // Podpora pro 3 nezávislé rotační sloty API klíčů
    private val keySlots = arrayOf(
        System.getenv("GEMINI_API_KEY") ?: "",
        System.getenv("GEMINI_API_KEY_SECONDARY") ?: "",
        System.getenv("GEMINI_API_KEY_TERTIARY") ?: ""
    )

    private val currentIndex = AtomicInteger(0)
    private val failureCounts = IntArray(3) { 0 }

    /**
     * Získá aktuálně aktivní API klíč v poolu.
     */
    fun getActiveKey(): String {
        val idx = (currentIndex.get() % keySlots.size + keySlots.size) % keySlots.size
        val key = keySlots[idx]
        return if (key.isNotBlank()) key else keySlots[0]
    }

    /**
     * Provede Round-Robin rotaci na následující klíč.
     */
    fun rotateKey(reason: String = "ROUND_ROBIN"): String {
        val oldIdx = (currentIndex.get() % keySlots.size + keySlots.size) % keySlots.size
        val newIdx = (currentIndex.incrementAndGet() % keySlots.size + keySlots.size) % keySlots.size

        TelemetryEngine.log(
            type = "SECURITY",
            component = "GEMINI_KEY_POOL",
            message = "Rotace API klíče: slot $oldIdx -> slot $newIdx ($reason)",
            metadata = "{\"old_slot\":$oldIdx,\"new_slot\":$newIdx,\"reason\":\"$reason\"}"
        )
        return keySlots[newIdx]
    }

    /**
     * Zaznamená selhání aktuálního klíče (např. HTTP 429) a okamžitě rotuje.
     */
    fun reportKeyFailure(httpCode: Int) {
        val idx = (currentIndex.get() % keySlots.size + keySlots.size) % keySlots.size
        failureCounts[idx]++
        Log.w(TAG, "Slot klíče $idx zaznamenal chybu HTTP $httpCode (celkem: ${failureCounts[idx]})")
        rotateKey("HTTP_$httpCode")
    }

    /**
     * Umožňuje dynamickou registraci klíčů za běhu (např. z O.M.N.I.S. Secure Storage).
     */
    fun registerKey(slotIndex: Int, apiKey: String) {
        if (slotIndex in 0 until keySlots.size && apiKey.isNotBlank()) {
            keySlots[slotIndex] = apiKey.trim()
            failureCounts[slotIndex] = 0
            Log.i(TAG, "Zaregistrován nový API klíč do slotu $slotIndex")
        }
    }

    fun getPoolStatus(): Map<String, Any> {
        val active = (currentIndex.get() % keySlots.size + keySlots.size) % keySlots.size
        return mapOf(
            "activeSlot" to active,
            "totalSlots" to keySlots.size,
            "configuredSlots" to keySlots.count { it.isNotBlank() },
            "failureStats" to failureCounts.toList()
        )
    }
}
