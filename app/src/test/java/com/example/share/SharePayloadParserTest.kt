package com.example.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.ui.OmnisTab
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.OmnisStrings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], packageName = "com.example")
class SharePayloadParserTest {

    @Test
    fun `parse ACTION_SEND with url extracts detectedUrls and URL content type`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "NIS2 Briefing")
            putExtra(Intent.EXTRA_TEXT, "Check https://example.org/nis2-audit for details.")
        }

        val payload = SharePayloadParser.parse(context, intent)
        assertNotNull(payload)
        assertEquals(SharedContentType.URL, payload!!.contentType)
        assertEquals("NIS2 Briefing", payload.subject)
        assertEquals(listOf("https://example.org/nis2-audit"), payload.detectedUrls)
    }

    @Test
    fun `parse ACTION_SEND with pdf stream caches file safely`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourcePdf = File(context.cacheDir, "sample_brief.pdf").apply {
            writeBytes("%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".toByteArray())
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, Uri.fromFile(sourcePdf))
        }

        val payload = SharePayloadParser.parse(context, intent)
        assertNotNull(payload)
        assertEquals(SharedContentType.PDF, payload!!.contentType)
        assertEquals(1, payload.attachments.size)
        assertTrue(payload.attachments.first().isPdf)
        assertTrue(File(payload.attachments.first().localCachePath).exists())
    }

    @Test
    fun `parse ACTION_MAIN returns null`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(Intent.ACTION_MAIN)
        assertNull(SharePayloadParser.parse(context, intent))
    }

    @Test
    fun `core telemetry tab title resolves cleanly in CS and EN`() {
        assertEquals("Core / Telemetry", OmnisStrings.tabTitle(OmnisTab.CORE_TELEMETRY, AppLanguage.CS))
        assertEquals("Core / Telemetry", OmnisStrings.tabTitle(OmnisTab.CORE_TELEMETRY, AppLanguage.EN))
    }

    @Test
    fun `parse ACTION_SEND_MULTIPLE merges text, image, and pdf as MIXED`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pdfFile = File(context.cacheDir, "audit_multi.pdf").apply {
            writeBytes("%PDF-1.4\n%%EOF".toByteArray())
        }
        val imgFile = File(context.cacheDir, "diagram_multi.png").apply {
            writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_TEXT, "Auditní balíček viz https://example.org/spec?v=2!")
            putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                arrayListOf(Uri.fromFile(pdfFile), Uri.fromFile(imgFile))
            )
        }

        val payload = SharePayloadParser.parse(context, intent)
        assertNotNull(payload)
        assertEquals(SharedContentType.MIXED, payload!!.contentType)
        assertEquals(2, payload.attachments.size)
        assertEquals(listOf("https://example.org/spec?v=2"), payload.detectedUrls)
    }

    @Test
    fun `pruneStaleCache removes expired files and preserves fresh ones`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "test_prune_shared").apply {
            deleteRecursively()
            mkdirs()
        }
        val stale = File(dir, "old.pdf").apply {
            writeText("old")
            setLastModified(1_000L)
        }
        val fresh = File(dir, "new.pdf").apply {
            writeText("new")
        }

        val removed = SharePayloadParser.pruneStaleCache(
            cacheDir = dir,
            maxAgeMs = 60_000L,
            nowMs = System.currentTimeMillis()
        )
        assertEquals(1, removed)
        assertTrue(!stale.exists())
        assertTrue(fresh.exists())
        dir.deleteRecursively()
    }

    @Test
    fun `KeyPool refresh updates activeKeyName accurately when empty or populated`() {
        com.example.api.OmnisGeminiClient.KeyPool.refresh(
            custom1 = "AIzaTestCustomKeyForRotationPool12345"
        )
        assertTrue(com.example.api.OmnisGeminiClient.KeyPool.hasAnyKey())
        assertTrue(com.example.api.OmnisGeminiClient.activeKeyName.value.contains("Slot 1"))

        com.example.api.OmnisGeminiClient.KeyPool.refresh()
    }
}
