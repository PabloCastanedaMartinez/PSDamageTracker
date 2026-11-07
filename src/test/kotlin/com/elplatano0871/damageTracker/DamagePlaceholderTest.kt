package com.elplatano0871.damageTracker

import com.elplatano0871.damageTracker.databases.DatabaseManager
import com.elplatano0871.damageTracker.managers.DamageManager
import com.elplatano0871.damageTracker.placeholders.PlaceholderExpansion
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.*
import java.io.File
import java.util.UUID

class DamagePlaceholderTest {
    @TempDir lateinit var folder: File

    @Test fun damageUsesPlayerUuidAcrossActiveEncountersAndBothCallbacks() {
        val plugin = mock(DamageTracker::class.java)
        `when`(plugin.dataFolder).thenReturn(folder)
        val database = mock(DatabaseManager::class.java)
        val manager = DamageManager(plugin, database)
        val expansion = PlaceholderExpansion(manager, database, plugin)
        val player = mock(Player::class.java)
        val id = UUID.randomUUID()
        `when`(player.uniqueId).thenReturn(id)
        `when`(player.name).thenReturn("Alice")
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        assertEquals("0.0", expansion.onRequest(player, "damage"))
        manager.trackDamage(first, "BOSS", player, 10.5)
        manager.trackDamage(second, "OTHER", player, 4.0)
        `when`(player.name).thenReturn("Renamed")
        assertEquals("14.5", expansion.onRequest(player, "damage"))
        assertEquals("14.5", expansion.onPlaceholderRequest(player, "DAMAGE"))
        val other = mock(Player::class.java)
        `when`(other.uniqueId).thenReturn(UUID.randomUUID())
        assertEquals("0.0", expansion.onRequest(other, "damage"))
        assertEquals("0.0", expansion.onRequest(null, "damage"))
        assertEquals("0.0", expansion.onPlaceholderRequest(null, "damage"))
        manager.finish(first)
        assertEquals("4.0", expansion.onRequest(player, "damage"))
        manager.discard(second)
        assertEquals("0.0", expansion.onRequest(player, "damage"))
        assertNull(expansion.onRequest(player, "unknown"))
    }
}
