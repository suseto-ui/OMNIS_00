package com.example.monitoring

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.OmnisDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserActivityLoggerTest {

    private lateinit var context: Context
    private lateinit var logger: UserActivityLogger
    private lateinit var db: OmnisDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = OmnisDatabase.getInMemoryDatabase(context)
        logger = UserActivityLogger(db.userActivityLogDao(), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
    }

    @Test
    fun `test logging user activity persists entry into database`() = runBlocking {
        logger.logActivity(
            userId = "admin",
            action = "TEST_ACTION",
            details = "Detail testovací aktivity",
            severity = "INFO"
        )
        kotlinx.coroutines.delay(100)

        val logs = logger.getAllLogs().first { it.isNotEmpty() }
        assertEquals(1, logs.size)
        val entry = logs[0]
        assertEquals("admin", entry.userId)
        assertEquals("TEST_ACTION", entry.action)
        assertEquals("Detail testovací aktivity", entry.details)
        assertEquals("INFO", entry.severity)
        assertTrue(entry.timestamp > 0)
    }

    @Test
    fun `test user login success and failure logging`() = runBlocking {
        logger.logUserLogin("user", true, "Přihlášení úspěšné")
        logger.logUserLogin("user", false, "Špatné heslo")
        kotlinx.coroutines.delay(100)

        val userLogs = logger.getLogsByUserId("user").first { it.size >= 2 }
        assertEquals(2, userLogs.size)

        val failureLog = userLogs.find { it.action == "LOGIN_FAILURE" }
        assertNotNull(failureLog)
        assertEquals("WARN", failureLog?.severity)

        val successLog = userLogs.find { it.action == "LOGIN_SUCCESS" }
        assertNotNull(successLog)
        assertEquals("INFO", successLog?.severity)
    }

    @Test
    fun `test security alert logging and clearing logs`() = runBlocking {
        logger.logSecurityAlert("operator", "UNAUTHORIZED_ACCESS", "Pokus o přístup k paměti bez oprávnění")

        val logsBefore = logger.getRecentLogs(10).first()
        assertEquals(1, logsBefore.size)
        assertEquals("CRITICAL", logsBefore[0].severity)

        logger.clearLogs()

        val logsAfter = logger.getRecentLogs(10).first()
        assertEquals(0, logsAfter.size)
    }
}
