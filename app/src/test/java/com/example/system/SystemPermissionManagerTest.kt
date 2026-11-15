package com.example.system

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SystemPermissionManagerTest {

    @Test
    fun testPermissionsOverviewList() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val overview = SystemPermissionManager.getPermissionsOverview(context)

        assertNotNull(overview)
        assertTrue("Přehled má obsahovat alespoň 4 systémové položky", overview.size >= 4)

        val internetItem = overview.find { it.permissionKey == android.Manifest.permission.INTERNET }
        assertNotNull("Položka sítě INTERNET musí existovat", internetItem)
        assertTrue("Sítové oprávnění je povinné", internetItem!!.isRequired)
    }

    @Test
    fun testBatteryStatusString() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val status = SystemPermissionManager.getBatteryOptimizationStatus(context)

        assertNotNull(status)
        assertTrue("Stav baterie obsahuje popisek", status.contains("baterie"))
    }
}
