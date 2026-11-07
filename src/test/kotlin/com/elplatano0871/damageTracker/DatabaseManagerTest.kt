package com.elplatano0871.damageTracker

import com.elplatano0871.damageTracker.databases.DatabaseManager
import com.elplatano0871.damageTracker.managers.DamageManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.*
import java.io.File
import java.sql.DriverManager
import java.util.UUID
import java.util.logging.Logger

class DatabaseManagerTest {
    @TempDir lateinit var folder: File
    @Test fun queuedWritesPersistUuidAndNameChangesBeforeShutdown() {
        val plugin = mock(DamageTracker::class.java)
        `when`(plugin.dataFolder).thenReturn(folder)
        `when`(plugin.logger).thenReturn(Logger.getAnonymousLogger())
        val db = DatabaseManager(plugin)
        val id = UUID.randomUUID()
        db.loadDatabase()
        db.createDatabase()
        db.saveEncounter("boss", listOf(DamageManager.Entry(id, "OldName", 10.0)))
        db.saveEncounter("boss", listOf(DamageManager.Entry(id, "NewName", 20.0)))
        db.closeDatabase()
        DriverManager.getConnection("jdbc:sqlite:" + File(folder, "damage_tracker.db")).use { c ->
            c.createStatement().use { stmt ->
                stmt.executeQuery("SELECT * FROM boss_damage_uuid").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(id.toString(), rs.getString("player_uuid"))
                    assertEquals("NewName", rs.getString("player_name"))
                    assertEquals(30.0, rs.getDouble("damage"))
                    assertFalse(rs.next())
                }
            }
        }
        assertEquals("NewName", db.getPlayerAtPosition("boss", 1))
    }
}
