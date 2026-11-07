package com.elplatano0871.damageTracker.databases

import com.elplatano0871.damageTracker.DamageTracker
import com.elplatano0871.damageTracker.managers.DamageManager.Entry
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** A single worker owns JDBC; placeholder reads use immutable snapshots only. */
class DatabaseManager(private val plugin: DamageTracker) {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "PSDamageTracker-database") }
    private var connection: Connection? = null
    @Volatile private var rankings: Map<String, List<Pair<String, Double>>> = emptyMap()
    private fun submit(action: () -> Unit) {
        worker.execute {
            try { action() } catch (e: Exception) {
                plugin.logger.log(java.util.logging.Level.SEVERE, "Damage database operation failed", e)
            }
        }
    }
    fun loadDatabase() = submit {
        plugin.dataFolder.mkdirs()
        connection = DriverManager.getConnection("jdbc:sqlite:" + File(plugin.dataFolder, "damage_tracker.db").absolutePath)
    }
    fun createDatabase() = submit {
        val db = checkNotNull(connection)
        db.createStatement().use {
            it.execute("CREATE TABLE IF NOT EXISTS boss_damage (player_name TEXT NOT NULL, damage REAL NOT NULL, boss_name TEXT NOT NULL, PRIMARY KEY(player_name,boss_name))")
            it.execute("CREATE TABLE IF NOT EXISTS boss_damage_uuid (player_uuid TEXT NOT NULL, player_name TEXT NOT NULL, damage REAL NOT NULL, boss_name TEXT NOT NULL, PRIMARY KEY(player_uuid,boss_name))")
        }
        refresh()
    }
    fun saveEncounter(boss: String, entries: List<Entry>) = submit {
        val db = checkNotNull(connection)
        db.autoCommit = false
        try {
            db.prepareStatement("INSERT INTO boss_damage_uuid VALUES (?, ?, ?, ?) ON CONFLICT(player_uuid,boss_name) DO UPDATE SET player_name=excluded.player_name, damage=boss_damage_uuid.damage+excluded.damage").use { stmt ->
                entries.forEach { entry ->
                    require(entry.damage.isFinite() && entry.damage > 0)
                    stmt.setString(1, entry.uuid.toString())
                    stmt.setString(2, entry.name)
                    stmt.setDouble(3, entry.damage)
                    stmt.setString(4, boss.uppercase(Locale.ROOT))
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            db.commit()
        } catch (e: Exception) { db.rollback(); throw e }
        finally { db.autoCommit = true }
        refresh()
    }
    private fun refresh() {
        val rows = mutableMapOf<String, MutableList<Pair<String, Double>>>()
        checkNotNull(connection).createStatement().use { stmt ->
            // Legacy totals remain separate: names cannot safely be resolved to online UUIDs.
            stmt.executeQuery("SELECT player_name,damage,boss_name FROM boss_damage UNION ALL SELECT player_name,damage,boss_name FROM boss_damage_uuid").use { rs ->
                while (rs.next()) rows.getOrPut(rs.getString(3).uppercase(Locale.ROOT)) { mutableListOf() }
                    .add(rs.getString(1) to rs.getDouble(2))
            }
        }
        rankings = rows.mapValues { (_, list) -> list.sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first }) }
    }
    fun getPlayerAtPosition(bossName: String, position: Int): String? =
        if (position < 1) null else rankings[bossName.uppercase(Locale.ROOT)]?.getOrNull(position - 1)?.first
    fun getFormattedLeaderboard(bossName: String): String =
        rankings[bossName.uppercase(Locale.ROOT)]?.take(10)?.mapIndexed { i, entry -> "${i + 1}. ${entry.first}: ${entry.second}" }?.joinToString("\n") ?: ""
    fun closeDatabase() {
        submit { connection?.close(); connection = null }
        worker.shutdown()
        if (!worker.awaitTermination(30, TimeUnit.SECONDS)) plugin.logger.severe("Database shutdown is still pending.")
    }
}
