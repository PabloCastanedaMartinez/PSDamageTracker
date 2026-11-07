package com.elplatano0871.damageTracker.managers

import com.elplatano0871.damageTracker.DamageTracker
import com.elplatano0871.damageTracker.databases.DatabaseManager
import org.bukkit.entity.Player
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

class DamageManager(private val plugin: DamageTracker, private val databaseManager: DatabaseManager) {
    data class Entry(val uuid: UUID, val name: String, val damage: Double)
    private data class Fight(val boss: String, val entries: MutableMap<UUID, Entry> = mutableMapOf())
    private val fights = mutableMapOf<UUID, Fight>()
    private var trackedBosses = emptyList<String>()
    init { reload() }
    @Synchronized
    fun reload() {
        trackedBosses = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "tracked_bosses.yml"))
            .getStringList("tracked_bosses.enabled_bosses")
    }
    @Synchronized
    fun isBossTracked(bossID: String) = trackedBosses.isEmpty() || bossID in trackedBosses
    @Synchronized
    fun trackDamage(bossID: String, player: Player, damage: Double) =
        trackDamage(UUID.nameUUIDFromBytes(bossID.toByteArray()), bossID, player, damage)
    @Synchronized
    fun trackDamage(entity: UUID, bossID: String, player: Player, damage: Double) {
        if (!isBossTracked(bossID) || !damage.isFinite() || damage <= 0) return
        val fight = fights.getOrPut(entity) { Fight(bossID) }
        val id = player.uniqueId
        val total = (fight.entries[id]?.damage ?: 0.0) + damage
        if (total.isFinite()) fight.entries[id] = Entry(id, player.name, total)
    }
    @Synchronized
    fun finish(entity: UUID): List<Entry> = fights.remove(entity)?.entries?.values
        ?.sortedWith(compareByDescending<Entry> { it.damage }.thenBy { it.uuid }) ?: emptyList()
    @Synchronized
    fun discard(entity: UUID) { fights.remove(entity) }
    @Synchronized
    fun getLeaderboard(bossID: String, top: Int): List<Pair<String, Double>> =
        fights.values.filter { it.boss == bossID }.flatMap { it.entries.values }
            .groupBy { it.uuid }.values.map { rows -> rows.last().name to rows.sumOf { it.damage } }
            .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })
            .take(top.coerceAtLeast(0))
    @Synchronized
    fun getTotalDamageForBoss(bossID: String) = getLeaderboard(bossID, Int.MAX_VALUE).sumOf { it.second }
    @Synchronized
    fun getPlayerDamage(bossID: String, playerName: String) =
        getLeaderboard(bossID, Int.MAX_VALUE).firstOrNull { it.first == playerName }?.second ?: 0.0
    /** Total contribution across active encounters; safe for asynchronous placeholder requests. */
    @Synchronized
    fun getPlayerDamage(playerId: UUID): Double =
        fights.values.sumOf { it.entries[playerId]?.damage ?: 0.0 }

    @Synchronized
    fun clearBossData(bossID: String) { fights.entries.removeIf { it.value.boss == bossID } }
}
