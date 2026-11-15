package com.example.telemetry

import android.util.Log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TelemetryEngine {
    private val _events = MutableStateFlow<List<com.example.data.OmnisTelemetry>>(emptyList())
    val events: StateFlow<List<com.example.data.OmnisTelemetry>> = _events.asStateFlow()

    fun log(type: String, component: String, message: String, metadata: String = "") {
        try {
            Log.i("Telemetry", "[$type] [$component] $message ($metadata)")
        } catch (e: Throwable) {
            println("Telemetry: [$type] [$component] $message ($metadata)")
        }
        val newLog = com.example.data.OmnisTelemetry(
            type = type,
            component = component,
            message = message,
            metadata = metadata
        )
        _events.value = (_events.value + newLog).takeLast(100)
    }
}
