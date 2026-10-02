package me.xiaozhangup.cardtable.ui

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player

/** Reuses ordinary UI entities; keys must uniquely identify their owner and entity type. */
class DisplayFrame(private val initialize: (Entity, Player?) -> Unit) {
    private val entities = linkedMapOf<String, Entity>()
    private val used = mutableSetOf<String>()

    fun begin() {
        used.clear()
    }

    fun <T : Entity> use(key: String, point: Location, type: Class<T>, owner: Player? = null, update: (T) -> Unit): T {
        used += key
        val existing = entities[key]
        if (existing == null) {
            val entity = point.world.spawn(normalized(point), type) {
                initialize(it, owner)
                update(it)
            }
            entities[key] = entity
            return entity
        }
        val entity = type.cast(existing)
        val current = entity.location
        if (current.x != point.x || current.y != point.y || current.z != point.z) {
            entity.teleport(normalized(point))
        }
        update(entity)
        return entity
    }

    fun end() {
        val iterator = entities.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in used) {
                entry.value.remove()
                iterator.remove()
            }
        }
    }

    fun clear() {
        entities.values.forEach(Entity::remove)
        entities.clear()
        used.clear()
    }

    private fun normalized(point: Location): Location = point.clone().apply {
        yaw = 0f
        pitch = 0f
    }
}
