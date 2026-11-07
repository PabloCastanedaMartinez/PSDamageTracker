package com.elplatano0871.damageTracker.utils

import de.oliver.fancyholograms.api.FancyHologramsPlugin
import de.oliver.fancyholograms.api.data.TextHologramData
import de.oliver.fancyholograms.api.hologram.Hologram
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import com.elplatano0871.damageTracker.DamageTracker
import com.elplatano0871.damageTracker.managers.MessageManager
import net.kyori.adventure.text.minimessage.MiniMessage
import java.util.UUID

class HologramsUtils(private val plugin: DamageTracker) {

    private val messageManager = MessageManager(plugin)
    private val miniMessage = MiniMessage.miniMessage()
    private val activeHolograms = mutableMapOf<String, Hologram>()

    /**
     * Creates a temporary hologram to display the victory message
     * @param message The formatted message that would normally be displayed
     * @param participants List of battle participants
     * @param bossName Name of the defeated boss
     * @param duration Duration in seconds (default 300 = 5 minutes)
     */
    fun createTemporaryHologram(
        message: String,
        participants: List<Triple<UUID, String, Double>>,
        bossName: String,
        duration: Long = 300L
    ) {
        if (participants.isEmpty()) return

        // Find an online player to get the location
        val onlinePlayer = participants.mapNotNull { (uuid, _, _) ->
            Bukkit.getPlayer(uuid)
        }.firstOrNull() ?: return

        // Create hologram location (slightly above the player)
        val hologramLocation = onlinePlayer.location.clone().add(0.0, 3.0, 0.0)
        
        createHologramAtLocation(message, hologramLocation, bossName, duration)
        
        // Notify all participants about the hologram
        notifyParticipantsAboutHologram(participants, hologramLocation)
    }

    /**
     * Creates a temporary hologram at a specific location
     * @param message The message to display in the hologram
     * @param location The location where the hologram will be created
     * @param bossName Name of the boss for identification purposes
     * @param duration Duration in seconds before the hologram is removed
     */
    fun createHologramAtLocation(
        message: String,
        location: Location,
        bossName: String,
        duration: Long = 300L
    ) {
        try {
            val hologramManager = FancyHologramsPlugin.get().hologramManager
            
            // Generate a unique name for the hologram
            val hologramName = "victory_${bossName}_${System.currentTimeMillis()}"
            
            // Convert the message to MiniMessage format if it isn't already
            val miniMessageText = messageManager.convertLegacyToMiniMessage(message)
            
            // Split the message into lines for the hologram
            val lines = miniMessageText.split("\n")
            
            // Create the hologram data
            val hologramData = TextHologramData(hologramName, location)
            
            // Configure the hologram text
            hologramData.text = lines
            
            // Create the hologram
            val hologram = hologramManager.create(hologramData)
            
            // Register the hologram
            hologramManager.addHologram(hologram)
            
            // Store hologram reference
            activeHolograms[hologramName] = hologram
            
            plugin.logger.info("Hologram created: $hologramName for boss $bossName")
            
            // Schedule hologram removal after the specified duration
            object : BukkitRunnable() {
                override fun run() {
                    removeHologram(hologramName)
                }
            }.runTaskLater(plugin, duration * 20L) // Convert seconds to ticks
            
        } catch (e: Exception) {
            plugin.logger.severe("Error creating hologram for $bossName: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Removes a specific hologram
     * @param hologramName The name of the hologram to remove
     */
    private fun removeHologram(hologramName: String) {
        try {
            val hologramManager = FancyHologramsPlugin.get().hologramManager
            
            // Get the hologram by name first
            val hologram = hologramManager.getHologram(hologramName).orElse(null)
            if (hologram != null) {
                // Remove from manager
                hologramManager.removeHologram(hologram)
                
                // Remove from our registry
                activeHolograms.remove(hologramName)
                
                plugin.logger.info("Hologram removed: $hologramName")
            } else {
                plugin.logger.warning("Hologram not found: $hologramName")
            }
            
        } catch (e: Exception) {
            plugin.logger.warning("Error removing hologram $hologramName: ${e.message}")
        }
    }

    /**
     * Notifies participants about the hologram creation
     * @param participants List of battle participants
     * @param location Location where the hologram was created
     */
    private fun notifyParticipantsAboutHologram(
        participants: List<Triple<UUID, String, Double>>,
        location: Location
    ) {
        val notificationMessage = "<gold>A hologram with the battle results has been created!</gold>"
        
        participants.forEach { (uuid, _, _) ->
            Bukkit.getPlayer(uuid)?.let { player ->
                messageManager.sendMessage(player, notificationMessage)
            }
        }
    }

    /**
     * Clears all active holograms
     */
    fun clearAllHolograms() {
        activeHolograms.keys.toList().forEach { hologramName ->
            removeHologram(hologramName)
        }
    }

    /**
     * Checks if FancyHolograms is available
     * @return true if FancyHolograms plugin is available, false otherwise
     */
    fun isFancyHologramsAvailable(): Boolean {
        if (!plugin.server.pluginManager.isPluginEnabled("FancyHolograms")) return false
        return try {
            FancyHologramsPlugin.get() != null
        } catch (e: Exception) {
            false
        }
    }
}
