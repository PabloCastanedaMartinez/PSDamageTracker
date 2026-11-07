package com.elplatano0871.damageTracker

import com.elplatano0871.damageTracker.databases.DatabaseManager
import com.elplatano0871.damageTracker.managers.DamageManager
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.*
import java.io.File

class DamageManagerTest {
    @TempDir lateinit var folder: File

    private fun manager(): DamageManager {
        val plugin = mock(DamageTracker::class.java)
        `when`(plugin.config).thenReturn(YamlConfiguration())
        `when`(plugin.dataFolder).thenReturn(folder)
        return DamageManager(plugin, mock(DatabaseManager::class.java))
    }

    private fun player(name: String): Player = mock(Player::class.java).also {
        `when`(it.name).thenReturn(name)
        `when`(it.uniqueId).thenReturn(java.util.UUID.nameUUIDFromBytes(name.toByteArray()))
    }

    @Test fun emptyFilterTracksDamageAndClears() {
        val manager = manager()
        val player = player("Alice")
        manager.trackDamage("BOSS", player, 10.0)
        manager.trackDamage("BOSS", player, 5.0)
        assertEquals(15.0, manager.getPlayerDamage("BOSS", "Alice"))
        manager.clearBossData("BOSS")
        assertTrue(manager.getLeaderboard("BOSS", 3).isEmpty())
    }

    @Test fun invalidDamageAndNegativeLimitsAreSafe() {
        val manager = manager()
        val player = player("Alice")
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0).forEach {
            manager.trackDamage("BOSS", player, it)
        }
        assertEquals(0.0, manager.getTotalDamageForBoss("BOSS"))
        manager.trackDamage("BOSS", player, 3.0)
        assertTrue(manager.getLeaderboard("BOSS", -1).isEmpty())
    }

    @Test fun rankingIncludesAllDamageBeyondDisplayLimit() {
        val manager = manager()
        manager.trackDamage("BOSS", player("Alice"), 5.0)
        manager.trackDamage("BOSS", player("Bob"), 10.0)
        assertEquals(listOf("Bob" to 10.0), manager.getLeaderboard("BOSS", 1))
        assertEquals(15.0, manager.getTotalDamageForBoss("BOSS"))
    }

    @Test fun explicitFilterExcludesOtherBosses() {
        File(folder, "tracked_bosses.yml").writeText("tracked_bosses:\n  enabled_bosses: [ALLOWED]\n")
        val manager = manager()
        manager.trackDamage("OTHER", player("Alice"), 10.0)
        assertEquals(0.0, manager.getTotalDamageForBoss("OTHER"))
    }
    @Test fun simultaneousBossesAndRenamedPlayersStayIsolated() {
        val manager = manager()
        val a = java.util.UUID.randomUUID()
        val b = java.util.UUID.randomUUID()
        val player = player("Alice")
        manager.trackDamage(a, "BOSS", player, 10.0)
        manager.trackDamage(b, "BOSS", player, 20.0)
        assertEquals(10.0, manager.finish(a).single().damage)
        assertTrue(manager.finish(a).isEmpty())
        assertEquals(20.0, manager.finish(b).single().damage)
    }}
