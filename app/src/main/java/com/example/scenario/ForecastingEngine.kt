package com.example.scenario

import com.example.data.OmnisRecord
import kotlin.math.max
import kotlin.math.min

/**
 * Engine pro výpočet časových projekcí 8D matice
 */
object ForecastingEngine {

    /**
     * Vygeneruje časovou řadu (forecast) na základě aktuálního stavu a sady událostí
     */
    fun project(
        initialState: OmnisRecord,
        events: List<ScenarioEvent>,
        steps: Int = 10
    ): List<ForecastPoint> {
        val forecast = mutableListOf<ForecastPoint>()
        
        // Startovní bod
        var currentSys = initialState.valSys
        var currentEcon = initialState.valEcon
        var currentPsych = initialState.valPsych
        var currentEco = initialState.valEco
        var currentLaw = initialState.valLaw
        var currentSec = initialState.valSec
        var currentPhys = initialState.valPhys
        var currentSoc = initialState.valSoc

        forecast.add(ForecastPoint(0, currentSys, currentEcon, currentPsych, currentEco, currentLaw, currentSec, currentPhys, currentSoc))

        // Výpočet kroků skrze diferenční Markovovu matici 8D vazeb
        for (i in 1..steps) {
            // Každá událost má kumulativní vliv rozložený v čase
            events.forEach { event ->
                val influenceFactor = 1.0f / steps
                currentSys = clamp(currentSys + event.impactSys * influenceFactor)
                currentEcon = clamp(currentEcon + event.impactEcon * influenceFactor)
                currentPsych = clamp(currentPsych + event.impactPsych * influenceFactor)
                currentEco = clamp(currentEco + event.impactEco * influenceFactor)
                currentLaw = clamp(currentLaw + event.impactLaw * influenceFactor)
                currentSec = clamp(currentSec + event.impactSec * influenceFactor)
                currentPhys = clamp(currentPhys + event.impactPhys * influenceFactor)
                currentSoc = clamp(currentSoc + event.impactSoc * influenceFactor)
            }
            
            // Deterministická Markovova difúze mezi svázanými doménami:
            // PHYS -> SYS (+0.005), SYS -> SEC (+0.004), SEC -> LAW (+0.003), ECON -> ECO (-0.002)
            val deltaSys = (currentPhys * 0.005f) - (currentLaw * 0.002f)
            val deltaEcon = (currentSys * 0.004f) + (currentSoc * 0.002f)
            val deltaPsych = (currentSec * 0.003f) + (currentEco * 0.002f)
            val deltaEco = (currentEcon * -0.002f)
            val deltaLaw = (currentSec * 0.004f)
            val deltaSec = (currentSys * 0.003f)
            val deltaPhys = (currentSys * 0.002f)
            val deltaSoc = (currentEcon * 0.003f) + (currentLaw * 0.002f)

            currentSys = clamp(currentSys + deltaSys)
            currentEcon = clamp(currentEcon + deltaEcon)
            currentPsych = clamp(currentPsych + deltaPsych)
            currentEco = clamp(currentEco + deltaEco)
            currentLaw = clamp(currentLaw + deltaLaw)
            currentSec = clamp(currentSec + deltaSec)
            currentPhys = clamp(currentPhys + deltaPhys)
            currentSoc = clamp(currentSoc + deltaSoc)
            
            forecast.add(ForecastPoint(i, currentSys, currentEcon, currentPsych, currentEco, currentLaw, currentSec, currentPhys, currentSoc))
        }

        return forecast
    }

    private fun clamp(value: Float): Float = max(0.0f, min(1.0f, value))
}
