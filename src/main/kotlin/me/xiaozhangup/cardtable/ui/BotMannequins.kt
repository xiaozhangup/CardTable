package me.xiaozhangup.cardtable.ui

import io.papermc.paper.datacomponent.item.ResolvableProfile
import me.xiaozhangup.cardtable.CardTablePlugin
import me.xiaozhangup.cardtable.api.Participant
import me.xiaozhangup.cardtable.table.TableConfig
import me.xiaozhangup.cardtable.util.ext.warning
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.entity.Entity
import org.bukkit.entity.Interaction
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Pose
import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityDismountEvent
import org.bukkit.event.entity.EntityTeleportEvent
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

/** Native avatars are presentation only; game participants never become Bukkit players. */
class BotMannequins(private val plugin: CardTablePlugin) : Listener {
    private data class Avatar(val participant: Participant, val mannequin: Mannequin, val seat: Interaction, var skinApplied: Boolean)

    private val tables = mutableMapOf<String, MutableMap<UUID, Avatar>>()
    private val protectedEntities = mutableSetOf<UUID>()
    private val tableKey = NamespacedKey(plugin, "table")
    private val actionKey = NamespacedKey(plugin, "action")

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
        val skin = plugin.botSkins.profile(participant)
        val profile = skin ?: ResolvableProfile.resolvableProfile()
            .uuid(participant.id)
            .name("CardBot_${participant.seat + 1}")
            .build()
        val mannequin = location.world.spawn(location.clone().add(0.0, SeatingHook.ANCHOR_HEIGHT - SeatingHook.VEHICLE_ATTACHMENT, 0.0), Mannequin::class.java) {
            prepare(it)
            it.profile = profile
            it.isImmovable = true
            it.isCollidable = false
            it.isSilent = true
            it.setPose(Pose.STANDING, true)
            it.bodyYaw = location.yaw
            it.customName(Component.text(participant.name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
            it.isCustomNameVisible = true
            it.description = null
            // Reuse the table's entity-click entry point when every seat is occupied.
            it.persistentDataContainer.set(tableKey, PersistentDataType.STRING, table.id)
            it.persistentDataContainer.set(actionKey, PersistentDataType.STRING, "join")
        }
        // Mannequin rejects Pose.SITTING. The native humanoid model bends its legs when mounted.
        if (!anchor.addPassenger(mannequin)) {
            warning("Could not seat bot '${participant.name}' at table '${table.id}'; the mannequin will play while standing.")
        }
        return Avatar(participant, mannequin, anchor, skin != null)
    }

    fun refreshSkins() {
        tables.values.forEach { avatars -> avatars.values.filterNot { it.skinApplied }.forEach { avatar ->
            // A bot may be added before the asynchronous skin cache/refresh has completed.
            plugin.botSkins.profile(avatar.participant)?.let { profile ->
                avatar.mannequin.profile = profile
                avatar.skinApplied = true
            }
        } }
    }

    fun swing(tableId: String, seat: Int) {
        tables[tableId]?.values?.firstOrNull { it.participant.seat == seat }?.mannequin?.swingMainHand()
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
    internal fun entity(tableId: String, id: UUID): Mannequin? = tables[tableId]?.get(id)?.mannequin
    fun shutdown() { tables.keys.toList().forEach(::remove) }
}
