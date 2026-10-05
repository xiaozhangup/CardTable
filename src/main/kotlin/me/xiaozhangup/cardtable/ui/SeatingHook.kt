package me.xiaozhangup.cardtable.ui

import me.xiaozhangup.cardtable.CardTablePlugin
import me.xiaozhangup.cardtable.table.TableConfig
import me.xiaozhangup.cardtable.util.ext.info
import me.xiaozhangup.cardtable.util.ext.submitTask
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Interaction
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDismountEvent
import org.bukkit.event.entity.EntityTeleportEvent
import java.util.UUID

/** A zero-size native mount provides the seated avatar pose without a clickable seat entity. */
class SeatingHook(private val plugin: CardTablePlugin) : Listener {
    private data class OwnedSeat(val player: Player, val anchor: Interaction)
    private val ownedSeats = linkedMapOf<UUID, OwnedSeat>()
    private val anchors = mutableSetOf<UUID>()

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        info("Using native seating; press Shift to dismount and leave the table.")
    }

    fun join(player: Player, table: TableConfig, seat: Int, seatCount: Int) {
        val position = seatLocation(table, seat, seatCount)
        // Avatar's vehicle attachment is 0.60; the rendered hip is about 0.704 above its feet.
        // A 0.50 anchor puts the hip on the shared 0.60 stool and the eye at ground + 1.52.
        val feet = position.clone().add(0.0, ANCHOR_HEIGHT - VEHICLE_ATTACHMENT, 0.0)
        require(player.teleport(feet)) { "入座传送被取消, 请稍后再试" }
        val anchor = spawnAnchor(position.clone().add(0.0, ANCHOR_HEIGHT, 0.0))
        val owned = OwnedSeat(player, anchor)
        ownedSeats[player.uniqueId] = owned
        anchors += anchor.uniqueId
        if (!anchor.addPassenger(player)) {
            ownedSeats.remove(player.uniqueId)
            anchors.remove(anchor.uniqueId)
            anchor.remove()
            throw IllegalArgumentException("坐下被取消, 请稍后再试")
        }
    }

    private fun spawnAnchor(position: Location): Interaction = position.world.spawn(position, Interaction::class.java) {
        it.isPersistent = false
        it.isInvulnerable = true
        it.setGravity(false)
        it.interactionWidth = 0f
        it.interactionHeight = 0f
        it.isResponsive = false
    }

    fun repair() {
        ownedSeats.values.toList().filter { !it.anchor.isValid }.forEach(::repair)
    }

    private fun repair(owned: OwnedSeat) {
        anchors.remove(owned.anchor.uniqueId)
        val anchor = spawnAnchor(owned.anchor.location)
        ownedSeats[owned.player.uniqueId] = OwnedSeat(owned.player, anchor)
        anchors += anchor.uniqueId
        if (!anchor.addPassenger(owned.player)) {
            plugin.tables.leave(owned.player)
            plugin.tell(owned.player, "恢复坐姿被取消, 已离开牌桌, 请重新入座")
        }
    }

    fun leave(player: Player) {
        val owned = ownedSeats.remove(player.uniqueId) ?: return
        anchors.remove(owned.anchor.uniqueId)
        // Release ownership first: Bukkit's dismount callback must not queue another table leave.
        owned.anchor.remove()
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun dismount(event: EntityDismountEvent) {
        val player = event.entity as? Player ?: return
        val owned = ownedSeats[player.uniqueId] ?: return
        if (event.dismounted.uniqueId != owned.anchor.uniqueId) return
        // Wait for stopRiding/removal to finish so Shift and a deleted anchor can be distinguished.
        submitTask(delay = 1) {
            if (ownedSeats[player.uniqueId] === owned) {
                if (!owned.anchor.isValid) repair(owned) else plugin.tables.leave(player)
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun damage(event: EntityDamageEvent) {
        if (event.entity.uniqueId in anchors) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun teleport(event: EntityTeleportEvent) {
        if (event.entity.uniqueId in anchors) event.isCancelled = true
    }

    fun shutdown() {
        ownedSeats.values.toList().forEach { leave(it.player) }
        HandlerList.unregisterAll(this)
    }

    companion object {
        const val ANCHOR_HEIGHT = 0.50
        const val VEHICLE_ATTACHMENT = 0.60
        fun seatLocation(table: TableConfig, seat: Int, count: Int): Location = TableLayout.seatLocation(table, seat, count)
    }
}
