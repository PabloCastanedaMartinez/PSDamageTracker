package com.elplatano0871.damageTracker.listeners

import com.elplatano0871.damageTracker.DamageTracker
import com.elplatano0871.damageTracker.managers.DamageManager
import com.elplatano0871.damageTracker.managers.MessageManager
import com.elplatano0871.damageTracker.utils.HologramsUtils
import com.elplatano0871.damageTracker.databases.DatabaseManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent
import org.bukkit.entity.Player
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.entity.EntityDamageByEntityEvent
import java.io.File
import java.util.UUID
import io.lumine.mythic.api.mobs.MythicMob
import io.lumine.mythic.bukkit.MythicBukkit

class MythicMobsListener(private val plugin: DamageTracker, private val damageManager: DamageManager, private val databaseManager: DatabaseManager) : Listener {

    private val messageManager = MessageManager(plugin)
    private val hologramsUtils get() = plugin.hologramsUtils
    private val config: FileConfiguration = plugin.config
    private val messagesConfig: FileConfiguration
    private val trackedBossesConfig: FileConfiguration
    private val leaderboardFormatConfig: FileConfiguration

    init {
        val messagesFile = File(plugin.dataFolder, "victory_messages.yml")
        messagesConfig = if (messagesFile.exists()) YamlConfiguration.loadConfiguration(messagesFile) else YamlConfiguration()

        val trackedBossesFile = File(plugin.dataFolder, "tracked_bosses.yml")
        trackedBossesConfig = if (trackedBossesFile.exists()) YamlConfiguration.loadConfiguration(trackedBossesFile) else YamlConfiguration()

        val leaderboardFormatFile = File(plugin.dataFolder, "leaderboard_format.yml")
        leaderboardFormatConfig = if (leaderboardFormatFile.exists())
            YamlConfiguration.loadConfiguration(leaderboardFormatFile)
        else
            YamlConfiguration()
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    fun onMythicMobDeath(event: MythicMobDeathEvent) {
        val bossId = event.mobType.internalName

        if (!isTrackedBoss(bossId)) {
            return
        }

        val bossConfig = getBossConfig(bossId)
        val broadcastMessage = bossConfig.getBoolean("broadcast_message", true)
        val hologramEnabled = bossConfig.getBoolean("hologram_enabled", false)
        val topPlayersToShow = bossConfig.getInt("top_players_to_show", 3)

        val victoryMessageId = bossConfig.getString("victory_message_id", "DEFAULT_VICTORY")
        val victoryMessage = getMessageFromConfig(victoryMessageId)

        val entries = damageManager.finish(event.entity.uniqueId)
        val leaderboard = entries.take(topPlayersToShow.coerceAtLeast(0)).map { it.name to it.damage }
        val allPlayers = entries.map { it.name to it.damage }
        val totalDamage = getTotalDamage(allPlayers)

        val persistData = plugin.config.getBoolean("tracking_config.persist_data", false)
        if (persistData) {
            databaseManager.saveEncounter(bossId, entries)
        }

        val formattedMessage = victoryMessage?.let {
            formatVictoryMessage(
                it,
                bossId,
                leaderboard,
                totalDamage
            )
        }

        val participants = entries.map { Triple(it.uuid, it.name, it.damage) }

        // Check conditions for hologram: broadcast = false AND hologram_enabled = true
        if (!broadcastMessage && hologramEnabled && hologramsUtils.isFancyHologramsAvailable()) {
            // Create temporary hologram instead of sending messages
            if (formattedMessage != null) {
                hologramsUtils.createTemporaryHologram(
                    formattedMessage,
                    participants,
                    bossId,
                    300L // 5 minutes
                )
                plugin.logger.info("Hologram created for $bossId instead of private messages")
            }
        } else {
            // Original message logic
            if (broadcastMessage) {
                if (formattedMessage != null) {
                    messageManager.broadcastMessage(formattedMessage)
                }
            } else {
                participants.forEach { participant ->
                    val (uuid, _, _) = participant
                    Bukkit.getPlayer(uuid)?.let { player ->
                        if (formattedMessage != null) {
                            messageManager.sendMessage(player, formattedMessage)
                        }
                    }
                }
            }
        }

        val personalMessageId = bossConfig.getString("personal_message_id")
        if (personalMessageId != null) {
            val personalMessage = messagesConfig.getString("personal_messages.$personalMessageId")
            participants.forEach { participant ->
                val (uuid, name, damage) = participant
                Bukkit.getPlayer(uuid)?.let { player ->
                    val playerPercentage = if (totalDamage > 0) (damage / totalDamage) * 100 else 0.0
                    val formattedPersonalMessage = personalMessage?.let {
                        formatPersonalMessage(
                            it.replace("{position}", (entries.indexOfFirst { entry -> entry.uuid == uuid } + 1).toString()),
                            bossId,
                            name,
                            damage,
                            playerPercentage
                        )
                    }
                    if (formattedPersonalMessage != null) {
                        messageManager.sendMessage(player, formattedPersonalMessage)
                    }
                }
            }
        }

        val participantIds = entries.map { it.uuid }.toSet()
        val nonParticipantId = bossConfig.getString("non_participant_message_id", "DEFAULT")
        messagesConfig.getString("non_participant_messages.$nonParticipantId")?.let { text ->
            Bukkit.getOnlinePlayers().filter { it.uniqueId !in participantIds }.forEach {
                messageManager.sendMessage(it, text.replace("{boss_name}", bossId))
            }
        }
        run {
            // Encounter was removed atomically before rendering.
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    fun onMythicMobDamage(event: EntityDamageByEntityEvent) {
        val damager = (event.damager as? org.bukkit.entity.Projectile)?.shooter ?: event.damager
        if (damager !is Player) {
            return
        }

        val entity = event.entity
        val mythicMob = MythicBukkit.inst().apiHelper.getMythicMobInstance(entity) ?: return
        val bossId = mythicMob.type.internalName

        if (!isTrackedBoss(bossId)) {
            return
        }

        damageManager.trackDamage(entity.uniqueId, bossId, damager, event.finalDamage)
    }

    private fun isTrackedBoss(bossId: String): Boolean {
        val enabledBosses = trackedBossesConfig.getStringList("tracked_bosses.enabled_bosses")

        return enabledBosses.isEmpty() || enabledBosses.contains(bossId)
    }

    private fun getBossConfig(bossId: String): ConfigurationSection {
        val bossesConfig = config.getConfigurationSection("bosses")
        val defaultConfig = config.getConfigurationSection("default_boss_config")

        return bossesConfig?.getConfigurationSection(bossId) ?: defaultConfig ?: config.createSection("default_boss_config")
    }

    private fun getMessageFromConfig(messageId: String?): String? {
        if (messageId == null) return "Boss defeated!"
        return messagesConfig.getString("messages.$messageId", "Boss defeated!")
    }

    /**
     * Calculates the total damage based on the damage list
     * @param leaderboard List of player damage pairs
     * @return Total damage dealt by all players
     */
    private fun getTotalDamage(leaderboard: List<Pair<String, Double>>): Double {
        return leaderboard.sumOf { it.second }
    }

    /**
     * Formats the victory message with the leaderboard using leaderboard_format.yml configuration
     * @param template The message template to format
     * @param bossName Name of the defeated boss
     * @param leaderboard List of top players and their damage
     * @param totalDamage Total damage dealt to the boss
     * @return Formatted victory message with leaderboard
     */
    private fun formatVictoryMessage(
        template: String,
        bossName: String,
        leaderboard: List<Pair<String, Double>>,
        totalDamage: Double
    ): String {
        var message = template.replace("%boss%", bossName).replace("{boss_name}", bossName).replace("{boss_name}", bossName).replace("{personal_damage}", "")

        // Get formats from leaderboard_format.yml file
        val nameFormat = leaderboardFormatConfig.getString("formats.name", "&e{name}")
        val damageFormat = leaderboardFormatConfig.getString("formats.damage", "&c⚔{damage}")
        val symbolFormat = leaderboardFormatConfig.getString("formats.symbol", "&a>>")
        val lineFormat = leaderboardFormatConfig.getString("format", "{symbol} {position}. {name}: {damage}")

        // Format damage values according to configuration
        val damageNumberFormat = config.getString("damage_format", "%.2f")

        // Build the leaderboard
        val leaderboardString = StringBuilder()
        leaderboard.forEachIndexed { index, (playerName, damage) ->
            val position = index + 1
            val percentage = if (totalDamage > 0) (damage / totalDamage) * 100 else 0.0

            // Format the name according to the defined format
            val formattedName = nameFormat?.replace("{name}", playerName) ?: playerName

            // Format the damage according to the defined format
            val formattedDamage = damageFormat?.replace("{damage}", String.format(damageNumberFormat ?: "%.2f", damage)) ?: String.format("%.2f", damage)

            // Build the complete line according to the defined format

            var line = symbolFormat?.let {
                lineFormat
                    ?.replace("{symbol}", it)
                    ?.replace("{position}", position.toString())
                    ?.replace("{name}", formattedName)
                    ?.replace("{damage}", formattedDamage)
                    ?.replace("{percentage}", String.format(config.getString("percentage_format", "%.1f") ?: "%.1f", percentage))
            }

            val formatId = getBossConfig(bossName).getString("position_format_id", "DEFAULT")
            val formats = messagesConfig.getStringList("position_formats.$formatId")
            formats.getOrNull(index)?.let { positionTemplate ->
                line = positionTemplate.replace("{prefix}", "")
                    .replace("{player_name}", playerName)
                    .replace("{damage}", String.format(damageNumberFormat ?: "%.2f", damage))
                    .replace("{percentage}", String.format("%.1f", percentage))
                    .replace("{position}", position.toString())
            }
            // Convert legacy colors to MiniMessage if necessary
            line = line?.let { messageManager.convertLegacyToMiniMessage(it) }

            leaderboardString.append(line).append("\n")
        }

        // Replace the leaderboard placeholder
        message = message.replace("%leaderboard%", leaderboardString.toString().trim()).replace("{top_players}", leaderboardString.toString().trim())

        return message
    }

    private fun formatPersonalMessage(
        template: String,
        bossName: String,
        playerName: String,
        damage: Double,
        percentage: Double
    ): String {
        val damageNumberFormat = config.getString("damage_format", "%.2f")
        val percentageFormat = config.getString("percentage_format", "%.1f")

        return template
            .replace("%boss%", bossName).replace("{boss_name}", bossName)
            .replace("%player%", playerName).replace("{player_name}", playerName)
            .replace("%damage%", String.format(damageNumberFormat ?: "%.2f", damage)).replace("{damage}", String.format(damageNumberFormat ?: "%.2f", damage))
            .replace("%percentage%", String.format(percentageFormat ?: "%.1f", percentage)).replace("{percentage}", String.format(percentageFormat ?: "%.1f", percentage))
    }

}
