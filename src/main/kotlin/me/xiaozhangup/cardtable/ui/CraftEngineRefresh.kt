package me.xiaozhangup.cardtable.ui

import me.xiaozhangup.cardtable.CardTablePlugin
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

/** Isolated optional integration; re-render after CE replaces item definitions. */
object CraftEngineRefresh {
    fun register(plugin: CardTablePlugin) {
        Bukkit.getPluginManager().registerEvents(object : Listener {
            @EventHandler
            fun reload(event: CraftEngineReloadEvent) {
                plugin.crab.submitTask(delay = 1) {
                    plugin.tables.rooms.values.forEach { room -> plugin.renderer.refreshAssets(room); plugin.menus.refresh(room) }
                }
            }
        }, plugin)
    }
}
