package me.xiaozhangup.cardtable.ui

import io.papermc.paper.event.player.PlayerTrackEntityEvent
import io.papermc.paper.event.player.PlayerUntrackEntityEvent
import me.xiaozhangup.cardtable.util.ext.submitTask
import net.kyori.adventure.text.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundBundlePacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.world.phys.Vec3
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.craftbukkit.entity.CraftTextDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.hypot

/** Independent client-only status displays use table coordinates and follow avatar tracking. */
internal class ParticipantStatusDisplays : Listener {
    data class Status(val id: UUID, val avatar: Entity, val point: Location, val text: Component, val lines: Int)

    private data class ViewerState(val text: Component, val transformation: Transformation)

    private class Binding(val tableId: String, var status: Status, val display: CraftTextDisplay) {
        val viewers = mutableMapOf<UUID, ViewerState>()
    }

    private val bindings = linkedMapOf<UUID, Binding>()

    fun refresh(tableId: String, statuses: List<Status>) {
        val wanted = statuses.associateBy { it.id }
        bindings.values.filter { binding ->
            binding.tableId == tableId && wanted[binding.status.id]?.let {
                it.avatar.uniqueId == binding.status.avatar.uniqueId
            } != true
        }.toList().forEach(::remove)
        for (status in statuses) {
            val binding = bindings.getOrPut(status.id) { create(tableId, status) }
            binding.status = status
            refreshViewers(binding)
        }
    }

    fun tick() {
        bindings.values.forEach { binding ->
            binding.viewers.keys.mapNotNull(Bukkit::getPlayer).forEach { refreshMetadata(binding, it) }
        }
    }

    private fun create(tableId: String, status: Status): Binding {
        // createEntity allocates the native metadata/ID without adding an entity to the world.
        val display = status.avatar.world.createEntity(status.point, TextDisplay::class.java) as CraftTextDisplay
        display.setRotation(0f, 0f)
        display.text(status.text)
        display.billboard = Display.Billboard.FIXED
        display.isDefaultBackground = true
        display.isShadowed = false
        display.alignment = TextDisplay.TextAlignment.CENTER
        display.lineWidth = 280
        display.setGravity(false)
        return Binding(tableId, status, display)
    }

    private fun refreshViewers(binding: Binding) {
        val status = binding.status
        val viewers = status.avatar.trackedBy
            .filter { it.uniqueId != status.id && it.isOnline && it.canSee(status.avatar) }.associateBy { it.uniqueId }
        (binding.viewers.keys - viewers.keys).forEach { id ->
            Bukkit.getPlayer(id)?.let { hide(binding, it) }
            binding.viewers.remove(id)
        }
        viewers.forEach { (id, viewer) ->
            if (id !in binding.viewers) {
                val state = viewerState(binding, viewer)
                binding.viewers[id] = state
                val handle = binding.display.handle
                send(viewer, ClientboundAddEntityPacket(handle.id, handle.uuid, handle.x, handle.y, handle.z,
                    handle.xRot, handle.yRot, handle.type, 0, Vec3.ZERO, handle.yHeadRot.toDouble()),
                    metadata(binding, state))
            } else {
                refreshMetadata(binding, viewer)
            }
        }
    }

    private fun viewerState(binding: Binding, viewer: Player): ViewerState {
        val status = binding.status
        val point = status.point
        val eye = viewer.eyeLocation
        val dx = eye.x - point.x
        val dy = eye.y - point.y
        val dz = eye.z - point.z
        val rotation = Quaternionf().rotationYXZ(
            atan2(dx, dz).toFloat(), -atan2(dy, hypot(dx, dz)).toFloat(), 0f)
        val origin = binding.display.location
        val scale = 0.5f
        // FIXED keeps the world offset stable; each viewer receives a rotation around the text center.
        val translation = rotation.transform(Vector3f(-0.025f * scale, -0.125f * status.lines * scale, 0f))
            .add((point.x - origin.x).toFloat(), (point.y - origin.y).toFloat(), (point.z - origin.z).toFloat())
        return ViewerState(status.text, Transformation(translation, rotation, Vector3f(scale), Quaternionf()))
    }

    private fun refreshMetadata(binding: Binding, viewer: Player) {
        val state = viewerState(binding, viewer)
        if (binding.viewers[viewer.uniqueId] == state) return
        binding.viewers[viewer.uniqueId] = state
        send(viewer, metadata(binding, state))
    }

    private fun metadata(binding: Binding, state: ViewerState): ClientboundSetEntityDataPacket {
        binding.display.text(state.text)
        binding.display.transformation = state.transformation
        return ClientboundSetEntityDataPacket(binding.display.entityId, binding.display.handle.entityData.packAll())
    }

    private fun send(viewer: Player, vararg packets: Packet<in ClientGamePacketListener>) {
        (viewer as CraftPlayer).handle.connection.send(ClientboundBundlePacket(packets.toList()))
    }

    private fun hide(binding: Binding, viewer: Player) {
        send(viewer, ClientboundRemoveEntitiesPacket(binding.display.entityId))
        binding.viewers.remove(viewer.uniqueId)
    }

    private fun remove(binding: Binding) {
        bindings.remove(binding.status.id)
        binding.viewers.keys.toList().mapNotNull(Bukkit::getPlayer).forEach { hide(binding, it) }
    }

    fun remove(tableId: String) = bindings.values.filter { it.tableId == tableId }.toList().forEach(::remove)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun track(event: PlayerTrackEntityEvent) {
        // Wait until native pairing has added the player to the avatar's trackedBy set.
        matching(event.entity).forEach(::refreshLater)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun untrack(event: PlayerUntrackEntityEvent) {
        matching(event.entity).forEach { binding ->
            if (event.player.uniqueId in binding.viewers) hide(binding, event.player)
        }
    }

    private fun refreshLater(binding: Binding) {
        submitTask(delay = 1) {
            // A queued tracking callback must not recreate a label after its participant was removed.
            if (bindings[binding.status.id] === binding) refreshViewers(binding)
        }
    }

    private fun matching(entity: Entity) = bindings.values.filter {
        it.status.avatar.uniqueId == entity.uniqueId
    }

    @EventHandler
    fun quit(event: PlayerQuitEvent) {
        bindings.values.forEach {
            it.viewers.remove(event.player.uniqueId)
        }
    }
}
