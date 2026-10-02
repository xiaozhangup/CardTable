package me.xiaozhangup.cardtable.ui

import io.papermc.paper.datacomponent.item.ResolvableProfile
import me.xiaozhangup.cardtable.api.Participant
import me.xiaozhangup.cardtable.table.TableConfig
import net.kyori.adventure.key.Key
import org.bukkit.entity.Entity
import org.bukkit.entity.Interaction
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Pose
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityDismountEvent
import org.bukkit.event.entity.EntityTeleportEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.profile.PlayerTextures
import java.util.UUID

/** Native avatars are presentation only; game participants never become Bukkit players. */
class BotMannequins(private val plugin: JavaPlugin) : Listener {
    private data class Avatar(val participant: Participant, val mannequin: Mannequin, val seat: Interaction)

    private val tables = mutableMapOf<String, MutableMap<UUID, Avatar>>()
    private val protectedEntities = mutableSetOf<UUID>()
    private val skins = listOf("steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri")

    /** The caller passes only bot participants. Existing avatars survive hand/turn refreshes. */
    fun refresh(table: TableConfig, seatCount: Int, participants: List<Participant>) {
        val current = tables.getOrPut(table.id) { linkedMapOf() }
        val wanted = participants.associateBy { it.id }
        current.keys.filter { id -> wanted[id] != current.getValue(id).participant }.forEach { id ->
            removeAvatar(current.remove(id)!!)
        }
        participants.filter { it.id !in current }.forEach { participant ->
            current[participant.id] = spawn(table, seatCount, participant)
        }
        if (current.isEmpty()) tables.remove(table.id)
    }

    private fun spawn(table: TableConfig, seatCount: Int, participant: Participant): Avatar {
        val location = SeatingHook.seatLocation(table, participant.seat, seatCount)
        val anchor = location.world.spawn(location.clone().add(0.0, SeatingHook.ANCHOR_HEIGHT, 0.0), Interaction::class.java) {
            prepare(it)
            it.interactionWidth = 0f
            it.interactionHeight = 0f
            it.isResponsive = false
        }
        val profile = ResolvableProfile.resolvableProfile()
            .uuid(participant.id)
            .name("CardBot_${participant.seat + 1}")
            .skinPatch {
                it.body(Key.key("minecraft:entity/player/wide/${skins[participant.seat % skins.size]}"))
                it.model(PlayerTextures.SkinModel.CLASSIC)
            }
            .build()
        val mannequin = location.world.spawn(location.clone().add(0.0, SeatingHook.ANCHOR_HEIGHT - SeatingHook.VEHICLE_ATTACHMENT, 0.0), Mannequin::class.java) {
            prepare(it)
            it.profile = profile
            it.isImmovable = true
            it.isCollidable = false
            it.isSilent = true
            it.setPose(Pose.STANDING, true)
            it.bodyYaw = location.yaw
            it.isCustomNameVisible = false
            it.description = null
        }
        // Mannequin rejects Pose.SITTING. The native humanoid model bends its legs when mounted.
        if (!anchor.addPassenger(mannequin)) {
            plugin.logger.warning("牌桌 ${table.id} 的 ${participant.name} 坐姿被取消，人偶将站立游玩。")
        }
        return Avatar(participant, mannequin, anchor)
    }

    private fun prepare(entity: Entity) {
        entity.isPersistent = false
        entity.isInvulnerable = true
        entity.setGravity(false)
        protectedEntities += entity.uniqueId
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun damage(event: EntityDamageEvent) {
        if (event.entity.uniqueId in protectedEntities) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun teleport(event: EntityTeleportEvent) {
        if (event.entity.uniqueId in protectedEntities) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun dismount(event: EntityDismountEvent) {
        if (event.entity.uniqueId in protectedEntities) event.isCancelled = true
    }

    @EventHandler
    fun death(event: EntityDeathEvent) {
        if (event.entity.uniqueId in protectedEntities) {
            event.drops.clear()
            event.droppedExp = 0
        }
    }

    private fun removeAvatar(avatar: Avatar) {
        protectedEntities.remove(avatar.mannequin.uniqueId)
        protectedEntities.remove(avatar.seat.uniqueId)
        avatar.mannequin.remove()
        avatar.seat.remove()
    }

    fun remove(tableId: String) { tables.remove(tableId)?.values?.forEach(::removeAvatar) }
    fun shutdown() { tables.keys.toList().forEach(::remove) }
}
