package com.elplatano0871.damageTracker.commands

import com.elplatano0871.damageTracker.DamageTracker
import com.elplatano0871.damageTracker.managers.DamageManager
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import java.util.*

class CommandHandler(private val plugin: DamageTracker, private val damageManager: DamageManager) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sendHelpMessage(sender)
            return true
        }
        if (!sender.hasPermission("damageTracker." + args[0].lowercase(Locale.ROOT))) {
            sender.sendMessage("You do not have permission.")
            return true
        }
        when (args[0].lowercase(Locale.ROOT)) {
            "reload" -> {
                plugin.reloadSettings()
                sender.sendMessage("DamageTracker has been reloaded.")
                return true
            }
            "check" -> {
                if (args.size < 2) {
                    sender.sendMessage("Uso: /damagetracker check <bossID>")
                    return true
                }
                val bossID = args[1]
                val topPlayers = damageManager.getLeaderboard(bossID, 3)
                if (topPlayers.isEmpty()) {
                    sender.sendMessage("No info for boss $bossID.")
                } else {
                    sender.sendMessage("Leaderboard for boss $bossID:")
                    val damageFormat = plugin.config.getString("damage_format", "%.2f")
                    topPlayers.forEachIndexed { index, pair ->
                        val formattedDamage = String.format(damageFormat ?: "%.2f", pair.second)
                        sender.sendMessage("${index + 1}. ${pair.first} - $formattedDamage")
                    }
                }
                return true
            }
            else -> {
                sendHelpMessage(sender)
            }
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String>? {
        val completions = mutableListOf<String>()
        if (args.size == 1) {
            completions.add("reload")
            completions.add("check")
        }
        return completions
    }

    fun sendHelpMessage(sender: CommandSender) {
        sender.sendMessage("Usage:")
        sender.sendMessage("/damagetracker reload - Reload configuration")
        sender.sendMessage("/damagetracker check <bossID> - View the leaderboard for a boss")
    }
}
