package me.xiaozhangup.cardtable.util

import me.xiaozhangup.crab.command.Notify
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.command.CommandSender

internal val tableNotify = Notify("牌桌", "#9bc7b6")
private val repeatedSpaces = Regex(" {2,}")

internal fun sendTableMessage(sender: CommandSender, text: String) {
    val spaced = text.replace("(", " (").replace(")", ") ").replace(repeatedSpaces, " ").trim()
    // Convert legacy highlights to MiniMessage while escaping literal player-provided text.
    val body = LegacyComponentSerializer.legacySection().deserialize(spaced)
    tableNotify.send(sender, MiniMessage.miniMessage().serialize(body))
}
