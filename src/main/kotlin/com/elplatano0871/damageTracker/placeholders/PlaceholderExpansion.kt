package com.elplatano0871.damageTracker.placeholders

import com.elplatano0871.damageTracker.managers.DamageManager
import com.elplatano0871.damageTracker.databases.DatabaseManager
import com.elplatano0871.damageTracker.DamageTracker
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

class PlaceholderExpansion(
    private val damageManager: DamageManager, 
    private val databaseManager: DatabaseManager,
    private val plugin: DamageTracker
) : PlaceholderExpansion() {
    
    override fun persist(): Boolean = true

    override fun getAuthor(): String = "ElPlatano0871"

    override fun getIdentifier(): String = "dt"

    override fun getVersion(): String = plugin.description.version

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        return processPlaceholder(player, params)
    }

    override fun onPlaceholderRequest(player: Player?, params: String): String? {
        return processPlaceholder(player, params)
    }

    private fun processPlaceholder(player: OfflinePlayer?, params: String): String? {
        return when {
            params.equals("damage", ignoreCase = true) -> (player?.let { damageManager.getPlayerDamage(it.uniqueId) } ?: 0.0).toString()
            params.startsWith("damagetop_", ignoreCase = true) -> {
                processDamageTopPlaceholder(params)
            }
            else -> null
        }
    }

    private fun processDamageTopPlaceholder(params: String): String? {
        val parts = listOf(params.substringBefore("_"), params.substringAfter("_").substringBeforeLast("_"), params.substringAfterLast("_"))
        
        if (parts.size != 3 || !parts[0].equals("damagetop", ignoreCase = true)) {
            return null
        }

        val bossName = parts[1]
        val positionStr = parts[2]

        val position = positionStr.toIntOrNull()
        if (position == null || position < 1) {
            return "Invalid position"
        }

        return databaseManager.getPlayerAtPosition(bossName, position) ?: "No player found"
    }
}
