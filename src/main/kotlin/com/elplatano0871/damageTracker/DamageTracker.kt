package com.elplatano0871.damageTracker

import com.elplatano0871.damageTracker.commands.CommandHandler
import com.elplatano0871.damageTracker.databases.DatabaseManager
import com.elplatano0871.damageTracker.listeners.MythicMobsListener
import com.elplatano0871.damageTracker.managers.DamageManager
import com.elplatano0871.damageTracker.placeholders.PlaceholderExpansion
import com.elplatano0871.damageTracker.utils.HologramsUtils
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class DamageTracker : JavaPlugin() {

    lateinit var databaseManager: DatabaseManager
    lateinit var damageManager: DamageManager
    lateinit var hologramsUtils: HologramsUtils
    lateinit var commandHandler: CommandHandler

    override fun onEnable() {
        if (!server.pluginManager.isPluginEnabled("MythicMobs")) {
            logger.severe("MythicMobs is not enabled. Install a version compatible with this server.")
            server.pluginManager.disablePlugin(this)
            return
        }
        loadConfig()

        databaseManager = DatabaseManager(this)
        damageManager = DamageManager(this, databaseManager)
        commandHandler = CommandHandler(this, damageManager)

        loadListeners()
        loadCommands()
        loadDatabase()
        setupPlaceholderExpansion()
        displayAsciiArt()
        
        hologramsUtils = HologramsUtils(this)
    }

    override fun onDisable() {
        // Limpiar hologramas activos
        if (::hologramsUtils.isInitialized) {
            hologramsUtils.clearAllHolograms()
        }
        if (::databaseManager.isInitialized) closeDatabase()
        logger.info("DamageTracker has been disabled.")
    }

    private fun loadConfig() {
        saveDefaultConfig()

        copyResource("leaderboard_format.yml")
        copyResource("victory_messages.yml")
        copyResource("tracked_bosses.yml")
    }

    private fun copyResource(filename: String) {
        val file = File(dataFolder, filename)
        if (!file.exists()) {
            saveResource(filename, false)
        }
    }

    fun reloadSettings() {
        reloadConfig()
        damageManager.reload()
        org.bukkit.event.HandlerList.unregisterAll(this)
        loadListeners()
    }

    private fun loadListeners() {
        server.pluginManager.registerEvents(MythicMobsListener(this, damageManager, databaseManager), this)
    }

    private fun loadCommands() {
        getCommand("damagetracker")?.setExecutor(commandHandler)
        getCommand("damagetracker")?.tabCompleter = commandHandler
    }

    private fun loadDatabase() {
        databaseManager.loadDatabase()
        databaseManager.createDatabase()
    }

    private fun closeDatabase() {
        databaseManager.closeDatabase()
    }

    private fun setupPlaceholderExpansion() {
        if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
            val expansion = PlaceholderExpansion(damageManager, databaseManager, this)
            expansion.register()
            logger.info("PlaceholderAPI expansion registered.")
        }
    }

    private fun displayAsciiArt(
        customBanner: String? = null,
        pluginName: String? = null,
        showBorder: Boolean = true
        ) {
            val yellow = "\u001B[33m"
            val reset = "\u001B[0m"
            
            val banner = customBanner ?: """
            ____       _            _         ____  _             _ _           
            |  _ \ ___ | |_ __ _ ___(_) ___   / ___|| |_ _   _  __| (_) ___  ___ 
            | |_) / _ \| __/ _` / __| |/ _ \  \___ \| __| | | |/ _` | |/ _ \/ __|
            |  __/ (_) | || (_| \__ \ | (_) |  ___) | |_| |_| | (_| | | (_) \__ \
            |_|   \___/ \__\__,_|___/_|\___/  |____/ \__|\__,_|\__,_|_|\___/|___/
                    """.trimIndent()
                    
                val name = pluginName ?: description.name
                
                if (showBorder) {
                    logger.info("$yellow${"=".repeat(70)}$reset")
                }
                
                // Split banner into lines and log each one with yellow color
                banner.split("\n").forEach { line ->
                    logger.info("$yellow$line$reset")
                }
                
                if (showBorder) {
                    logger.info("$yellow${"=".repeat(70)}$reset")
                    logger.info("${yellow}Plugin: $name$reset")
                    logger.info("${yellow}Version: ${description.version}$reset")
                    logger.info("${yellow}Author: ${description.authors.joinToString(", ")}$reset")
                    logger.info("$yellow${"=".repeat(70)}$reset")
                }
        }
}
