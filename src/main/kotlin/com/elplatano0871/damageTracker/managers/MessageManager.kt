package com.elplatano0871.damageTracker.managers

import com.elplatano0871.damageTracker.utils.MessageUtils
import com.elplatano0871.damageTracker.DamageTracker
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

class MessageManager(private val plugin: DamageTracker) {

    private val messageUtils = MessageUtils()
    private val miniMessage = MiniMessage.miniMessage()
    private val legacySerializer = LegacyComponentSerializer.legacySection()

    fun convertLegacyToMiniMessage(input: String): String {
        return messageUtils.convertLegacyToMiniMessage(input)
    }

    fun broadcastMessage(message: String) {
        val processedMessage = processMessage(message)
        val legacyText = legacySerializer.serialize(processedMessage)
        Bukkit.getServer().broadcastMessage(legacyText)
    }

    fun sendMessage(player: Player, message: String) {
        val processedMessage = processMessage(message)
        val legacyText = legacySerializer.serialize(processedMessage)
        player.sendMessage(legacyText)
    }

    fun sendBossMessage(message: String, participants: List<Player>, broadcastToAll: Boolean) {
        if (broadcastToAll) {
            broadcastMessage(message)
        } else {
            participants.forEach { player ->
                sendMessage(player, message)
            }
        }
    }

    private fun processMessage(message: String): Component {
        var processedMessage = convertLegacyToMiniMessage(message)

        processedMessage = processCenteredTags(processedMessage)

        return miniMessage.deserialize(processedMessage)
    }

    private fun processCenteredTags(message: String): String {
        val centerTagRegex = "<centered>(.*?)</centered>".toRegex()

        return message.replace(centerTagRegex) { matchResult ->
            val textToCenter = matchResult.groupValues[1]
            centerText(textToCenter)
        }
    }

    private fun centerText(text: String): String {
        val chatWidth = 320

        val textWidth = estimateTextWidth(text)

        val spacesNeeded = (chatWidth - textWidth) / 2

        val padding = " ".repeat((spacesNeeded / 4).coerceAtLeast(0))

        return padding + text
    }

    private fun estimateTextWidth(text: String): Int {
        val plainText = text.replace("<[^>]*>".toRegex(), "")

        return plainText.length * 6
    }
}