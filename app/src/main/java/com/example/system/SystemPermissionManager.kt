package com.example.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

data class PermissionItemStatus(
    val permissionKey: String,
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val isRequired: Boolean = true,
    val category: String = "SYSTEM"
)

object SystemPermissionManager {

    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getPermissionsOverview(context: Context): List<PermissionItemStatus> {
        val list = mutableListOf<PermissionItemStatus>()

        // 1. Internet & Network State (Install-time)
        list.add(
            PermissionItemStatus(
                permissionKey = Manifest.permission.INTERNET,
                title = "Síťový přístup O.M.N.I.S.",
                description = "Povolení pro bezpečné šifrované spojené s AI Studio a Firestore cloudem.",
                isGranted = true,
                isRequired = true,
                category = "KONEKTIVITA"
            )
        )

        list.add(
            PermissionItemStatus(
                permissionKey = Manifest.permission.ACCESS_NETWORK_STATE,
                title = "Sledování stavu sítě",
                description = "Detekce dostupnosti Wi-Fi/LTE pro optimalizaci RAG mezipaměti.",
                isGranted = isPermissionGranted(context, Manifest.permission.ACCESS_NETWORK_STATE),
                isRequired = true,
                category = "KONEKTIVITA"
            )
        )

        // 2. Camera / OCR (Runtime)
        list.add(
            PermissionItemStatus(
                permissionKey = Manifest.permission.CAMERA,
                title = "Fotoaparát & OCR Analýza",
                description = "Extrakce textu a dokumentů pro kognitivní analýzu O.M.N.I.S.",
                isGranted = isPermissionGranted(context, Manifest.permission.CAMERA),
                isRequired = false,
                category = "KOGNITIVNÍ VSTUP"
            )
        )

        // 3. Post Notifications (Runtime - Android 13+)
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
        list.add(
            PermissionItemStatus(
                permissionKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else "android.permission.POST_NOTIFICATIONS",
                title = "Systémové Notifikace & Varování",
                description = "Upozornění při detekci anomálií a dokončení výpočtu v pozadí.",
                isGranted = notifGranted,
                isRequired = false,
                category = "NOTIFIKACE"
            )
        )

        // 4. Vibration / Telemetry Feedback
        list.add(
            PermissionItemStatus(
                permissionKey = Manifest.permission.VIBRATE,
                title = "Haptická Odevzdanost",
                description = "Vibrace při potvrditelných akcích a vykonání příkazů.",
                isGranted = isPermissionGranted(context, Manifest.permission.VIBRATE),
                isRequired = false,
                category = "HAPTIKA"
            )
        )

        return list
    }

    fun getBatteryOptimizationStatus(context: Context): String {
        return "Optimalizace baterie: Aktivní (Standardní režim napájení)"
    }
}
