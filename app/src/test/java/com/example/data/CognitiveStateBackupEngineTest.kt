package com.example.data

import org.junit.Assert.*
import org.junit.Test

class CognitiveStateBackupEngineTest {

    @Test
    fun testExportAndImportBackupPackage() {
        val records = listOf(
            OmnisRecord(id = 1L, role = "user", content = "Testovací dotaz kognitivní zálohy", domain = "SYS")
        )
        val fragments = listOf(
            MemoryFragment(
                id = 1L,
                title = "Fragment #1",
                summary = "Souhrn fragmentu #1",
                timestamp = System.currentTimeMillis(),
                sourceRecordCount = 3,
                tags = "SYNAPSE"
            )
        )
        val goals = listOf(
            OmnisGoal(
                id = 1L,
                title = "Cíl #1",
                description = "Popis cíle #1",
                status = "ACTIVE",
                priority = 3,
                progress = 0.5f,
                tasksJson = "[]"
            )
        )
        val artifacts = listOf(
            OmnisArtifact(
                id = 1L,
                title = "Artefakt #1",
                type = "CODE",
                language = "kotlin",
                content = "val x = 42"
            )
        )

        val json = CognitiveStateBackupEngine.exportToJson(
            records = records,
            fragments = fragments,
            goals = goals,
            artifacts = artifacts
        )

        assertNotNull(json)
        assertTrue("JSON obsahuje verzi 4.2", json.contains("4.2"))
        assertTrue("JSON obsahuje kontrolní součet", json.contains("checksum"))

        val importResult = CognitiveStateBackupEngine.importFromJson(json)
        assertTrue("Import má být úspěšný", importResult.isSuccess)

        val pkg = importResult.getOrNull()
        assertNotNull(pkg)
        assertEquals(1, pkg!!.records.size)
        assertEquals("Testovací dotaz kognitivní zálohy", pkg.records[0].content)
        assertEquals(1, pkg.fragments.size)
    }
}
